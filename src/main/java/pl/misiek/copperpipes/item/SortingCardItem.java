package pl.misiek.copperpipes.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import pl.misiek.copperpipes.card.CardFilter;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.menu.CardEditMenu;
import pl.misiek.copperpipes.registry.ModComponents;

/** Filter card for inlet connectors; its rules live in the {@link ModComponents#SORTING_CARD} component. */
public class SortingCardItem extends Item {
	public SortingCardItem(Properties properties) {
		super(properties);
	}

	public static SortingCard cardOf(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SORTING_CARD, SortingCard.EMPTY);
	}

	/** Right-click opens the card editor. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CardEditMenu(id, inventory), this.getName(player.getItemInHand(hand))));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		SortingCard card = cardOf(stack);
		builder.accept(Component.translatable(card.blacklist() ? "item.copperpipes.sorting_card.blacklist" : "item.copperpipes.sorting_card.whitelist")
				.withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.copperpipes.sorting_card.priority", card.priority()).withStyle(ChatFormatting.GRAY));
		if (card.rules().isEmpty()) {
			builder.accept(Component.translatable("item.copperpipes.sorting_card.no_rules").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (String raw : card.rules()) {
			builder.accept(CardFilter.Rule.parse(raw)
					.map(rule -> Component.literal("  ").append(rule.describe()).withStyle(rule.negated() ? ChatFormatting.RED : ChatFormatting.AQUA))
					.orElseGet(() -> Component.literal("  ? " + raw).withStyle(ChatFormatting.DARK_RED)));
		}
	}
}
