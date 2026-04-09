package io.z23illucia.ae2_ftbquest_detector.registry;

import io.z23illucia.ae2_ftbquest_detector.Ae2_ftbquest_detector;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Ae2_ftbquest_detector.MODID);

    public static final Supplier<DataComponentType<UUID>> OWNER_TEAM_ID = DATA_COMPONENTS.register("owner_team_id",
            () -> DataComponentType.<UUID>builder()
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                    .build()
    );

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
