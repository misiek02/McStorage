package pl.misiek.copperpipes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.block.entity.OutletBlockEntity;

/** Pulls items out of the attached container into the pipe network (driven by the network manager). */
public class OutletConnectorBlock extends ConnectorBlock {
	public OutletConnectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new OutletBlockEntity(pos, state);
	}
}
