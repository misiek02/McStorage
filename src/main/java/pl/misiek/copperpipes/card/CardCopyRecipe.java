package pl.misiek.copperpipes.card;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import pl.misiek.copperpipes.registry.ModItems;

/** Sorting card + paper = two copies of the card (same rules, priority and mode). */
public class CardCopyRecipe extends CustomRecipe {
	public static final CardCopyRecipe INSTANCE = new CardCopyRecipe();
	public static final RecipeSerializer<CardCopyRecipe> SERIALIZER = new RecipeSerializer<>(
			MapCodec.unit(INSTANCE), StreamCodec.<RegistryFriendlyByteBuf, CardCopyRecipe>unit(INSTANCE));

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !findCard(input).isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return findCard(input).copyWithCount(2);
	}

	/** The card if the grid holds exactly one sorting card and one paper, otherwise empty. */
	private static ItemStack findCard(CraftingInput input) {
		ItemStack card = ItemStack.EMPTY;
		int paper = 0;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModItems.SORTING_CARD) && card.isEmpty()) {
				card = stack;
			} else if (stack.is(Items.PAPER)) {
				paper++;
			} else {
				return ItemStack.EMPTY;
			}
		}
		return paper == 1 ? card : ItemStack.EMPTY;
	}

	@Override
	public RecipeSerializer<CardCopyRecipe> getSerializer() {
		return SERIALIZER;
	}
}
