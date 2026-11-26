package pl.misiek.copperpipes.command;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.PipeConnections;
import pl.misiek.copperpipes.block.entity.InletBlockEntity;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.registry.ModBlocks;
import pl.misiek.copperpipes.registry.ModComponents;
import pl.misiek.copperpipes.registry.ModItems;

/**
 * {@code /copperpipes demo}: a showcase sorting system next to the player. A barrel of mixed items feeds a main
 * pipe with branches to labelled barrels, each with a sorting card; whatever matches nothing ends up in the
 * overflow barrel at the end. It shows tags, single items, a mod rule, an exclusion and priorities.
 *
 * <pre>
 *            [oak] [planks] [valuables] [food] [redstone] [tools]        (north branches)
 *   [input][>]=====+========+===========+======+==========+=======[>][overflow]
 *            [logs] [stone] [ores]      [dirt] [wool]     [pipes]        (south branches)
 * </pre>
 */
final class DemoBuilder {
	private static final int FIRST_BRANCH_X = 3;
	private static final int BRANCH_SPACING = 3;
	private static final int BRANCH_LENGTH = 2;

	private record Category(String key, int priority, List<String> rules) {
	}

	/** North/south pairs, from the input towards the overflow. */
	private static final List<Category> CATEGORIES = List.of(
			new Category("oak", 5, List.of("minecraft:oak_log")),
			new Category("logs", 0, List.of("#minecraft:logs")),
			new Category("planks", 0, List.of("#minecraft:planks")),
			new Category("stone", 0, List.of("#c:stones", "#c:cobblestones", "!minecraft:granite")),
			new Category("valuables", 5, List.of("minecraft:diamond", "minecraft:emerald")),
			new Category("ores", 0, List.of("#c:ingots", "#c:raw_materials", "#c:gems", "#c:ores")),
			new Category("food", 0, List.of("#c:foods")),
			new Category("dirt", 0, List.of("minecraft:dirt", "minecraft:grass_block", "minecraft:gravel", "#minecraft:sand")),
			new Category("redstone", 0, List.of("minecraft:redstone", "minecraft:repeater", "minecraft:comparator",
					"minecraft:redstone_torch", "minecraft:piston")),
			new Category("wool", 0, List.of("#minecraft:wool")),
			new Category("tools", 0, List.of("#c:tools")),
			new Category("pipes", 0, List.of("@copperpipes")));

	private DemoBuilder() {
	}

	static int build(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos input = BlockPos.containing(source.getPosition()).east(2);
		int branches = CATEGORIES.size() / 2;
		int trunkEnd = FIRST_BRANCH_X + (branches - 1) * BRANCH_SPACING + 2;
		List<BlockPos> connectable = new ArrayList<>();
		List<Runnable> afterConnect = new ArrayList<>();

		barrel(level, input, "input");
		fillInput(level, input);
		place(level, connectable, input.east(1), connector(ModBlocks.OUTLET_CONNECTOR, Direction.WEST));
		for (int x = 2; x <= trunkEnd; x++) {
			place(level, connectable, input.east(x), ModBlocks.COPPER_PIPE.waxed().unaffected().defaultBlockState());
		}
		place(level, connectable, input.east(trunkEnd + 1), connector(ModBlocks.INLET_CONNECTOR, Direction.EAST));
		barrel(level, input.east(trunkEnd + 2), "overflow");

		for (int i = 0; i < CATEGORIES.size(); i++) {
			Category category = CATEGORIES.get(i);
			Direction side = i % 2 == 0 ? Direction.NORTH : Direction.SOUTH;
			Block pipe = side == Direction.NORTH ? ModBlocks.COPPER_PIPE.waxed().exposed() : ModBlocks.COPPER_PIPE.waxed().weathered();
			BlockPos branchStart = input.east(FIRST_BRANCH_X + (i / 2) * BRANCH_SPACING);
			for (int step = 1; step <= BRANCH_LENGTH; step++) {
				place(level, connectable, branchStart.relative(side, step), pipe.defaultBlockState());
			}
			BlockPos inlet = branchStart.relative(side, BRANCH_LENGTH + 1);
			place(level, connectable, inlet, connector(ModBlocks.INLET_CONNECTOR, side));
			barrel(level, inlet.relative(side), category.key());
			afterConnect.add(() -> {
				if (level.getBlockEntity(inlet) instanceof InletBlockEntity inletEntity) {
					ItemStack card = new ItemStack(ModItems.SORTING_CARD);
					card.set(ModComponents.SORTING_CARD, new SortingCard(category.rules(), category.priority(), false));
					inletEntity.setCard(card);
				}
			});
		}

		// setBlock skips placement logic, so compute connections once everything is in place.
		for (BlockPos pos : connectable) {
			level.setBlock(pos, PipeConnections.withConnections(level, pos, level.getBlockState(pos)), Block.UPDATE_ALL);
		}
		afterConnect.forEach(Runnable::run);

		source.sendSuccess(() -> Component.translatable("commands.copperpipes.demo", CATEGORIES.size()), true);
		return 1;
	}

	/** A barrel named after its category, so opening it shows what it collects. */
	private static void barrel(ServerLevel level, BlockPos pos, String key) {
		level.setBlock(pos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
			ItemStack named = new ItemStack(Items.BARREL);
			named.set(DataComponents.CUSTOM_NAME, Component.translatable("demo.copperpipes." + key));
			barrel.applyComponentsFromItemStack(named);
		}
	}

	private static void fillInput(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) {
			return;
		}
		Object[][] contents = {
				{Items.OAK_LOG, 32}, {Items.BIRCH_LOG, 32}, {Items.SPRUCE_LOG, 16}, {Items.OAK_PLANKS, 32}, {Items.SPRUCE_PLANKS, 16},
				{Items.COBBLESTONE, 64}, {Items.STONE, 32}, {Items.GRANITE, 16}, {Items.IRON_INGOT, 16}, {Items.RAW_COPPER, 16},
				{Items.DIAMOND, 4}, {Items.EMERALD, 4}, {Items.BREAD, 16}, {Items.APPLE, 16}, {Items.COOKED_BEEF, 8},
				{Items.DIRT, 32}, {Items.SAND, 32}, {Items.GRAVEL, 16}, {Items.REDSTONE, 32}, {Items.REPEATER, 4},
				{vanilla("white_wool"), 16}, {vanilla("red_wool"), 16}, {Items.IRON_PICKAXE, 1}, {ModBlocks.COPPER_PIPE.weathering().unaffected().asItem(), 16},
				{Items.BONE, 8}, {Items.STRING, 8}, {Items.FEATHER, 8}};
		for (int slot = 0; slot < contents.length && slot < barrel.getContainerSize(); slot++) {
			barrel.setItem(slot, new ItemStack((Item) contents[slot][0], (Integer) contents[slot][1]));
		}
		barrel.setChanged();
	}

	private static Item vanilla(String name) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name));
	}

	private static BlockState connector(Block block, Direction facing) {
		return block.defaultBlockState().setValue(ConnectorBlock.FACING, facing);
	}

	private static void place(ServerLevel level, List<BlockPos> connectable, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		connectable.add(pos);
	}
}
