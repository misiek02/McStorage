package pl.misiek.copperpipes.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import pl.misiek.copperpipes.client.render.FlowingItems;

/**
 * {@code /cpfps [seconds]}: samples the FPS counter once per client tick for a few seconds (default 10) and prints
 * the average, minimum and maximum, plus how many pipe items were being animated.
 */
public final class FpsCommand {
	private static int ticksLeft;
	private static long sum;
	private static int samples;
	private static int min;
	private static int max;
	private static int maxItems;

	private FpsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("cpfps")
						.executes(context -> start(context.getSource().getClient(), 10))
						.then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, 120))
								.executes(context -> start(context.getSource().getClient(), IntegerArgumentType.getInteger(context, "seconds"))))));
		ClientTickEvents.END_CLIENT_TICK.register(FpsCommand::tick);
	}

	private static int start(Minecraft client, int seconds) {
		ticksLeft = seconds * 20;
		sum = 0;
		samples = 0;
		min = Integer.MAX_VALUE;
		max = 0;
		maxItems = 0;
		client.gui.hud.getChat().addClientSystemMessage(Component.translatable("commands.copperpipes.fps.start", seconds));
		return 1;
	}

	private static void tick(Minecraft client) {
		if (ticksLeft <= 0) {
			return;
		}
		int fps = client.getFps();
		sum += fps;
		samples++;
		min = Math.min(min, fps);
		max = Math.max(max, fps);
		maxItems = Math.max(maxItems, FlowingItems.count());
		if (--ticksLeft == 0) {
			client.gui.hud.getChat().addClientSystemMessage(Component.translatable("commands.copperpipes.fps.result",
					sum / Math.max(1, samples), min, max, maxItems));
		}
	}
}
