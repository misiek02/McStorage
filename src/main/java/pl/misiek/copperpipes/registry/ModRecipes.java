package pl.misiek.copperpipes.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.card.CardCopyRecipe;

public final class ModRecipes {
	private ModRecipes() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, CopperPipes.id("sorting_card_copy"), CardCopyRecipe.SERIALIZER);
	}
}
