package io.github.qihua233.ae2_ftbquest_detector.registry;

import io.github.qihua233.ae2_ftbquest_detector.block.DetectorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector.MODID;

@SuppressWarnings("null")
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);

    public static final Supplier<Block> DETECTOR_BLOCK = BLOCKS.register("me_quests_detector", DetectorBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
