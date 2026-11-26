package pl.misiek.copperpipes.network;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import pl.misiek.copperpipes.block.entity.InletBlockEntity;
import pl.misiek.copperpipes.block.entity.OutletBlockEntity;

/** Holds one {@link PipeNetworkManager} per server level and wires it to Fabric events. */
public final class PipeNetworks {
	private static final Map<ServerLevel, PipeNetworkManager> MANAGERS = new IdentityHashMap<>();

	private PipeNetworks() {
	}

	public static PipeNetworkManager get(ServerLevel level) {
		return MANAGERS.computeIfAbsent(level, PipeNetworkManager::new);
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			PipeNetworkManager manager = MANAGERS.get(level);
			if (manager != null) {
				manager.tick();
			}
		});

		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof InletBlockEntity || blockEntity instanceof OutletBlockEntity) {
				get(level).registerConnector(blockEntity.getBlockPos(), blockEntity.getBlockState());
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof InletBlockEntity || blockEntity instanceof OutletBlockEntity) {
				get(level).unregisterConnector(blockEntity.getBlockPos());
			}
		});

		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> chunkChanged(level, chunk.getPos().pack()));
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> chunkChanged(level, chunk.getPos().pack()));

		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> MANAGERS.values().forEach(PipeNetworkManager::discardAll));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> MANAGERS.values().forEach(PipeNetworkManager::flush));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MANAGERS.clear());
	}

	private static void chunkChanged(ServerLevel level, long chunk) {
		PipeNetworkManager manager = MANAGERS.get(level);
		if (manager != null) {
			manager.onChunkChanged(chunk);
		}
	}

	/** A pipe or connector left {@code pos}; oxidizing, waxing or scraping replaces it with another node and drops nothing. */
	public static void nodeRemovedOrReplaced(ServerLevel level, BlockPos pos) {
		if (Network.isNode(level.getBlockState(pos))) {
			get(level).onNodeChanged(pos);
		} else {
			get(level).onNodeRemoved(pos);
		}
	}

	/** Hook for the BlockEntity.setChanged mixin; cheap when nothing watches that position. */
	public static void onBlockEntityChanged(ServerLevel level, BlockPos pos) {
		PipeNetworkManager manager = MANAGERS.get(level);
		if (manager != null) {
			manager.onContainerChanged(pos);
		}
	}
}
