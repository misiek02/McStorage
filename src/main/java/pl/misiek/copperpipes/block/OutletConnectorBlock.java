package pl.misiek.copperpipes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.block.entity.OutletBlockEntity;
import pl.misiek.copperpipes.registry.ModBlockEntities;

/** Pulls items out of the attached container into the pipe network. */
public class OutletConnectorBlock extends ConnectorBlock {
	public OutletConnectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new OutletBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModBlockEntities.OUTLET) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<OutletBlockEntity>) OutletBlockEntity::serverTick;
	}
}
