package pl.misiek.copperpipes.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import pl.misiek.copperpipes.client.render.FlowingItems;
import pl.misiek.copperpipes.client.screen.CardEditScreen;
import pl.misiek.copperpipes.registry.ModMenus;

public class CopperPipesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		MenuScreens.register(ModMenus.CARD_EDIT, CardEditScreen::new);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CardEditScreen.clearRegistryNames());
		FlowingItems.init();
		FpsCommand.register();
	}
}
