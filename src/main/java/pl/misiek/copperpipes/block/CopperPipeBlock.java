package pl.misiek.copperpipes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A pipe segment. Plain block: no block entity and no ticking; connections are added in milestone 3. */
public class CopperPipeBlock extends Block {
	private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);

	public CopperPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return CORE;
	}
}
