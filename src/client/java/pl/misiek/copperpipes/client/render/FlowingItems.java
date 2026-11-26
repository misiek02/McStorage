package pl.misiek.copperpipes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.misiek.copperpipes.block.ConnectorBlock;
import pl.misiek.copperpipes.block.CopperPipeBlock;
import pl.misiek.copperpipes.client.ClientConfig;
import pl.misiek.copperpipes.packet.ItemFlowPayload;

/**
 * Client-side animation of items travelling through pipes. Each {@link ItemFlowPayload} becomes one moving item
 * that follows the path's block centres with the server's timing; nothing here affects the game state.
 */
public final class FlowingItems {
	private static final List<FlowingItem> ITEMS = new ArrayList<>();
	private static final float SCALE = 0.9F;

	private FlowingItems() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ItemFlowPayload.TYPE, (payload, context) -> add(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(FlowingItems::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(FlowingItems::submit);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ITEMS.clear());
	}

	private static void add(Minecraft client, ItemFlowPayload payload) {
		if (!ClientConfig.get().renderItems || client.level == null || payload.path().length == 0) {
			return;
		}
		Vec3[] points = new Vec3[payload.path().length + 1];
		float[] times = new float[points.length];
		points[0] = Vec3.atCenterOf(BlockPos.of(payload.outlet()));
		for (int i = 0; i < payload.path().length; i++) {
			points[i + 1] = Vec3.atCenterOf(BlockPos.of(payload.path()[i]));
			times[i + 1] = payload.times()[i];
		}
		ItemStackRenderState renderState = new ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(renderState, payload.stack(), ItemDisplayContext.GROUND, client.level, null,
				(int) payload.startTick());
		ITEMS.add(new FlowingItem(renderState, points, times, payload.startTick(), (int) (payload.startTick() * 7)));
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			ITEMS.clear();
			return;
		}
		long now = client.level.getGameTime();
		ITEMS.removeIf(item -> item.dead || now > item.startTick + item.times[item.times.length - 1] + 1);
	}

	private static void submit(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		ClientConfig config = ClientConfig.get();
		if (level == null || ITEMS.isEmpty() || !config.renderItems) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		double maxDistanceSqr = (double) config.renderDistance * config.renderDistance;
		double now = level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack poseStack = context.poseStack();
		int drawn = 0;

		for (Iterator<FlowingItem> it = ITEMS.iterator(); it.hasNext() && drawn < config.maxVisibleItems; ) {
			FlowingItem item = it.next();
			Vec3 pos = item.positionAt(now - item.startTick);
			if (pos == null || pos.distanceToSqr(camera) > maxDistanceSqr) {
				continue;
			}
			BlockPos blockPos = BlockPos.containing(pos);
			BlockState state = level.getBlockState(blockPos);
			if (!(state.getBlock() instanceof CopperPipeBlock || state.getBlock() instanceof ConnectorBlock)) {
				item.dead = true; // the pipe was broken; the server dropped the real item
				continue;
			}

			poseStack.pushPose();
			poseStack.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
			poseStack.rotateDegrees(Axis.YP, (float) ((now * 4 + item.spinOffset) % 360));
			poseStack.scale(SCALE, SCALE, SCALE);
			AABB box = item.renderState.getModelBoundingBox();
			poseStack.translate(-box.getCenter().x, -box.getCenter().y, -box.getCenter().z);
			int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, blockPos), level.getBrightness(LightLayer.SKY, blockPos));
			item.renderState.submit(poseStack, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
			drawn++;
		}
	}

	private static final class FlowingItem {
		final ItemStackRenderState renderState;
		final Vec3[] points;
		final float[] times;
		final long startTick;
		final int spinOffset;
		boolean dead;

		FlowingItem(ItemStackRenderState renderState, Vec3[] points, float[] times, long startTick, int spinOffset) {
			this.renderState = renderState;
			this.points = points;
			this.times = times;
			this.startTick = startTick;
			this.spinOffset = spinOffset & 0xFF;
		}

		/** Position {@code elapsed} ticks after leaving the outlet, interpolated between block centres; null when done. */
		Vec3 positionAt(double elapsed) {
			if (elapsed < 0) {
				return this.points[0];
			}
			for (int i = 1; i < this.points.length; i++) {
				if (elapsed <= this.times[i]) {
					double span = this.times[i] - this.times[i - 1];
					double t = span <= 0 ? 1 : (elapsed - this.times[i - 1]) / span;
					return this.points[i - 1].lerp(this.points[i], t);
				}
			}
			return null;
		}
	}
}
