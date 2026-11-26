package pl.misiek.copperpipes.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.item.SortingCardItem;

public final class ModItems {
	public static final Item SORTING_CARD = register("sorting_card",
			new Item.Properties().stacksTo(16).component(ModComponents.SORTING_CARD, SortingCard.EMPTY));

	private ModItems() {
	}

	private static Item register(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CopperPipes.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new SortingCardItem(properties.setId(key)));
	}

	public static void init() {
		// Triggers static initialisation.
	}
}
