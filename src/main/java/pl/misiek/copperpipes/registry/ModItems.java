package pl.misiek.copperpipes.registry;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.item.PipeWrenchItem;
import pl.misiek.copperpipes.item.SortingCardItem;

public final class ModItems {
	public static final Item SORTING_CARD = register("sorting_card", SortingCardItem::new,
			new Item.Properties().stacksTo(16).component(ModComponents.SORTING_CARD, SortingCard.EMPTY));
	public static final Item PIPE_WRENCH = register("pipe_wrench", PipeWrenchItem::new, new Item.Properties().stacksTo(1));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CopperPipes.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		// Triggers static initialisation.
	}
}
