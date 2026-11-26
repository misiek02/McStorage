package pl.misiek.copperpipes.network;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.InletConnectorBlock;
import pl.misiek.copperpipes.block.OutletConnectorBlock;

/**
 * All pipe networks of one level.
 *
 * <ul>
 * <li>Networks are discarded when one of their blocks changes and rebuilt lazily on next use.</li>
 * <li>Only awake outlets are processed; an outlet sleeps when it moved nothing and is woken when its container
 *     or a container behind one of its network's inlets changes, or when its network changes.</li>
 * <li>Items in transit wait in a queue ordered by arrival tick; each tick only the head is checked.</li>
 * </ul>
 */
public final class PipeNetworkManager {
	/** Outlet interval in ticks at speed 1.0 (scaled by the oxidation of the outlet's first pipe). */
	static final int BASE_INTERVAL = 8;
	/** Items moved per outlet operation (one stack, one transit). */
	static final int MAX_ITEMS = 16;
	private static final int UNLOADED_RETRY_TICKS = 20;

	private final ServerLevel level;
	private final Long2ObjectOpenHashMap<Network> networkByNode = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<Set<Network>> networksByChunk = new Long2ObjectOpenHashMap<>();
	/** Container position -> connectors attached to it. */
	private final Long2ObjectOpenHashMap<LongArrayList> connectorsByContainer = new Long2ObjectOpenHashMap<>();
	/** Connector position -> container position it is registered under. */
	private final Long2LongOpenHashMap containerByConnector = new Long2LongOpenHashMap();
	/** Awake outlets -> game tick of their next operation. */
	private final Long2LongOpenHashMap activeOutlets = new Long2LongOpenHashMap();
	private final PriorityQueue<Transit> transit = new PriorityQueue<>(Comparator.comparingLong(Transit::arrivalTick));

	PipeNetworkManager(ServerLevel level) {
		this.level = level;
	}

	// ---- ticking ----

	void tick() {
		long now = this.level.getGameTime();
		while (!this.transit.isEmpty() && this.transit.peek().arrivalTick() <= now) {
			deliver(this.transit.poll(), now);
		}

		if (this.activeOutlets.isEmpty()) {
			return;
		}
		LongArrayList due = new LongArrayList();
		for (Long2LongMap.Entry entry : this.activeOutlets.long2LongEntrySet()) {
			if (entry.getLongValue() <= now) {
				due.add(entry.getLongKey());
			}
		}
		for (long outlet : due) {
			int interval = processOutlet(outlet, now);
			if (interval > 0) {
				this.activeOutlets.put(outlet, now + interval);
			} else {
				this.activeOutlets.remove(outlet); // sleep until woken
			}
		}
	}

	/** Tries to send one stack from the outlet's container; returns the delay until the next try, or 0 to sleep. */
	private int processOutlet(long outlet, long now) {
		BlockPos outletPos = BlockPos.of(outlet);
		if (!this.level.isLoaded(outletPos)) {
			return 0;
		}
		BlockState outletState = this.level.getBlockState(outletPos);
		if (!(outletState.getBlock() instanceof OutletConnectorBlock)) {
			return 0;
		}
		BlockPos sourcePos = ConnectorBlock.targetPos(outletPos, outletState);
		Storage<ItemVariant> source = ItemStorage.SIDED.find(this.level, sourcePos, ConnectorBlock.targetSide(outletState));
		if (source == null || !source.supportsExtraction()) {
			return 0;
		}

		Network network = networkAt(outletPos);
		// Take the first item kind in the container that some allowed inlet has room for.
		for (StorageView<ItemVariant> view : source.nonEmptyViews()) {
			ItemVariant variant = view.getResource();
			for (Route route : network.routesFor(this.level, outlet, variant.getItem())) {
				BlockPos inletPos = BlockPos.of(route.inlet());
				BlockState inletState = this.level.getBlockState(inletPos);
				BlockPos targetPos = ConnectorBlock.targetPos(inletPos, inletState);
				if (targetPos.equals(sourcePos) || !this.level.isLoaded(targetPos)) {
					continue;
				}
				Storage<ItemVariant> target = ItemStorage.SIDED.find(this.level, targetPos, ConnectorBlock.targetSide(inletState));
				if (target == null) {
					continue;
				}
				ItemStack sent = extract(source, target, variant);
				if (!sent.isEmpty()) {
					this.transit.add(new Transit(sent, route.inlet(), route.path(), now + route.travelTicks()));
					return interval(route);
				}
			}
		}
		return 0;
	}

	/** Extracts up to {@link #MAX_ITEMS} of {@code variant}, limited to what the target currently has room for. */
	private static ItemStack extract(Storage<ItemVariant> source, Storage<ItemVariant> target, ItemVariant variant) {
		long accepted;
		try (Transaction simulation = Transaction.openOuter()) {
			accepted = target.insert(variant, Math.min(MAX_ITEMS, variant.getItem().getDefaultMaxStackSize()), simulation);
			// not committed: only a capacity check
		}
		if (accepted <= 0) {
			return ItemStack.EMPTY;
		}
		try (Transaction transaction = Transaction.openOuter()) {
			long extracted = source.extract(variant, accepted, transaction);
			if (extracted <= 0) {
				return ItemStack.EMPTY;
			}
			transaction.commit();
			return variant.toStack((int) extracted);
		}
	}

	/** Outlet interval: base interval scaled by the speed of the first block the item entered. */
	private int interval(Route route) {
		BlockState first = this.level.getBlockState(BlockPos.of(route.path()[0]));
		return (int) Math.ceil(BASE_INTERVAL / Network.speedOf(first));
	}

	private void deliver(Transit item, long now) {
		BlockPos inletPos = BlockPos.of(item.inlet());
		BlockState inletState = this.level.getBlockState(inletPos);
		BlockPos targetPos = ConnectorBlock.targetPos(inletPos, inletState);
		if (!this.level.isLoaded(inletPos) || !this.level.isLoaded(targetPos)) {
			this.transit.add(item.delayedTo(now + UNLOADED_RETRY_TICKS));
			return;
		}
		ItemStack left = insertInto(inletPos, inletState, item.stack());
		if (!left.isEmpty()) {
			// The inlet filled up while the item was travelling: try the other inlets allowed for it, right away.
			Network network = networkAt(inletPos);
			for (long other : network.inletsFor(left.getItem())) {
				if (left.isEmpty()) {
					break;
				}
				BlockPos otherPos = BlockPos.of(other);
				if (other != item.inlet() && this.level.isLoaded(otherPos)) {
					left = insertInto(otherPos, this.level.getBlockState(otherPos), left);
				}
			}
		}
		if (!left.isEmpty()) {
			drop(inletPos, left);
		}
	}

	/** Inserts into the container behind an inlet; returns what did not fit. */
	private ItemStack insertInto(BlockPos inletPos, BlockState inletState, ItemStack stack) {
		if (!(inletState.getBlock() instanceof InletConnectorBlock)) {
			return stack;
		}
		Storage<ItemVariant> target = ItemStorage.SIDED.find(this.level, ConnectorBlock.targetPos(inletPos, inletState),
				ConnectorBlock.targetSide(inletState));
		if (target == null) {
			return stack;
		}
		try (Transaction transaction = Transaction.openOuter()) {
			long inserted = target.insert(ItemVariant.of(stack), stack.getCount(), transaction);
			transaction.commit();
			return stack.copyWithCount(stack.getCount() - (int) inserted);
		}
	}

	private void drop(BlockPos pos, ItemStack stack) {
		Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
	}

	/** Server shutdown: deliver everything still travelling so no items are lost. */
	void flush() {
		while (!this.transit.isEmpty()) {
			Transit item = this.transit.poll();
			BlockPos inletPos = BlockPos.of(item.inlet());
			this.level.getChunkAt(inletPos); // force-load; acceptable once during shutdown
			ItemStack left = insertInto(inletPos, this.level.getBlockState(inletPos), item.stack());
			if (!left.isEmpty()) {
				drop(inletPos, left);
			}
		}
	}

	// ---- network bookkeeping ----

	private Network networkAt(BlockPos pos) {
		Network network = this.networkByNode.get(pos.asLong());
		if (network == null) {
			network = Network.build(this.level, pos);
			for (long node : network.nodes) {
				this.networkByNode.put(node, network);
			}
			for (long chunk : network.chunks) {
				this.networksByChunk.computeIfAbsent(chunk, c -> new HashSet<>()).add(network);
			}
		}
		return network;
	}

	private void discard(Network network) {
		for (long node : network.nodes) {
			this.networkByNode.remove(node);
		}
		for (long chunk : network.chunks) {
			Set<Network> set = this.networksByChunk.get(chunk);
			if (set != null) {
				set.remove(network);
				if (set.isEmpty()) {
					this.networksByChunk.remove(chunk);
				}
			}
		}
		// Routes may have changed: give every outlet of the old network another try.
		for (long outlet : network.outlets) {
			wakeOutlet(outlet);
		}
	}

	/** A pipe or connector was placed, removed or changed state at {@code pos}. */
	public void onNodeChanged(BlockPos pos) {
		discardAt(pos.asLong());
		for (Direction direction : Direction.values()) {
			discardAt(pos.relative(direction).asLong());
		}
	}

	private void discardAt(long pos) {
		Network network = this.networkByNode.get(pos);
		if (network != null) {
			discard(network);
		}
	}

	/** A pipe or connector was broken: items travelling through it fall out. */
	public void onNodeRemoved(BlockPos pos) {
		long removed = pos.asLong();
		List<Transit> dropped = new ArrayList<>();
		this.transit.removeIf(item -> {
			if (item.passesThrough(removed)) {
				dropped.add(item);
				return true;
			}
			return false;
		});
		for (Transit item : dropped) {
			drop(pos, item.stack());
		}
		onNodeChanged(pos);
	}

	/** Tags may have changed (datapack reload): recompile all filters on next use. */
	public void discardAll() {
		for (Network network : Set.copyOf(this.networkByNode.values())) {
			discard(network);
		}
	}

	public void onChunkChanged(long chunk) {
		Set<Network> networks = this.networksByChunk.get(chunk);
		if (networks != null) {
			for (Network network : List.copyOf(networks)) {
				discard(network);
			}
		}
	}

	// ---- connectors and waking ----

	public void registerConnector(BlockPos pos, BlockState state) {
		unregisterConnector(pos);
		long container = ConnectorBlock.targetPos(pos, state).asLong();
		this.connectorsByContainer.computeIfAbsent(container, c -> new LongArrayList()).add(pos.asLong());
		this.containerByConnector.put(pos.asLong(), container);
		if (state.getBlock() instanceof OutletConnectorBlock) {
			wakeOutlet(pos.asLong());
		}
	}

	public void unregisterConnector(BlockPos pos) {
		long connector = pos.asLong();
		if (!this.containerByConnector.containsKey(connector)) {
			return;
		}
		long container = this.containerByConnector.remove(connector);
		LongArrayList list = this.connectorsByContainer.get(container);
		if (list != null) {
			list.rem(connector);
			if (list.isEmpty()) {
				this.connectorsByContainer.remove(container);
			}
		}
		this.activeOutlets.remove(connector);
	}

	/** Called for every block entity change in the level, so it must stay a single map lookup when unwatched. */
	public void onContainerChanged(BlockPos containerPos) {
		LongArrayList connectors = this.connectorsByContainer.get(containerPos.asLong());
		if (connectors == null) {
			return;
		}
		for (long connector : connectors.toLongArray()) {
			BlockPos connectorPos = BlockPos.of(connector);
			BlockState state = this.level.getBlockState(connectorPos);
			if (state.getBlock() instanceof OutletConnectorBlock) {
				wakeOutlet(connector);
			} else if (state.getBlock() instanceof InletConnectorBlock) {
				// Room may have appeared behind this inlet: wake the outlets that feed it.
				for (long outlet : networkAt(connectorPos).outlets) {
					wakeOutlet(outlet);
				}
			}
		}
	}

	private void wakeOutlet(long outlet) {
		if (!this.activeOutlets.containsKey(outlet)) {
			this.activeOutlets.put(outlet, this.level.getGameTime() + 1);
		}
	}

	// ---- for tests / debugging ----

	public int itemsInTransitThrough(BlockPos pos) {
		long p = pos.asLong();
		return (int) this.transit.stream().filter(item -> item.passesThrough(p)).count();
	}

	public boolean isOutletAwake(BlockPos outlet) {
		return this.activeOutlets.containsKey(outlet.asLong());
	}
}
