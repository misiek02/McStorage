package pl.misiek.copperpipes.registry;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.block.CopperPipeBlock;

public final class ModBlocks {
	public static final Block COPPER_PIPE = register("copper_pipe", CopperPipeBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(3.0F, 6.0F)
					.sound(SoundType.COPPER)
					.requiresCorrectToolForDrops()
					.noOcclusion());

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
		// Triggers static initialisation.
	}
}
