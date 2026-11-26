package pl.misiek.copperpipes.packet;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.menu.CardEditMenu;
import pl.misiek.copperpipes.registry.ModComponents;

/** Client -> server: save the card being edited in the card editor. */
public record UpdateCardPayload(SortingCard card) implements CustomPacketPayload {
	public static final int MAX_RULE_LENGTH = 128;
	public static final Type<UpdateCardPayload> TYPE = new Type<>(CopperPipes.id("update_card"));
	public static final StreamCodec<ByteBuf, UpdateCardPayload> CODEC = SortingCard.STREAM_CODEC.map(UpdateCardPayload::new, UpdateCardPayload::card);

	@Override
	public Type<UpdateCardPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> apply(context.player(), payload.card()));
	}

	/** Never trust the client: only a card in the player's hand is changed, and the data is cleaned up first. */
	private static void apply(ServerPlayer player, SortingCard card) {
		InteractionHand hand = CardEditMenu.cardHand(player);
		if (hand == null) {
			return;
		}
		List<String> rules = card.rules().stream()
				.map(String::trim)
				.filter(rule -> !rule.isEmpty() && rule.length() <= MAX_RULE_LENGTH)
				.limit(SortingCard.MAX_RULES)
				.toList();
		SortingCard cleaned = new SortingCard(rules, card.priority(), card.blacklist());
		ItemStack held = player.getItemInHand(hand);
		if (held.getCount() == 1) {
			held.set(ModComponents.SORTING_CARD, cleaned);
			return;
		}
		// Editing one card of a stack: split it off, the rest of the stack stays as it was.
		ItemStack edited = held.split(1);
		edited.set(ModComponents.SORTING_CARD, cleaned);
		if (!player.getInventory().add(edited)) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), edited);
		}
	}
}
