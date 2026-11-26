package pl.misiek.copperpipes.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.PipeConnections;
import pl.misiek.copperpipes.block.entity.InletBlockEntity;
import pl.misiek.copperpipes.card.CardFilter;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.network.PipeNetworks;
import pl.misiek.copperpipes.registry.ModBlocks;
import pl.misiek.copperpipes.registry.ModComponents;
import pl.misiek.copperpipes.registry.ModItems;

/**
 * Network and sorting tests. Common layout (y = 1, inside the 8x8x8 empty structure):
 * <pre>
 *   z=1:  [source barrel] [outlet] [pipe x=2..6]
 *   z=2:                          inlet A at x=3, inlet B at x=6 (both facing +z)
 *   z=3:                          barrel A at x=3, barrel B at x=6
 * </pre>
 * Inlet A is nearer to the outlet than inlet B.
 */
public class CopperPipesGameTests {
	private static final BlockPos SOURCE = new BlockPos(0, 1, 1);
	private static final BlockPos OUTLET = new BlockPos(1, 1, 1);
	private static final BlockPos INLET_A = new BlockPos(3, 1, 2);
	private static final BlockPos INLET_B = new BlockPos(6, 1, 2);
	private static final BlockPos BARREL_A = new BlockPos(3, 1, 3);
	private static final BlockPos BARREL_B = new BlockPos(6, 1, 3);

	// ---------------------------------------------------------------- network (milestone 6)

	@GameTest(maxTicks = 200)
	public void transfersItemsThroughPipes(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		helper.destroyBlock(INLET_A); // only inlet B remains
		reconnect(helper);
		fill(helper, SOURCE, new ItemStack(Items.STONE, 40));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, BARREL_B, Items.STONE) == 40, "all 40 stone should arrive in barrel B");
			helper.assertTrue(count(helper, SOURCE, Items.STONE) == 0, "source should be empty");
		});
	}

	@GameTest(maxTicks = 300)
	public void dropsItemsInTransitWhenPipeBreaks(GameTestHelper helper) {
		// Waxed oxidized pipes are slow (16 ticks per block) and never change, so items are still on the way.
		buildLayout(helper, ModBlocks.COPPER_PIPE.waxed().oxidized());
		helper.destroyBlock(INLET_A);
		reconnect(helper);
		fill(helper, SOURCE, new ItemStack(Items.DIAMOND, 16));

		helper.runAfterDelay(20, () -> {
			helper.assertTrue(PipeNetworks.get(helper.getLevel()).itemsInTransitThrough(helper.absolutePos(new BlockPos(4, 1, 1))) == 1, "one stack should be travelling through x=4");
			helper.destroyBlock(new BlockPos(4, 1, 1));
		});
		helper.runAfterDelay(25, () -> {
			helper.assertItemEntityPresent(Items.DIAMOND, new BlockPos(4, 1, 1), 2.0);
			helper.assertTrue(PipeNetworks.get(helper.getLevel()).itemsInTransitThrough(helper.absolutePos(INLET_B)) == 0, "nothing should be left in transit to inlet B");
			helper.assertTrue(count(helper, BARREL_B, Items.DIAMOND) == 0, "nothing should reach barrel B");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- sorting (milestone 7)

	@GameTest(maxTicks = 200)
	public void filteredInletGetsMatchesAndOverflowGetsTheRest(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		setCard(helper, INLET_A, new SortingCard(List.of("minecraft:stone"), 0, false));
		fill(helper, SOURCE, new ItemStack(Items.STONE, 16), new ItemStack(Items.DIRT, 16));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, BARREL_A, Items.STONE) == 16, "stone goes to the filtered inlet A");
			helper.assertTrue(count(helper, BARREL_A, Items.DIRT) == 0, "dirt must not go to inlet A");
			helper.assertTrue(count(helper, BARREL_B, Items.DIRT) == 16, "dirt goes to the overflow inlet B");
		});
	}

	@GameTest(maxTicks = 200)
	public void higherPriorityWinsOverNearer(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		setCard(helper, INLET_A, new SortingCard(List.of("#minecraft:logs"), 1, false));
		setCard(helper, INLET_B, new SortingCard(List.of("#minecraft:logs"), 5, false));
		fill(helper, SOURCE, new ItemStack(Items.OAK_LOG, 32));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, BARREL_B, Items.OAK_LOG) == 32, "priority 5 inlet B gets all logs");
			helper.assertTrue(count(helper, BARREL_A, Items.OAK_LOG) == 0, "nearer priority 1 inlet A gets none");
		});
	}

	@GameTest(maxTicks = 200)
	public void nearestWinsWithinSamePriority(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		setCard(helper, INLET_A, new SortingCard(List.of("minecraft:stone"), 3, false));
		setCard(helper, INLET_B, new SortingCard(List.of("minecraft:stone"), 3, false));
		fill(helper, SOURCE, new ItemStack(Items.STONE, 32));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, BARREL_A, Items.STONE) == 32, "nearer inlet A gets everything");
			helper.assertTrue(count(helper, BARREL_B, Items.STONE) == 0, "farther inlet B gets nothing");
		});
	}

	@GameTest(maxTicks = 200)
	public void modRuleWithExclusion(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		setCard(helper, INLET_A, new SortingCard(List.of("@minecraft", "!minecraft:dirt"), 0, false));
		fill(helper, SOURCE, new ItemStack(Items.STONE, 16), new ItemStack(Items.DIRT, 16));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, BARREL_A, Items.STONE) == 16, "stone matches @minecraft");
			helper.assertTrue(count(helper, BARREL_B, Items.DIRT) == 16, "dirt is excluded and overflows");
		});
	}

	@GameTest(maxTicks = 200)
	public void blacklistCard(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		setCard(helper, INLET_A, new SortingCard(List.of("minecraft:dirt"), 0, true));
		fill(helper, SOURCE, new ItemStack(Items.STONE, 16), new ItemStack(Items.DIRT, 16));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, BARREL_A, Items.STONE) == 16, "blacklist accepts stone");
			helper.assertTrue(count(helper, BARREL_A, Items.DIRT) == 0, "blacklist rejects dirt");
			helper.assertTrue(count(helper, BARREL_B, Items.DIRT) == 16, "dirt overflows");
		});
	}

	@GameTest(maxTicks = 200)
	public void unmatchedItemsStayWithoutOverflow(GameTestHelper helper) {
		buildLayout(helper, ModBlocks.COPPER_PIPE.weathering().unaffected());
		setCard(helper, INLET_A, new SortingCard(List.of("minecraft:stone"), 0, false));
		setCard(helper, INLET_B, new SortingCard(List.of("minecraft:cobblestone"), 0, false));
		fill(helper, SOURCE, new ItemStack(Items.STONE, 16), new ItemStack(Items.DIRT, 16));

		helper.runAfterDelay(80, () -> {
			helper.assertTrue(count(helper, BARREL_A, Items.STONE) == 16, "stone sorted");
			helper.assertTrue(count(helper, SOURCE, Items.DIRT) == 16, "dirt has nowhere to go and stays");
			helper.assertTrue(!PipeNetworks.get(helper.getLevel()).isOutletAwake(helper.absolutePos(OUTLET)), "the outlet sleeps when nothing can move");
			helper.succeed();
		});
	}

	@GameTest
	public void ruleParsingAndMatching(GameTestHelper helper) {
		CardFilter tags = CardFilter.compile(new SortingCard(List.of("#minecraft:logs", "!minecraft:birch_log"), 0, false));
		helper.assertTrue(tags.matches(Items.OAK_LOG), "#minecraft:logs matches oak log");
		helper.assertFalse(tags.matches(Items.BIRCH_LOG), "!minecraft:birch_log excludes birch log");
		helper.assertFalse(tags.matches(Items.STONE), "stone is not a log");

		CardFilter onlyExclusions = CardFilter.compile(new SortingCard(List.of("!minecraft:dirt"), 0, false));
		helper.assertTrue(onlyExclusions.matches(Items.STONE), "only exclusions: everything else matches");
		helper.assertFalse(onlyExclusions.matches(Items.DIRT), "only exclusions: dirt excluded");

		CardFilter empty = CardFilter.compile(SortingCard.EMPTY);
		helper.assertFalse(empty.matches(Items.STONE), "empty whitelist matches nothing");

		CardFilter invalid = CardFilter.compile(new SortingCard(List.of("#", "@", "!", "Not Valid!", "minecraft:stone"), 0, false));
		helper.assertTrue(invalid.matches(Items.STONE), "invalid rules are ignored, valid ones still work");
		helper.assertFalse(invalid.matches(Items.DIRT), "invalid rules match nothing");
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	private static void buildLayout(GameTestHelper helper, Block pipe) {
		helper.setBlock(SOURCE, Blocks.BARREL);
		helper.setBlock(BARREL_A, Blocks.BARREL);
		helper.setBlock(BARREL_B, Blocks.BARREL);
		helper.setBlock(OUTLET, ModBlocks.OUTLET_CONNECTOR.defaultBlockState().setValue(ConnectorBlock.FACING, Direction.WEST));
		for (int x = 2; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), pipe);
		}
		helper.setBlock(INLET_A, ModBlocks.INLET_CONNECTOR.defaultBlockState().setValue(ConnectorBlock.FACING, Direction.SOUTH));
		helper.setBlock(INLET_B, ModBlocks.INLET_CONNECTOR.defaultBlockState().setValue(ConnectorBlock.FACING, Direction.SOUTH));
		reconnect(helper);
	}

	/** setBlock skips placement logic, so compute the connection properties like a player placement would. */
	private static void reconnect(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
				var state = level.getBlockState(pos);
				if (state.hasProperty(PipeConnections.PROPERTY_BY_DIRECTION.get(Direction.NORTH))) {
					level.setBlock(pos, PipeConnections.withConnections(level, pos, state), Block.UPDATE_ALL);
				}
			}
		}
	}

	private static void setCard(GameTestHelper helper, BlockPos inlet, SortingCard card) {
		ItemStack stack = new ItemStack(ModItems.SORTING_CARD);
		stack.set(ModComponents.SORTING_CARD, card);
		helper.getBlockEntity(inlet, InletBlockEntity.class).setCard(stack);
	}

	private static void fill(GameTestHelper helper, BlockPos barrel, ItemStack... stacks) {
		BarrelBlockEntity container = helper.getBlockEntity(barrel, BarrelBlockEntity.class);
		for (int i = 0; i < stacks.length; i++) {
			container.setItem(i, stacks[i]);
		}
		container.setChanged();
	}

	private static int count(GameTestHelper helper, BlockPos barrel, Item item) {
		BarrelBlockEntity container = helper.getBlockEntity(barrel, BarrelBlockEntity.class);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
