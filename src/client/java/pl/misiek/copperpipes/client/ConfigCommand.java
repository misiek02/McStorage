package pl.misiek.copperpipes.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import pl.misiek.copperpipes.client.screen.CopperPipesConfigScreen;

/**
 * {@code /cpconfig} opens the settings screen (also reachable from Options); {@code /cpconfig distance <blocks>}, {@code /cpconfig max <items>}
 * and {@code /cpconfig render <true|false>} change them immediately and save the config file.
 */
public final class ConfigCommand {
	private static boolean openScreenNextTick;

	private ConfigCommand() {
	}

	public static void register() {
		addOptionsButton();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openScreenNextTick) {
				openScreenNextTick = false;
				client.gui.setScreen(new CopperPipesConfigScreen(null));
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("cpconfig")
						.executes(context -> {
							// The chat screen closes right after the command runs, so open the settings on the next tick.
							openScreenNextTick = true;
							return 1;
						})
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

	/** Adds a "Copper Pipes..." button to the corner of the vanilla Options screen. */
	private static void addOptionsButton() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof OptionsScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("gui.copperpipes.config.button"),
						button -> client.gui.setScreen(new CopperPipesConfigScreen(screen))).bounds(width - 126, 6, 120, 20).build());
			}
		});
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
