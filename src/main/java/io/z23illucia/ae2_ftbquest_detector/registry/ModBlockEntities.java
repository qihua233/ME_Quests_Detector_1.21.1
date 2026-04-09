package io.z23illucia.ae2_ftbquest_detector.registry;

import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static io.z23illucia.ae2_ftbquest_detector.Ae2_ftbquest_detector.MODID;

@SuppressWarnings("null")
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final Supplier<BlockEntityType<DetectorBlockEntity>> DETECTOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ae2_ftbquests_detector", () ->
                    BlockEntityType.Builder.of(DetectorBlockEntity::new, ModBlocks.DETECTOR_BLOCK.get()).build(null)
            );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
