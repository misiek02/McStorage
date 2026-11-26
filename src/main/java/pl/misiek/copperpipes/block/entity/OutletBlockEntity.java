package pl.misiek.copperpipes.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.registry.ModBlockEntities;

/** Outlet state. Gets the sorting card slot in milestone 7; until then every outlet accepts everything (overflow). */
public class OutletBlockEntity extends BlockEntity {
	public OutletBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.OUTLET, pos, state);
	}
}
