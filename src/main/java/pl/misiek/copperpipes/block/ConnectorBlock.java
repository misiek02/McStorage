package pl.misiek.copperpipes.block;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.misiek.copperpipes.network.PipeNetworks;

/**
 * Base for outlet/inlet connectors. FACING points at the attached container; the other five sides link to pipes
 * the same way a pipe does.
 */
public abstract class ConnectorBlock extends Block implements EntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** Flange against the container plus the body up to the centre, for a connector facing north. */
	private static final Map<Direction, VoxelShape> BASE = Shapes.rotateAll(
			Shapes.or(Block.box(2, 2, 0, 14, 14, 2), Block.box(4, 4, 2, 12, 12, 12)));

	private final Function<BlockState, VoxelShape> shapes;

	protected ConnectorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(PipeConnections.withoutConnections(this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
		this.shapes = this.getShapeForEachState(state -> PipeConnections.addArms(BASE.get(state.getValue(FACING)), state));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		builder.add(PipeConnections.properties());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.apply(state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Face the block that was clicked: the container this connector is attached to.
		BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
		return PipeConnections.withConnections(context.getLevel(), context.getClickedPos(), state);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			PipeNetworks.get(serverLevel).onNodeChanged(pos);
			PipeNetworks.get(serverLevel).registerConnector(pos, state);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		PipeNetworks.nodeRemovedOrReplaced(level, pos);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return PipeConnections.updateSide(state, directionToNeighbour, neighbourState);
	}

	/** Position of the attached container. */
	public static BlockPos targetPos(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING));
	}

	/** Side of the attached container that the connector touches. */
	public static Direction targetSide(BlockState state) {
		return state.getValue(FACING).getOpposite();
	}
}
