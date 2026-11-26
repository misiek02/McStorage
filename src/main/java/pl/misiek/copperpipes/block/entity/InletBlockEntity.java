package pl.misiek.copperpipes.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.CopperPipeBlock;
import pl.misiek.copperpipes.block.OutletConnectorBlock;
import pl.misiek.copperpipes.block.PipeConnections;
import pl.misiek.copperpipes.registry.ModBlockEntities;

/**
 * Milestone 5 version: every {@link #INTERVAL} ticks, walks the pipes to find outlets and moves up to
 * {@link #MAX_ITEMS} items straight into them, nearest outlet first. Milestone 6 replaces the walk with the
 * cached network, items in transit and sleeping while idle.
 */
public class InletBlockEntity extends BlockEntity {
	static final int INTERVAL = 8;
	static final int MAX_ITEMS = 16;
	private static final int MAX_NODES = 4096;

	private int cooldown;

	public InletBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INLET, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, InletBlockEntity inlet) {
		if (--inlet.cooldown > 0) {
			return;
		}
		inlet.cooldown = INTERVAL;

		BlockPos sourcePos = ConnectorBlock.targetPos(pos, state);
		Storage<ItemVariant> source = ItemStorage.SIDED.find(level, sourcePos, ConnectorBlock.targetSide(state));
		if (source == null || !source.supportsExtraction()) {
			return;
		}

		long remaining = MAX_ITEMS;
		for (BlockPos outletPos : findOutlets(level, pos, state)) {
			BlockState outletState = level.getBlockState(outletPos);
			BlockPos targetPos = ConnectorBlock.targetPos(outletPos, outletState);
			if (targetPos.equals(sourcePos)) {
				continue; // would only loop items back into the same container
			}
			Storage<ItemVariant> target = ItemStorage.SIDED.find(level, targetPos, ConnectorBlock.targetSide(outletState));
			if (target == null) {
				continue;
			}
			try (Transaction transaction = Transaction.openOuter()) {
				remaining -= StorageUtil.move(source, target, variant -> true, remaining, transaction);
				transaction.commit();
			}
			if (remaining <= 0) {
				break;
			}
		}
	}

	/** Breadth-first walk over connected pipes; returns outlets ordered by distance. */
	private static List<BlockPos> findOutlets(Level level, BlockPos start, BlockState startState) {
		List<BlockPos> outlets = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(start);
		queue.add(start);

		while (!queue.isEmpty() && visited.size() < MAX_NODES) {
			BlockPos pos = queue.poll();
			BlockState state = pos.equals(start) ? startState : level.getBlockState(pos);
			for (Map.Entry<Direction, BooleanProperty> entry : PipeConnections.PROPERTY_BY_DIRECTION.entrySet()) {
				if (!state.getValue(entry.getValue())) {
					continue;
				}
				BlockPos next = pos.relative(entry.getKey());
				if (!visited.add(next)) {
					continue;
				}
				BlockState nextState = level.getBlockState(next);
				if (nextState.getBlock() instanceof CopperPipeBlock) {
					queue.add(next);
				} else if (nextState.getBlock() instanceof OutletConnectorBlock) {
					outlets.add(next);
				}
			}
		}
		return outlets;
	}
}
