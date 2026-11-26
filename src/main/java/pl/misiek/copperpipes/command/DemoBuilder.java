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
 * {@code /copperpipes demo}: a showcase sorting system next to the player in which items circulate forever.
 *
 * <p>Sorting network (top): the input barrel's outlet feeds a main pipe that climbs and dips between heights 2
 * and 5; twelve branches run out sideways and drop down to labelled barrels with sorting cards; whatever no card
 * takes reaches the overflow barrel at the end.
 *
 * <p>Return network (bottom): every sorted barrel and the overflow barrel has an outlet underneath; pipes along
 * the ground carry everything back into the input barrel through an inlet below it. The two networks never touch,
 * so they stay separate and the items keep going round.
 *
 * <p>Coordinates are relative to the input barrel's column: x runs away from the player (east), z sideways.
 */
public final class DemoBuilder {
	private static final int SORT_Y = 2;
	private static final int OVERFLOW_X = 22;
	/** Barrels sit this far to the north/south of the main pipe. */
	private static final int BARREL_Z = 5;
	/** Corners of the main pipe (x, y); it runs along z = 0. */
	private static final int[][] TRUNK = {{2, 2}, {4, 2}, {4, 5}, {8, 5}, {8, 3}, {12, 3}, {12, 5}, {16, 5}, {16, 2}, {20, 2}};
	/** x of each branch pair; the branch starts at the main pipe's height there. */
	private static final int[] BRANCH_X = {3, 6, 10, 14, 18, 20};

	private record Category(String key, int priority, List<String> rules) {
	}

	/** North/south pairs, in BRANCH_X order from the input towards the overflow. */
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

	private final ServerLevel level;
	private final BlockPos origin;
	private final List<BlockPos> connectable = new ArrayList<>();
	private final List<Runnable> afterConnect = new ArrayList<>();

	private DemoBuilder(ServerLevel level, BlockPos origin) {
		this.level = level;
		this.origin = origin;
	}

	/** Builds the demo with the input barrel's column at {@code origin} (y = ground level of the return pipes). */
	public static void buildAt(ServerLevel level, BlockPos origin) {
		new DemoBuilder(level, origin).build();
	}

	static int build(CommandSourceStack source) {
		new DemoBuilder(source.getLevel(), BlockPos.containing(source.getPosition()).east(2)).build();
		source.sendSuccess(() -> Component.translatable("commands.copperpipes.demo", CATEGORIES.size()), true);
		return 1;
	}

	private void build() {
		BlockState trunkPipe = ModBlocks.COPPER_PIPE.waxed().unaffected().defaultBlockState();
		BlockState branchPipe = ModBlocks.COPPER_PIPE.waxed().exposed().defaultBlockState();
		BlockState returnPipe = ModBlocks.COPPER_PIPE.waxed().weathered().defaultBlockState();

		// Input barrel: outlet into the sorting network on its east side, inlet from the return network below.
		barrel(at(0, SORT_Y, 0), "input");
		fillInput(at(0, SORT_Y, 0));
		place(at(1, SORT_Y, 0), connector(ModBlocks.OUTLET_CONNECTOR, Direction.WEST));
		place(at(0, SORT_Y - 1, 0), connector(ModBlocks.INLET_CONNECTOR, Direction.UP));

		// Main pipe, climbing and dipping.
		for (int i = 1; i < TRUNK.length; i++) {
			line(at(TRUNK[i - 1][0], TRUNK[i - 1][1], 0), at(TRUNK[i][0], TRUNK[i][1], 0), trunkPipe);
		}
		place(at(OVERFLOW_X - 1, SORT_Y, 0), connector(ModBlocks.INLET_CONNECTOR, Direction.EAST));
		barrel(at(OVERFLOW_X, SORT_Y, 0), "overflow");
		returnOutletUnder(at(OVERFLOW_X, SORT_Y, 0));

		// Branches: out sideways at the main pipe's height, then down to the barrel's level.
		for (int i = 0; i < CATEGORIES.size(); i++) {
			int x = BRANCH_X[i / 2];
			int side = i % 2 == 0 ? -1 : 1;
			Direction facing = side < 0 ? Direction.NORTH : Direction.SOUTH;
			int y = trunkHeightAt(x);
			line(at(x, y, side), at(x, y, side * (BARREL_Z - 2)), branchPipe);
			line(at(x, y, side * (BARREL_Z - 2)), at(x, SORT_Y, side * (BARREL_Z - 2)), branchPipe);
			BlockPos inlet = at(x, SORT_Y, side * (BARREL_Z - 1));
			place(inlet, connector(ModBlocks.INLET_CONNECTOR, facing));
			BlockPos barrel = at(x, SORT_Y, side * BARREL_Z);
			barrel(barrel, CATEGORIES.get(i).key());
			returnOutletUnder(barrel);
			Category category = CATEGORIES.get(i);
			this.afterConnect.add(() -> setCard(inlet, category));
		}

		// Return network along the ground: one line under each barrel row, both joining under the input barrel.
		int groundY = SORT_Y - 2;
		for (int side : new int[] {-1, 1}) {
			line(at(OVERFLOW_X, groundY, side * BARREL_Z), at(0, groundY, side * BARREL_Z), returnPipe);
			line(at(0, groundY, side * BARREL_Z), at(0, groundY, 0), returnPipe);
		}
		line(at(OVERFLOW_X, groundY, -BARREL_Z), at(OVERFLOW_X, groundY, 0), returnPipe);

		// setBlock skips placement logic, so compute connections once everything is in place.
		for (BlockPos pos : this.connectable) {
			this.level.setBlock(pos, PipeConnections.withConnections(this.level, pos, this.level.getBlockState(pos)), Block.UPDATE_ALL);
		}
		this.afterConnect.forEach(Runnable::run);
	}

	private static int trunkHeightAt(int x) {
		for (int i = 1; i < TRUNK.length; i++) {
			int from = TRUNK[i - 1][0];
			int to = TRUNK[i][0];
			if (TRUNK[i - 1][1] == TRUNK[i][1] && x >= Math.min(from, to) && x <= Math.max(from, to)) {
				return TRUNK[i][1];
			}
		}
		throw new IllegalArgumentException("No horizontal main pipe segment at x=" + x);
	}

	private BlockPos at(int x, int y, int z) {
		return this.origin.offset(x, y, z); // y = 0 is the player's feet level, where the return pipes lie
	}

	/** Places pipes on every block of an axis-aligned segment, both ends included. */
	private void line(BlockPos from, BlockPos to, BlockState pipe) {
		BlockPos step = new BlockPos(Integer.signum(to.getX() - from.getX()), Integer.signum(to.getY() - from.getY()),
				Integer.signum(to.getZ() - from.getZ()));
		BlockPos pos = from;
		place(pos, pipe);
		while (!pos.equals(to)) {
			pos = pos.offset(step);
			place(pos, pipe);
		}
	}

	/** An outlet under a barrel, feeding the return pipe below it. */
	private void returnOutletUnder(BlockPos barrel) {
		place(barrel.below(), connector(ModBlocks.OUTLET_CONNECTOR, Direction.UP));
	}

	private void setCard(BlockPos inlet, Category category) {
		if (this.level.getBlockEntity(inlet) instanceof InletBlockEntity inletEntity) {
			ItemStack card = new ItemStack(ModItems.SORTING_CARD);
			card.set(ModComponents.SORTING_CARD, new SortingCard(category.rules(), category.priority(), false));
			inletEntity.setCard(card);
		}
	}

	/** A barrel named after its category, so opening it shows what it collects. */
	private void barrel(BlockPos pos, String key) {
		this.level.setBlock(pos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
		if (this.level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
			ItemStack named = new ItemStack(Items.BARREL);
			named.set(DataComponents.CUSTOM_NAME, Component.translatable("demo.copperpipes." + key));
			barrel.applyComponentsFromItemStack(named);
		}
	}

	private void fillInput(BlockPos pos) {
		if (!(this.level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) {
			return;
		}
		Object[][] contents = {
				{Items.OAK_LOG, 32}, {Items.BIRCH_LOG, 32}, {Items.SPRUCE_LOG, 16}, {Items.OAK_PLANKS, 32}, {Items.SPRUCE_PLANKS, 16},
				{Items.COBBLESTONE, 64}, {Items.STONE, 32}, {Items.GRANITE, 16}, {Items.IRON_INGOT, 16}, {Items.RAW_COPPER, 16},
				{Items.DIAMOND, 4}, {Items.EMERALD, 4}, {Items.BREAD, 16}, {Items.APPLE, 16}, {Items.COOKED_BEEF, 8},
				{Items.DIRT, 32}, {Items.SAND, 32}, {Items.GRAVEL, 16}, {Items.REDSTONE, 32}, {Items.REPEATER, 4},
				{vanilla("white_wool"), 16}, {vanilla("red_wool"), 16}, {Items.IRON_PICKAXE, 1},
				{ModBlocks.COPPER_PIPE.weathering().unaffected().asItem(), 16},
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

	private void place(BlockPos pos, BlockState state) {
		this.level.setBlock(pos, state, Block.UPDATE_ALL);
		this.connectable.add(pos);
	}
}
