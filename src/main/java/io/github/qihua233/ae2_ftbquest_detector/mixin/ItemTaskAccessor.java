package io.github.qihua233.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemTask.class)
public interface ItemTaskAccessor {
    @Accessor(value = "matchComponents", remap = false)
    ItemMatchingSystem.ComponentMatchType ae2FtbQuestDetector$getMatchComponents();
}
