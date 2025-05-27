package io.z23illucia.ae2_ftbquest_detector.registry;


import appeng.api.networking.GridServices;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.storage.IStorageWatcherNode;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

import static io.z23illucia.ae2_ftbquest_detector.Ae2_ftbquest_detector.MODID;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

    public static final RegistryObject<BlockEntityType<DetectorBlockEntity>> DETECTOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ae2_ftbquests_detector", () ->
                    BlockEntityType.Builder.of(DetectorBlockEntity::new, ModBlocks.DETECTOR_BLOCK.get()).build(null)
            );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);

    }
}

