package io.github.qihua233.ae2_ftbquest_detector.utility;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorEntityList;
import net.minecraft.server.level.ServerPlayer;

public class SubmitHelper {
    public static void submitTask(TeamData teamData, ServerPlayer player, Task task)
    {
        if(!teamData.getFile().isServerSide()) return;

        for(var e: DetectorEntityList.getAll())
        {
            if(e.ownerTeamId != null && e.ownerTeamId.equals(teamData.getTeamId()))
            {
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

                var amount = task.getMaxProgress() - teamData.getProgress(task);
                AEKey key = null;
                if(task instanceof ItemTask itemTask)
                {
                    key = AEItemKey.of(itemTask.getItemStack());
                }
                else if(task instanceof FluidTask fluidTask){
                    key = AEFluidKey.of(fluidTask.getFluid());
                }

                if(key != null)
                {
                    long extractable = inventory.extract(
                            key,
                            amount,
                            Actionable.SIMULATE,
                            IActionSource.ofPlayer(player)
                    );
                    if(extractable > 0)
                    {
                        inventory.extract(
                                key,
                                extractable,
                                Actionable.MODULATE,
                                IActionSource.ofPlayer(player)
                        );
                        teamData.addProgress(task, extractable);
                    }
                }
            }

        }
    }
}
