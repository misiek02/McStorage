package pl.misiek.copperpipes.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.registry.ModBlockEntities;

/** Inlet state. Gets the sorting card slot in milestone 7; until then every inlet accepts everything (overflow). */
public class InletBlockEntity extends BlockEntity {
	public InletBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INLET, pos, state);
	}
}
