package io.github.qihua233.ae2_ftbquest_detector.utility;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorEntityList;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class SubmitHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SubmitHelper() {
    }

    public static void submitTask(TeamData teamData, ServerPlayer player, Task task) {
        if (!teamData.getFile().isServerSide()) {
            return;
        }
        if (!TeamOwnershipValidator.isUsableTeam(teamData.getTeamId())) {
            return;
        }

        for (var e : DetectorEntityList.copyForTeam(teamData.getTeamId())) {
            try {
                if (e == null || e.isRemoved() || e.isNetworkConflict()
                        || !e.getMainNode().isReady() || !e.getMainNode().isActive()) {
                    continue;
                }
                var grid = e.getMainNode().getGrid();
                if (grid == null) {
                    continue;
                }

                var storageService = grid.getStorageService();
                if (storageService == null) {
                    continue;
                }

                var inventory = storageService.getInventory();
                if (inventory == null) {
                    continue;
                }

                long amount = task.getMaxProgress() - teamData.getProgress(task);
                if (amount <= 0L) {
                    return;
                }
                AEKey key = null;
                if (task instanceof ItemTask itemTask) {
                    key = AEItemKey.of(itemTask.getItemStack());
                } else if (task instanceof FluidTask fluidTask) {
                    key = AEFluidKey.of(fluidTask.getFluid());
                }

                if (key != null) {
                    IActionSource source = IActionSource.ofPlayer(player);
                    AEKey extractionKey = key;
                    long extracted = ResourceExtraction.execute(
                            amount,
                            requested -> inventory.extract(extractionKey, requested, Actionable.SIMULATE, source),
                            requested -> inventory.extract(extractionKey, requested, Actionable.MODULATE, source)
                    );
                    if (extracted > 0L) {
                        teamData.addProgress(task, extracted);
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to submit task {} through detector at {}; another detector may retry it",
                        task.getId(), e.getBlockPos(), exception);
            }
        }
    }
}
