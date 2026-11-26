package pl.misiek.copperpipes.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.menu.CardEditMenu;

public final class ModMenus {
	public static final MenuType<CardEditMenu> CARD_EDIT = Registry.register(BuiltInRegistries.MENU, CopperPipes.id("card_edit"),
			new MenuType<>(CardEditMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
		// Triggers static initialisation.
	}
}
