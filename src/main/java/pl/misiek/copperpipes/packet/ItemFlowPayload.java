package pl.misiek.copperpipes.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import pl.misiek.copperpipes.CopperPipes;

/**
 * Server -> client, one per transferred stack: everything the client needs to animate it along its path.
 * Purely visual; the server never waits for or trusts anything about it.
 *
 * @param stack     what is travelling (only used for rendering)
 * @param outlet    where the item starts
 * @param path      blocks after the outlet up to and including the inlet
 * @param times     ticks after {@code startTick} at which the item reaches each path block's centre
 * @param startTick game time when the item left the outlet
 */
public record ItemFlowPayload(ItemStack stack, long outlet, long[] path, float[] times, long startTick) implements CustomPacketPayload {
	public static final Type<ItemFlowPayload> TYPE = new Type<>(CopperPipes.id("item_flow"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemFlowPayload> CODEC = StreamCodec.of(ItemFlowPayload::write, ItemFlowPayload::read);
	/** Players further away than this from the outlet never see the item, so they get no packet. */
	public static final double SEND_RADIUS = 96.0;
	private static final int MAX_PATH = 8192;

	@Override
	public Type<ItemFlowPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void sendAround(ServerLevel level, ItemFlowPayload payload) {
		for (ServerPlayer player : PlayerLookup.around(level, BlockPos.of(payload.outlet()), SEND_RADIUS)) {
			if (ServerPlayNetworking.canSend(player, TYPE)) { // clients without the mod just don't see the animation
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void write(RegistryFriendlyByteBuf buf, ItemFlowPayload payload) {
		ItemStack.STREAM_CODEC.encode(buf, payload.stack());
		buf.writeLong(payload.outlet());
		buf.writeVarInt(payload.path().length);
		for (int i = 0; i < payload.path().length; i++) {
			buf.writeLong(payload.path()[i]);
			buf.writeFloat(payload.times()[i]);
		}
		buf.writeVarLong(payload.startTick());
	}

	private static ItemFlowPayload read(RegistryFriendlyByteBuf buf) {
		ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
		long outlet = buf.readLong();
		int length = buf.readVarInt();
		if (length < 0 || length > MAX_PATH) {
			throw new IllegalArgumentException("Pipe path too long: " + length);
		}
		long[] path = new long[length];
		float[] times = new float[length];
		for (int i = 0; i < length; i++) {
			path[i] = buf.readLong();
			times[i] = buf.readFloat();
		}
		return new ItemFlowPayload(stack, outlet, path, times, buf.readVarLong());
	}
}
