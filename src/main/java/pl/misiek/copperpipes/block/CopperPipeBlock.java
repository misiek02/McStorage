package pl.misiek.copperpipes.block;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pipe segment. Plain block: no block entity and no ticking (waxed variants use this class directly).
 * Connections are stored in six boolean properties and updated only when a neighbour changes.
 */
public class CopperPipeBlock extends Block {
	public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

	private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
	private static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(8, 0, 4));

	private final WeatheringCopper.WeatherState weatherState;
	private final Function<BlockState, VoxelShape> shapes;

	public CopperPipeBlock(WeatheringCopper.WeatherState weatherState, Properties properties) {
		super(properties);
		this.weatherState = weatherState;
		BlockState state = this.stateDefinition.any();
		for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(property, false);
		}
		this.registerDefaultState(state);
		this.shapes = this.getShapeForEachState(CopperPipeBlock::computeShape);
	}

	public WeatheringCopper.WeatherState getAge() {
		return this.weatherState;
	}

	/** Item speed factor for this oxidation stage. */
	public float speedMultiplier() {
		return switch (this.weatherState) {
			case UNAFFECTED -> 1.0F;
			case EXPOSED -> 0.7F;
			case WEATHERED -> 0.45F;
			case OXIDIZED -> 0.25F;
		};
	}

	private static VoxelShape computeShape(BlockState state) {
		VoxelShape shape = CORE;
		for (Map.Entry<Direction, BooleanProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
			if (state.getValue(entry.getValue())) {
				shape = Shapes.or(shape, ARMS.get(entry.getKey()));
			}
		}
		return shape;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.apply(state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockGetter level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = this.defaultBlockState();
		for (Direction direction : Direction.values()) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), canConnectTo(neighbour));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour), canConnectTo(neighbourState));
	}

	/** Pipes connect to other pipes; connectors are added here in milestone 5. */
	public static boolean canConnectTo(BlockState neighbour) {
		return neighbour.getBlock() instanceof CopperPipeBlock;
	}
}
