package pl.misiek.copperpipes.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import pl.misiek.copperpipes.CopperPipes;

public final class ModCreativeTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CopperPipes.id("main"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.copperpipes.main"))
					.icon(() -> new ItemStack(ModBlocks.COPPER_PIPE.weathering().unaffected()))
					.displayItems((parameters, output) -> {
						ModBlocks.COPPER_PIPE.forEach(output::accept);
						output.accept(ModBlocks.OUTLET_CONNECTOR);
						output.accept(ModBlocks.INLET_CONNECTOR);
					})
					.build());

	private ModCreativeTab() {
	}

	public static void init() {
		// Triggers static initialisation.
	}
}
