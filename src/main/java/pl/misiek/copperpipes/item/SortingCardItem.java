package pl.misiek.copperpipes.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import pl.misiek.copperpipes.card.CardFilter;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.registry.ModComponents;

/** Filter card for inlet connectors; its rules live in the {@link ModComponents#SORTING_CARD} component. */
public class SortingCardItem extends Item {
	public SortingCardItem(Properties properties) {
		super(properties);
	}

	public static SortingCard cardOf(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SORTING_CARD, SortingCard.EMPTY);
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
		for (String rule : card.rules()) {
			boolean valid = CardFilter.Rule.parse(rule).isPresent();
			builder.accept(Component.literal("  " + rule).withStyle(valid ? ChatFormatting.AQUA : ChatFormatting.RED));
		}
	}
}
