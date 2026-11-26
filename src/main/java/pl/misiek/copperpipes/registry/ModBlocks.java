package pl.misiek.copperpipes.registry;

import java.util.function.Function;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.state.BlockBehaviour;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.block.CopperPipeBlock;
import pl.misiek.copperpipes.block.WeatheringCopperPipeBlock;

public final class ModBlocks {
	/** copper_pipe, exposed_copper_pipe, ... and the four waxed_* variants. */
	public static final WeatheringCopperCollection<Block> COPPER_PIPE = WeatheringCopperCollection.registerBlocks(
			WeatheringCopperCollection.prefixWithState(WeatheringCopperCollection.create("copper_pipe")),
			ModBlocks::register,
			CopperPipeBlock::new,
			WeatheringCopperPipeBlock::new,
			state -> BlockBehaviour.Properties.of()
					.mapColor(Blocks.COPPER_BLOCK.weathering().pick(state).defaultMapColor())
					.strength(3.0F, 6.0F)
					.sound(SoundType.COPPER)
					.requiresCorrectToolForDrops()
					.noOcclusion()
					.isRedstoneConductor((s, level, pos) -> false)
					.isSuffocating((s, level, pos) -> false));

	private ModBlocks() {
	}

	/** Registers a block together with its BlockItem. */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, CopperPipes.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CopperPipes.id(name));
		Item item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return block;
	}

	public static void init() {
		// Oxidation stages, axe scraping and honeycomb waxing all come from this vanilla-compatible registry.
		OxidizableBlocksRegistry.registerWeatheringCopperBlocks(COPPER_PIPE);
	}
}
