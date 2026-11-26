package pl.misiek.copperpipes.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Connection rules and arm shapes shared by pipes and connectors. Each block has one {@link Side} property per
 * direction (named north, east, ... like vanilla's six-way blocks).
 */
public final class PipeConnections {
	/**
	 * State of one side. The serialized names keep "true"/"false", so blocks saved before arms could be disabled
	 * load unchanged.
	 */
	public enum Side implements StringRepresentable {
		NONE("false"),
		CONNECTED("true"),
		/** Turned off with the wrench (or by sneak-placing): never connects, even to a pipe next to it. */
		DISABLED("disabled");

		private final String name;

		Side(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static final Map<Direction, EnumProperty<Side>> PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);
	/** 8x8 arm reaching from the block edge to the 8x8 core, for each direction. */
	public static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(8, 0, 4));

	static {
		for (Direction direction : Direction.values()) {
			PROPERTY_BY_DIRECTION.put(direction, EnumProperty.create(direction.getSerializedName(), Side.class));
		}
	}

	private PipeConnections() {
	}

	public static EnumProperty<?>[] properties() {
		return PROPERTY_BY_DIRECTION.values().toArray(EnumProperty[]::new);
	}

	public static boolean isConnected(BlockState state, Direction direction) {
		EnumProperty<Side> property = PROPERTY_BY_DIRECTION.get(direction);
		return state.hasProperty(property) && state.getValue(property) == Side.CONNECTED;
	}

	public static Side side(BlockState state, Direction direction) {
		EnumProperty<Side> property = PROPERTY_BY_DIRECTION.get(direction);
		return state.hasProperty(property) ? state.getValue(property) : Side.NONE;
	}

	/** Whether a block in {@code self} state may link to {@code neighbour}, which lies in {@code direction}. */
	public static boolean canConnect(BlockState self, Direction direction, BlockState neighbour) {
		if (side(self, direction) == Side.DISABLED || side(neighbour, direction.getOpposite()) == Side.DISABLED) {
			return false;
		}
		// A connector never links on its container side, from either end.
		if (self.getBlock() instanceof ConnectorBlock && self.getValue(ConnectorBlock.FACING) == direction) {
			return false;
		}
		if (neighbour.getBlock() instanceof ConnectorBlock) {
			return neighbour.getValue(ConnectorBlock.FACING) != direction.getOpposite();
		}
		return neighbour.getBlock() instanceof CopperPipeBlock;
	}

	/** Recomputes one side from its neighbour; a disabled side stays disabled. */
	public static BlockState updateSide(BlockState state, Direction direction, BlockState neighbour) {
		if (side(state, direction) == Side.DISABLED) {
			return state;
		}
		return state.setValue(PROPERTY_BY_DIRECTION.get(direction), canConnect(state, direction, neighbour) ? Side.CONNECTED : Side.NONE);
	}

	public static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction direction : Direction.values()) {
			state = updateSide(state, direction, level.getBlockState(pos.relative(direction)));
		}
		return state;
	}

	public static BlockState withoutConnections(BlockState state) {
		for (EnumProperty<Side> property : PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(property, Side.NONE);
		}
		return state;
	}

	public static VoxelShape addArms(VoxelShape shape, BlockState state) {
		for (Direction direction : Direction.values()) {
			if (isConnected(state, direction)) {
				shape = Shapes.or(shape, ARMS.get(direction));
			}
		}
		return shape;
	}
}
