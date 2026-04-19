package io.github.qihua233.ae2_ftbquest_detector.integration.jade;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import io.github.qihua233.ae2_ftbquest_detector.utility.FtbRuntime;
import io.github.qihua233.ae2_ftbquest_detector.utility.TeamDisplayNameResolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DetectorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final DetectorProvider INSTANCE = new DetectorProvider();
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2_ftbquest_detector", "detector");

    private static final long JADE_TASK_STATS_TTL_MS = 1000L;
    private static ServerQuestFile jadeTaskStatsFileRef;
    private static final ConcurrentHashMap<UUID, JadeTaskStats> JADE_TASK_STATS = new ConcurrentHashMap<>();

    private record JadeTaskStats(long expireAtMs, int completed, int total) {
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.getBoolean("NetworkConflict")) {
            tooltip.add(Component.translatable("ae2-ftbquests-detector.detector.network_conflict"));
            return;
        }
        boolean isPowered = accessor.getBlockState().getValue(Objects.requireNonNull(io.github.qihua233.ae2_ftbquest_detector.block.DetectorBlock.POWERED));
        if (!isPowered) {
            tooltip.add(Component.translatable("ae2-ftbquests-detector.detector.uncharged"));
            return;
        }
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
        if (!FtbRuntime.isAvailable()) {
            return;
        }
        if (accessor.getBlockEntity() instanceof DetectorBlockEntity detector) {
            if (detector.isNetworkConflict()) {
                data.putBoolean("NetworkConflict", true);
                return;
            }
            if (detector.ownerTeamId != null) {
                if (Config.jadeShowOwnerInfo) {
                    String teamName = TeamDisplayNameResolver.resolveExistingTeamName(detector.ownerTeamId, detector.ownerTeamNameCache);
                    if (teamName != null) {
                        data.putString("TeamName", teamName);
                    }
                }

                if (Config.jadeShowTaskProgress && ServerQuestFile.INSTANCE != null) {
                    TeamData teamData = ServerQuestFile.INSTANCE.getNullableTeamData(detector.ownerTeamId);
                    if (teamData != null) {
                        JadeTaskStats stats = computeOrGetCachedTaskStats(ServerQuestFile.INSTANCE, teamData, detector.ownerTeamId);
                        data.putInt("CompletedTasks", stats.completed);
                        data.putInt("TotalTasks", stats.total);
                    }
                }
            }
        }
    }

    private static JadeTaskStats computeOrGetCachedTaskStats(ServerQuestFile file, TeamData teamData, UUID teamId) {
        long now = System.currentTimeMillis();
        if (jadeTaskStatsFileRef != file) {
            JADE_TASK_STATS.clear();
            jadeTaskStatsFileRef = file;
        }
        JadeTaskStats cached = JADE_TASK_STATS.get(teamId);
        if (cached != null && now < cached.expireAtMs) {
            return cached;
        }
        List<Task> tasks = file.getAllTasks();
        int total = tasks.size();
        int completed = 0;
        for (Task task : tasks) {
            if (teamData.isCompleted(task)) {
                completed++;
            }
        }
        JadeTaskStats stats = new JadeTaskStats(now + JADE_TASK_STATS_TTL_MS, completed, total);
        JADE_TASK_STATS.put(teamId, stats);
        return stats;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
