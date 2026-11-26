package pl.misiek.copperpipes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.misiek.copperpipes.command.CopperPipesCommand;
import pl.misiek.copperpipes.network.PipeNetworks;
import pl.misiek.copperpipes.packet.ItemFlowPayload;
import pl.misiek.copperpipes.packet.UpdateCardPayload;
import pl.misiek.copperpipes.registry.ModBlockEntities;
import pl.misiek.copperpipes.registry.ModBlocks;
import pl.misiek.copperpipes.registry.ModComponents;
import pl.misiek.copperpipes.registry.ModCreativeTab;
import pl.misiek.copperpipes.registry.ModItems;
import pl.misiek.copperpipes.registry.ModMenus;
import pl.misiek.copperpipes.registry.ModRecipes;

public class CopperPipes implements ModInitializer {
	public static final String MOD_ID = "copperpipes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModRecipes.init();
		ModCreativeTab.init();
		PipeNetworks.init();
		UpdateCardPayload.register();
		ItemFlowPayload.register();
		CopperPipesCommand.register();
		LOGGER.info("Copper Pipes initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
