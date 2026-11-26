package pl.misiek.copperpipes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import pl.misiek.copperpipes.CopperPipes;

/**
 * Client-only visual settings, stored in {@code config/copperpipes-client.json}. Missing or broken files are
 * replaced with defaults. Change it in game with /cpconfig, or edit the file and restart the game.
 */
public final class ClientConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("copperpipes-client.json");
	/** The server only sends items to players within this range of the outlet, so drawing further is pointless. */
	public static final int MAX_RENDER_DISTANCE = 96;
	public static final int MAX_VISIBLE_ITEMS = 10_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Show items flowing through pipes. */
	public boolean renderItems = true;
	/** Items further than this many blocks from the camera are not drawn. */
	public int renderDistance = 32;
	/** At most this many items are drawn per frame. */
	public int maxVisibleItems = 200;

	private static ClientConfig instance = new ClientConfig();

	public static ClientConfig get() {
		return instance;
	}

	public static void load() {
		ClientConfig loaded = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				loaded = GSON.fromJson(reader, ClientConfig.class);
			} catch (IOException | RuntimeException e) {
				CopperPipes.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		instance = loaded == null ? new ClientConfig() : loaded;
		instance.renderDistance = Math.clamp(instance.renderDistance, 1, MAX_RENDER_DISTANCE);
		instance.maxVisibleItems = Math.clamp(instance.maxVisibleItems, 0, MAX_VISIBLE_ITEMS);
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			CopperPipes.LOGGER.warn("Could not write {}", FILE, e);
		}
	}
}
