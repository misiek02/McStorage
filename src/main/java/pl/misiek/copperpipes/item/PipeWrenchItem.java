package pl.misiek.copperpipes.item;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.CopperPipeBlock;
import pl.misiek.copperpipes.block.PipeConnections;
import pl.misiek.copperpipes.block.PipeConnections.Side;

/**
 * Right-click a pipe to disconnect / reconnect the side you clicked (an arm, or the core face pointing at a
 * neighbour). Right-click a connector to turn it to the next side, preferring sides with a container.
 * Sneak + right-click picks a pipe or connector up.
 */
public class PipeWrenchItem extends Item {
	/** Hits further than this from the block centre (in blocks) are on an arm rather than the core. */
	private static final double CORE_HALF_SIZE = 0.26;

	public PipeWrenchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		boolean pipe = state.getBlock() instanceof CopperPipeBlock;
		if (!pipe && !(state.getBlock() instanceof ConnectorBlock)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		if (player != null && player.isSecondaryUseActive()) {
			pickUp(level, pos, state, player);
		} else if (pipe) {
			Direction side = clickedSide(context);
			boolean disabled = toggleSide(level, pos, side);
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(disabled ? "item.copperpipes.pipe_wrench.disabled" : "item.copperpipes.pipe_wrench.enabled",
						Component.translatable("direction.copperpipes." + side.getSerializedName())));
			}
		} else {
			rotate(level, pos, state);
		}
		level.playSound(null, pos, SoundEvents.COPPER_HIT, SoundSource.BLOCKS, 1.0F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	/** The side of the pipe the player aimed at: an arm if the hit is outside the core, otherwise the face hit. */
	private static Direction clickedSide(UseOnContext context) {
		Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
		double furthest = Math.max(Math.abs(offset.x), Math.max(Math.abs(offset.y), Math.abs(offset.z)));
		return furthest > CORE_HALF_SIZE ? Direction.getApproximateNearest(offset) : context.getClickedFace();
	}

	/** Disables a connected/free side, or re-enables a disabled one. Returns true if the side is now disabled. */
	public static boolean toggleSide(Level level, BlockPos pos, Direction side) {
		BlockState state = level.getBlockState(pos);
		EnumProperty<Side> property = PipeConnections.PROPERTY_BY_DIRECTION.get(side);
		boolean disable = state.getValue(property) != Side.DISABLED;
		BlockState updated = disable
				? state.setValue(property, Side.DISABLED)
				: PipeConnections.updateSide(state.setValue(property, Side.NONE), side, level.getBlockState(pos.relative(side)));
		level.setBlock(pos, updated, Block.UPDATE_ALL); // the neighbour re-checks its side through updateShape
		return disable;
	}

	/** Turns a connector to the next side that has a container, or simply the next side if none has one. */
	private static void rotate(Level level, BlockPos pos, BlockState state) {
		Direction current = state.getValue(ConnectorBlock.FACING);
		Direction[] all = Direction.values();
		Direction next = all[(current.ordinal() + 1) % all.length];
		for (int i = 1; i < all.length; i++) {
			Direction candidate = all[(current.ordinal() + i) % all.length];
			if (ItemStorage.SIDED.find(level, pos.relative(candidate), candidate.getOpposite()) != null) {
				next = candidate;
				break;
			}
		}
		BlockState rotated = PipeConnections.withoutConnections(state).setValue(ConnectorBlock.FACING, next);
		level.setBlock(pos, PipeConnections.withConnections(level, pos, rotated), Block.UPDATE_ALL);
	}

	private static void pickUp(Level level, BlockPos pos, BlockState state, Player player) {
		ItemStack item = new ItemStack(state.getBlock());
		level.removeBlock(pos, false); // runs the usual removal: a card in an inlet drops, items in transit fall out
		if (!player.hasInfiniteMaterials() && !player.getInventory().add(item)) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
		}
	}
}
