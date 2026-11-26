package pl.misiek.copperpipes.network;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.CopperPipeBlock;
import pl.misiek.copperpipes.block.InletConnectorBlock;
import pl.misiek.copperpipes.block.OutletConnectorBlock;
import pl.misiek.copperpipes.block.PipeConnections;
import pl.misiek.copperpipes.block.entity.InletBlockEntity;
import pl.misiek.copperpipes.card.CardFilter;

/**
 * One connected group of pipes and connectors. Built once by BFS and thrown away (not patched) when any of its
 * blocks change; routes are computed lazily per outlet and cached until then.
 */
final class Network {
	static final int MAX_NODES = 8192;
	/** Ticks to cross one block at speed 1.0. */
	static final double TICKS_PER_BLOCK = 4.0;

	final LongOpenHashSet nodes = new LongOpenHashSet();
	final LongArrayList outlets = new LongArrayList();
	final LongArrayList inlets = new LongArrayList();
	/** Chunks holding nodes, plus unloaded chunks the walk stopped at; a load/unload there invalidates us. */
	final LongOpenHashSet chunks = new LongOpenHashSet();
	private final Long2ObjectOpenHashMap<List<Route>> routesByOutlet = new Long2ObjectOpenHashMap<>();
	/** Compiled card per filtered inlet; inlets without a card are overflow and absent here. */
	private final Long2ObjectOpenHashMap<CardFilter> filters = new Long2ObjectOpenHashMap<>();
	/** Item -> inlets that may receive it: matching filtered inlets by priority (highest first), then overflow inlets. */
	private final Map<Item, long[]> inletsByItem = new HashMap<>();
	/** Outlet -> item -> routes to the inlets above, in that preference order (nearest first within a priority). */
	private final Long2ObjectOpenHashMap<Map<Item, List<Route>>> routesByOutletAndItem = new Long2ObjectOpenHashMap<>();

	static boolean isNode(BlockState state) {
		return state.getBlock() instanceof CopperPipeBlock || state.getBlock() instanceof ConnectorBlock;
	}

	static Network build(ServerLevel level, BlockPos start) {
		Network network = new Network();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		network.add(level, start.asLong(), level.getBlockState(start));
		queue.enqueue(start.asLong());

		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		while (!queue.isEmpty() && network.nodes.size() < MAX_NODES) {
			long pos = queue.dequeueLong();
			BlockState state = level.getBlockState(BlockPos.of(pos));
			for (Map.Entry<Direction, BooleanProperty> entry : PipeConnections.PROPERTY_BY_DIRECTION.entrySet()) {
				if (!state.hasProperty(entry.getValue()) || !state.getValue(entry.getValue())) {
					continue;
				}
				next.set(pos).move(entry.getKey());
				long nextPos = next.asLong();
				if (network.nodes.contains(nextPos)) {
					continue;
				}
				if (!level.isLoaded(next)) {
					network.chunks.add(ChunkPos.pack(next)); // rebuild once that chunk loads
					continue;
				}
				BlockState nextState = level.getBlockState(next);
				if (isNode(nextState)) {
					network.add(level, nextPos, nextState);
					queue.enqueue(nextPos);
				}
			}
		}
		return network;
	}

	private void add(ServerLevel level, long pos, BlockState state) {
		this.nodes.add(pos);
		this.chunks.add(ChunkPos.pack(BlockPos.of(pos)));
		if (state.getBlock() instanceof OutletConnectorBlock) {
			this.outlets.add(pos);
		} else if (state.getBlock() instanceof InletConnectorBlock) {
			this.inlets.add(pos);
			if (level.getBlockEntity(BlockPos.of(pos)) instanceof InletBlockEntity inlet) {
				CardFilter filter = inlet.compileFilter();
				if (filter != null) {
					this.filters.put(pos, filter);
				}
			}
		}
	}

	/** Inlets allowed to receive {@code item}, in preference order. Tags were expanded when the filters were compiled. */
	long[] inletsFor(Item item) {
		return this.inletsByItem.computeIfAbsent(item, this::computeInletsFor);
	}

	private long[] computeInletsFor(Item item) {
		LongArrayList matched = new LongArrayList();
		LongArrayList overflow = new LongArrayList();
		for (long inlet : this.inlets) {
			CardFilter filter = this.filters.get(inlet);
			if (filter == null) {
				overflow.add(inlet);
			} else if (filter.matches(item)) {
				matched.add(inlet);
			}
		}
		matched.sort((a, b) -> Integer.compare(this.filters.get(b).priority(), this.filters.get(a).priority()));
		matched.addAll(overflow);
		return matched.toLongArray();
	}

	/** Priority of an inlet for ordering; overflow inlets come after every filtered one. */
	private int priorityOf(long inlet) {
		CardFilter filter = this.filters.get(inlet);
		return filter == null ? -1 : filter.priority();
	}

	/** Routes from an outlet for one item: allowed inlets only, highest priority first, nearest first within a priority. */
	List<Route> routesFor(ServerLevel level, long outlet, Item item) {
		return this.routesByOutletAndItem.computeIfAbsent(outlet, o -> new HashMap<>()).computeIfAbsent(item, i -> {
			LongOpenHashSet allowed = new LongOpenHashSet(inletsFor(i));
			List<Route> routes = new ArrayList<>();
			for (Route route : routesFrom(level, outlet)) {
				if (allowed.contains(route.inlet())) {
					routes.add(route);
				}
			}
			// Stable sort: routes are already nearest-first, so that order survives within one priority.
			routes.sort(Comparator.comparingInt((Route r) -> priorityOf(r.inlet())).reversed());
			return routes;
		});
	}

	/** Routes from an outlet to every reachable inlet, fastest first. */
	List<Route> routesFrom(ServerLevel level, long outlet) {
		return this.routesByOutlet.computeIfAbsent(outlet, o -> computeRoutes(level, o));
	}

	/** Dijkstra over travel time, so faster (less oxidized) pipes win over shorter slow ones. */
	private List<Route> computeRoutes(ServerLevel level, long outlet) {
		Long2DoubleOpenHashMap cost = new Long2DoubleOpenHashMap();
		cost.defaultReturnValue(Double.MAX_VALUE);
		Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
		PriorityQueue<Step> open = new PriorityQueue<>(Comparator.comparingDouble(Step::cost));
		cost.put(outlet, 0);
		open.add(new Step(0, outlet));

		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		while (!open.isEmpty()) {
			Step step = open.poll();
			long pos = step.pos();
			if (step.cost() > cost.get(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(BlockPos.of(pos));
			// Items stop at inlets; they don't flow through them to other pipes.
			if (pos != outlet && state.getBlock() instanceof InletConnectorBlock) {
				continue;
			}
			for (Map.Entry<Direction, BooleanProperty> e : PipeConnections.PROPERTY_BY_DIRECTION.entrySet()) {
				if (!state.hasProperty(e.getValue()) || !state.getValue(e.getValue())) {
					continue;
				}
				next.set(pos).move(e.getKey());
				long nextPos = next.asLong();
				if (!this.nodes.contains(nextPos)) {
					continue;
				}
				double nextCost = step.cost() + TICKS_PER_BLOCK / speedOf(level.getBlockState(next));
				if (nextCost < cost.get(nextPos)) {
					cost.put(nextPos, nextCost);
					parent.put(nextPos, pos);
					open.add(new Step(nextCost, nextPos));
				}
			}
		}

		List<Route> routes = new ArrayList<>();
		for (long inlet : this.inlets) {
			if (cost.get(inlet) == Double.MAX_VALUE) {
				continue;
			}
			LongArrayList path = new LongArrayList();
			for (long p = inlet; p != outlet; p = parent.get(p)) {
				path.add(p);
			}
			long[] ordered = new long[path.size()];
			for (int i = 0; i < ordered.length; i++) {
				ordered[i] = path.getLong(ordered.length - 1 - i);
			}
			routes.add(new Route(inlet, ordered, (int) Math.ceil(cost.get(inlet))));
		}
		routes.sort(Comparator.comparingInt(Route::travelTicks));
		return routes;
	}

	private record Step(double cost, long pos) {
	}

	static double speedOf(BlockState state) {
		return state.getBlock() instanceof CopperPipeBlock pipe ? pipe.speedMultiplier() : 1.0;
	}
}
