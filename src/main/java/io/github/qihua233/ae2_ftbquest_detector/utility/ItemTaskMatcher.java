package io.github.qihua233.ae2_ftbquest_detector.utility;

import appeng.api.stacks.AEItemKey;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import io.github.qihua233.ae2_ftbquest_detector.mixin.ItemTaskAccessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemTaskMatcher {
    private ItemTaskMatcher() {
    }

    public static boolean usesExactKey(ItemTask task) {
        ItemStack target = task.getItemStack();
        return !target.isEmpty()
                && !isFilter(target)
                && getMatchComponents(task) == ItemMatchingSystem.ComponentMatchType.STRICT;
    }

    public static boolean isFilter(ItemTask task) {
        return isFilter(task.getItemStack());
    }

    public static Item itemType(ItemTask task) {
        return task.getItemStack().getItem();
    }

    public static boolean matches(ItemTask task, AEItemKey candidate) {
        return candidate != null && task.test(candidate.getReadOnlyStack());
    }

    private static boolean isFilter(ItemStack stack) {
        return !stack.isEmpty() && ItemMatchingSystem.INSTANCE.isItemFilter(stack);
    }

    private static ItemMatchingSystem.ComponentMatchType getMatchComponents(ItemTask task) {
        ItemMatchingSystem.ComponentMatchType matchType =
                ((ItemTaskAccessor) (Object) task).ae2FtbQuestDetector$getMatchComponents();
        return matchType == null ? ItemMatchingSystem.ComponentMatchType.NONE : matchType;
    }
}
