package pl.misiek.copperpipes.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.registry.ModBlockEntities;

/**
 * Marks an outlet connector. It does not tick: the level's {@link pl.misiek.copperpipes.network.PipeNetworkManager}
 * runs outlets only while they have work.
 */
public class OutletBlockEntity extends BlockEntity {
	public OutletBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.OUTLET, pos, state);
	}
}
