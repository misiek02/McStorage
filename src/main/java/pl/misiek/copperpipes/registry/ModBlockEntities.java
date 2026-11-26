package pl.misiek.copperpipes.registry;

import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import pl.misiek.copperpipes.CopperPipes;
import pl.misiek.copperpipes.block.entity.OutletBlockEntity;
import pl.misiek.copperpipes.block.entity.InletBlockEntity;

public final class ModBlockEntities {
	public static final BlockEntityType<OutletBlockEntity> OUTLET = register("outlet_connector", OutletBlockEntity::new, ModBlocks.OUTLET_CONNECTOR);
	public static final BlockEntityType<InletBlockEntity> INLET = register("inlet_connector", InletBlockEntity::new, ModBlocks.INLET_CONNECTOR);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CopperPipes.id(name), new BlockEntityType<>(factory, Set.of(block)));
	}

	public static void init() {
		// Triggers static initialisation.
	}
}
