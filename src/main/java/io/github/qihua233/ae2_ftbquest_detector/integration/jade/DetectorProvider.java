package io.github.qihua233.ae2_ftbquest_detector.integration.jade;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Objects;

public class DetectorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final DetectorProvider INSTANCE = new DetectorProvider();
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2_ftbquest_detector", "detector");

    private static boolean isFtbRuntimeAvailable() {
        try {
            Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
            Class.forName("dev.ftb.mods.ftbteams.data.TeamManagerImpl");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        boolean isPowered = accessor.getBlockState().getValue(Objects.requireNonNull(io.github.qihua233.ae2_ftbquest_detector.block.DetectorBlock.POWERED));
        if (!isPowered) {
            tooltip.add(Component.translatable("ae2-ftbquests-detector.detector.uncharged"));
            return;
        }

        CompoundTag data = accessor.getServerData();
        if (Config.jadeShowOwnerInfo) {
            if (data.contains("TeamName")) {
                String teamName = data.getString("TeamName");
                tooltip.add(Component.translatable("ae2-ftbquests-detector.detector.owner_is", teamName));
            } else {
                tooltip.add(Component.translatable("ae2-ftbquests-detector.detector.no_owner"));
            }
        }

        if (Config.jadeShowTaskProgress && data.contains("CompletedTasks")) {
            int completed = data.getInt("CompletedTasks");
            int total = data.getInt("TotalTasks");
            tooltip.add(Component.translatable("ae2-ftbquests-detector.jade.tasks", completed, total));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!isFtbRuntimeAvailable()) {
            return;
        }
        if (accessor.getBlockEntity() instanceof DetectorBlockEntity detector) {
            if (detector.ownerTeamId != null) {
                if (Config.jadeShowOwnerInfo) {
                    var team = TeamManagerImpl.INSTANCE.getTeamMap().get(detector.ownerTeamId);
                    if (team != null) {
                        data.putString("TeamName", Objects.requireNonNull(team.getName().getString()));
                    }
                }

                if (Config.jadeShowTaskProgress && ServerQuestFile.INSTANCE != null) {
                    TeamData teamData = ServerQuestFile.INSTANCE.getNullableTeamData(detector.ownerTeamId);
                    if (teamData != null) {
                        int completed = 0;
                        int total = 0;
                        for (Task task : ServerQuestFile.INSTANCE.getAllTasks()) {
                            total++;
                            if (teamData.isCompleted(task)) {
                                completed++;
                            }
                        }
                        data.putInt("CompletedTasks", completed);
                        data.putInt("TotalTasks", total);
                    }
                }
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
