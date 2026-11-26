package pl.misiek.copperpipes.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Connection rules and arm shapes shared by pipes and connectors (both store six boolean connection properties). */
public final class PipeConnections {
	public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
	/** 8x8 arm reaching from the block edge to the 8x8 core, for each direction. */
	public static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(8, 0, 4));

	private PipeConnections() {
	}

	/** Whether a block in {@code self} state links to {@code neighbour}, which lies in {@code direction}. */
	public static boolean canConnect(BlockState self, Direction direction, BlockState neighbour) {
		// A connector never links on its container side, from either end.
		if (self.getBlock() instanceof ConnectorBlock && self.getValue(ConnectorBlock.FACING) == direction) {
			return false;
		}
		if (neighbour.getBlock() instanceof ConnectorBlock) {
			return neighbour.getValue(ConnectorBlock.FACING) != direction.getOpposite();
		}
		return neighbour.getBlock() instanceof CopperPipeBlock;
	}

	public static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction direction : Direction.values()) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), canConnect(state, direction, neighbour));
		}
		return state;
	}

	public static BlockState withoutConnections(BlockState state) {
		for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(property, false);
		}
		return state;
	}

	public static VoxelShape addArms(VoxelShape shape, BlockState state) {
		for (Map.Entry<Direction, BooleanProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
			if (state.getValue(entry.getValue())) {
				shape = Shapes.or(shape, ARMS.get(entry.getKey()));
			}
		}
		return shape;
	}
}
