package pl.misiek.copperpipes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import pl.misiek.copperpipes.block.entity.InletBlockEntity;
import pl.misiek.copperpipes.registry.ModItems;

/**
 * Inserts items from the network into the attached container. Right-click with a sorting card to insert it
 * (swapping any card already there); right-click with an empty hand to take the card out.
 */
public class InletConnectorBlock extends ConnectorBlock {
	public InletConnectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new InletBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hitResult) {
		if (!stack.is(ModItems.SORTING_CARD)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InletBlockEntity inlet) {
			ItemStack previous = inlet.getCard();
			inlet.setCard(stack.copyWithCount(1));
			stack.consume(1, player);
			if (!previous.isEmpty()) {
				giveOrDrop(player, previous);
			}
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("block.copperpipes.inlet_connector.card_inserted"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level.getBlockEntity(pos) instanceof InletBlockEntity inlet) || inlet.getCard().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack card = inlet.getCard();
			inlet.setCard(ItemStack.EMPTY);
			giveOrDrop(player, card);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	private static void giveOrDrop(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		}
	}
}
