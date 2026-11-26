package pl.misiek.copperpipes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.misiek.copperpipes.network.PipeNetworks;

/** Wakes sleeping outlets when a container next to a connector changes, instead of polling it every tick. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
	@Inject(method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
	private static void copperpipes$onSetChanged(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
		if (level instanceof ServerLevel serverLevel) {
			PipeNetworks.onBlockEntityChanged(serverLevel, pos);
		}
	}
}
