package pl.misiek.copperpipes.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * {@code /cpconfig} shows the visual settings; {@code /cpconfig distance <blocks>}, {@code /cpconfig max <items>}
 * and {@code /cpconfig render <true|false>} change them immediately and save the config file.
 */
public final class ConfigCommand {
	private ConfigCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("cpconfig")
						.executes(context -> show(context.getSource()))
						.then(ClientCommands.literal("distance")
								.then(ClientCommands.argument("blocks", IntegerArgumentType.integer(1, ClientConfig.MAX_RENDER_DISTANCE))
										.executes(context -> {
											ClientConfig.get().renderDistance = IntegerArgumentType.getInteger(context, "blocks");
											return saveAndShow(context.getSource());
										})))
						.then(ClientCommands.literal("max")
								.then(ClientCommands.argument("items", IntegerArgumentType.integer(0, ClientConfig.MAX_VISIBLE_ITEMS))
										.executes(context -> {
											ClientConfig.get().maxVisibleItems = IntegerArgumentType.getInteger(context, "items");
											return saveAndShow(context.getSource());
										})))
						.then(ClientCommands.literal("render")
								.then(ClientCommands.argument("enabled", BoolArgumentType.bool())
										.executes(context -> {
											ClientConfig.get().renderItems = BoolArgumentType.getBool(context, "enabled");
											return saveAndShow(context.getSource());
										})))));
	}

	private static int saveAndShow(FabricClientCommandSource source) {
		ClientConfig.save();
		return show(source);
	}

	private static int show(FabricClientCommandSource source) {
		ClientConfig config = ClientConfig.get();
		source.sendFeedback(Component.translatable("commands.copperpipes.config", config.renderItems, config.renderDistance, config.maxVisibleItems));
		return 1;
	}
}
