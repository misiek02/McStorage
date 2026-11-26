package pl.misiek.copperpipes.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.card.SortingCard;

public final class ModComponents {
	public static final DataComponentType<SortingCard> SORTING_CARD = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			CopperPipes.id("sorting_card"),
			DataComponentType.<SortingCard>builder().persistent(SortingCard.CODEC).networkSynchronized(SortingCard.STREAM_CODEC).build());

	private ModComponents() {
	}

	public static void init() {
		// Triggers static initialisation.
	}
}
