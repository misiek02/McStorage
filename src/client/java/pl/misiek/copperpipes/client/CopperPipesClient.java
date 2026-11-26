package pl.misiek.copperpipes.client;

import net.fabricmc.api.ClientModInitializer;
import pl.misiek.copperpipes.CopperPipes;

public class CopperPipesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CopperPipes.LOGGER.info("Copper Pipes client initialized");
	}
}
