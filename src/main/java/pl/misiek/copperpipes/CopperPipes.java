package pl.misiek.copperpipes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.misiek.copperpipes.network.PipeNetworks;
import pl.misiek.copperpipes.registry.ModBlockEntities;
import pl.misiek.copperpipes.registry.ModBlocks;
import pl.misiek.copperpipes.registry.ModComponents;
import pl.misiek.copperpipes.registry.ModCreativeTab;
import pl.misiek.copperpipes.registry.ModItems;

public class CopperPipes implements ModInitializer {
	public static final String MOD_ID = "copperpipes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModCreativeTab.init();
		PipeNetworks.init();
		LOGGER.info("Copper Pipes initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
