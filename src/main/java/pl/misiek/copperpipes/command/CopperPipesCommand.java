package pl.misiek.copperpipes.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.PipeConnections;
import pl.misiek.copperpipes.network.PipeNetworkManager;
import pl.misiek.copperpipes.network.PipeNetworks;
import pl.misiek.copperpipes.registry.ModBlocks;

/**
 * Performance tools (operators only):
 * <ul>
 * <li>{@code /copperpipes stats} - pipe system cost in this dimension: ms per tick, networks, outlets, items.</li>
 * <li>{@code /copperpipes stress <rigs> <length>} - builds test rigs next to you in which items circulate forever
 *     between two barrels, to put load on the pipe system.</li>
 * </ul>
 */
public final class CopperPipesCommand {
	private static final int ROW_SPACING = 3;

	private CopperPipesCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("copperpipes")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("stats").executes(CopperPipesCommand::stats))
						.then(Commands.literal("stress")
								.then(Commands.argument("rigs", IntegerArgumentType.integer(1, 64))
										.then(Commands.argument("length", IntegerArgumentType.integer(1, 64))
												.executes(CopperPipesCommand::stress))))));
	}

	private static int stats(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		PipeNetworkManager manager = PipeNetworks.get(source.getLevel());
		long[] times = manager.tickTimeNanos();
		double serverMspt = source.getServer().getAverageTickTimeNanos() / 1_000_000.0;
		source.sendSuccess(() -> Component.translatable("commands.copperpipes.stats",
				String.format("%.3f", times[0] / 1_000_000.0),
				String.format("%.3f", times[1] / 1_000_000.0),
				String.format("%.2f", serverMspt),
				manager.networkCount(),
				manager.awakeOutletCount(),
				manager.itemsInTransit()), false);
		return 1;
	}

	/**
	 * Each rig (along +x from the player, rigs side by side along +z):
	 * <pre>
	 *   z:   [barrel A][outlet ->][pipe x length][-> inlet][barrel B]
	 *   z+1: [inlet ^ ][pipe x (length + 2)          ][outlet ^]
	 * </pre>
	 * The rows are disconnected from each other (wrench-style), so A's outlet feeds B and B's outlet feeds A: items loop.
	 */
	private static int stress(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		int rigs = IntegerArgumentType.getInteger(context, "rigs");
		int length = IntegerArgumentType.getInteger(context, "length");
		BlockPos origin = BlockPos.containing(source.getPosition()).east(2);
		List<BlockPos> connectable = new ArrayList<>();
		List<BlockPos> firstRow = new ArrayList<>();

		for (int r = 0; r < rigs; r++) {
			BlockPos a = origin.south(r * ROW_SPACING);
			BlockPos b = a.east(length + 3);
			level.setBlock(a, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(b, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

			place(level, connectable, a.east(1), connector(ModBlocks.OUTLET_CONNECTOR, Direction.WEST));
			for (int i = 0; i < length; i++) {
				place(level, connectable, a.east(2 + i), ModBlocks.COPPER_PIPE.waxed().unaffected().defaultBlockState());
			}
			place(level, connectable, b.west(1), connector(ModBlocks.INLET_CONNECTOR, Direction.EAST));
			for (int i = 1; i <= length + 2; i++) {
				firstRow.add(a.east(i));
			}

			place(level, connectable, b.south(1), connector(ModBlocks.OUTLET_CONNECTOR, Direction.NORTH));
			for (int i = 1; i <= length + 2; i++) {
				place(level, connectable, a.south(1).east(i), ModBlocks.COPPER_PIPE.waxed().unaffected().defaultBlockState());
			}
			place(level, connectable, a.south(1), connector(ModBlocks.INLET_CONNECTOR, Direction.NORTH));

			if (level.getBlockEntity(a) instanceof BarrelBlockEntity barrel) {
				for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
					barrel.setItem(slot, new ItemStack(slot % 2 == 0 ? Items.COBBLESTONE : Items.OAK_LOG, 64));
				}
				barrel.setChanged();
			}
		}
		// The two rows of a rig run side by side; disconnect them, as a player would with the wrench.
		for (BlockPos pos : firstRow) {
			level.setBlock(pos, level.getBlockState(pos).setValue(PipeConnections.PROPERTY_BY_DIRECTION.get(Direction.SOUTH), PipeConnections.Side.DISABLED), Block.UPDATE_ALL);
		}
		// setBlock skips placement logic, so compute connections once everything is in place.
		for (BlockPos pos : connectable) {
			level.setBlock(pos, PipeConnections.withConnections(level, pos, level.getBlockState(pos)), Block.UPDATE_ALL);
		}

		int outlets = rigs * 2;
		source.sendSuccess(() -> Component.translatable("commands.copperpipes.stress", rigs, length, outlets), true);
		return outlets;
	}

	private static BlockState connector(Block block, Direction facing) {
		return block.defaultBlockState().setValue(ConnectorBlock.FACING, facing);
	}

	private static void place(ServerLevel level, List<BlockPos> connectable, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		connectable.add(pos);
	}
}
