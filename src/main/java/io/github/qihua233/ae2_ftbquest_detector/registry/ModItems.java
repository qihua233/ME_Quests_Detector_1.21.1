package io.github.qihua233.ae2_ftbquest_detector.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector.MODID;

@SuppressWarnings("null")
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);

    public static final Supplier<Item> DETECTOR_BLOCK_ITEM = ITEMS.register("me_quests_detector",
            () -> new BlockItem(ModBlocks.DETECTOR_BLOCK.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
