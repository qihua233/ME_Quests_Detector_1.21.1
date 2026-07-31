package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IStackWatcher;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.utility.CoalescingLongQueue;
import io.github.qihua233.ae2_ftbquest_detector.utility.DetectorProgressSyncContext;
import io.github.qihua233.ae2_ftbquest_detector.utility.FtbRuntime;
import io.github.qihua233.ae2_ftbquest_detector.utility.TeamOwnershipValidator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;

final class DetectorDetectionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PARTIAL_PROGRESS_FLUSH_INTERVAL = Config.detectorTickRate * 3;
    private static final int MAX_PROGRESS_UPDATES_PER_TICK = 64;

    private final Map<AEKey, List<Task>> cachedTasksByKey = new ConcurrentHashMap<>();
    private final Map<AEKey, List<Task>> activeTasksByKey = new ConcurrentHashMap<>();
    private final Map<AEKey, Long> pendingKeyAmounts = new ConcurrentHashMap<>();
    private final CoalescingLongQueue<Task> progressUpdates = new CoalescingLongQueue<>();

    private IStackWatcher stackWatcher;
    private boolean cacheDirty = true;
    private boolean activeCacheDirty = true;
    private boolean stateDirty = true;
    private boolean fullScanPending = true;
    private boolean reconnectPending;
    private boolean immediateProgressFlushPending;
    private long nextPartialProgressFlushGameTime = Long.MIN_VALUE;

    synchronized void updateWatcher(IStackWatcher watcher) {
        stackWatcher = watcher;
        watcher.reset();
        for (AEKey key : cachedTasksByKey.keySet()) {
            watcher.add(key);
        }
    }

    synchronized void onStackChange(AEKey key, long amount) {
        pendingKeyAmounts.put(key, amount);
        stateDirty = true;
    }

    synchronized void tick(DetectorBlockEntity detector, long gameTime, boolean detectionTick) {
        if (!FtbRuntime.isAvailable()) {
            return;
        }
        if (detectionTick) {
            scanIfNeeded(detector, gameTime);
        }
        flushProgressIfReady(detector, gameTime);
    }

    synchronized void requestReconnect() {
        reconnectPending = true;
        stateDirty = true;
    }

    synchronized void markCacheDirty() {
        cacheDirty = true;
        activeCacheDirty = true;
        pendingKeyAmounts.clear();
        requestFullScan();
    }

    synchronized void markActiveCacheDirty() {
        activeCacheDirty = true;
        requestFullScan();
    }

    private void invalidateCachesPreservingProgress() {
        cacheDirty = true;
        activeCacheDirty = true;
        pendingKeyAmounts.clear();
        requestFullScan();
    }

    synchronized void requestFullScan() {
        fullScanPending = true;
        stateDirty = true;
    }

    /** Persists queued progress before the detector changes team or task objects are invalidated. */
    synchronized boolean persistPendingProgress(DetectorBlockEntity detector, UUID teamId) {
        if (progressUpdates.isEmpty()) {
            return true;
        }
        MinecraftServer server = getServer(detector);
        if (server == null || teamId == null) {
            LOGGER.warn("Cannot persist pending detector progress at {} because its server context is unavailable",
                    detector.getBlockPos());
            return false;
        }

        long gameTime = detector.getLevel() instanceof ServerLevel serverLevel
                ? serverLevel.getGameTime()
                : 0L;
        try {
            flushProgress(detector, gameTime, true);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to flush pending detector progress for team {}; retaining it for retry",
                    teamId, exception);
        }
        if (progressUpdates.isEmpty()) {
            return true;
        }

        try {
            progressUpdates.drain(Integer.MAX_VALUE, (task, targetProgress) ->
                    DetectorProgressRecoveryStore.retain(server, teamId, task.getId(), targetProgress));
            immediateProgressFlushPending = false;
            nextPartialProgressFlushGameTime = Long.MIN_VALUE;
            return progressUpdates.isEmpty();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to persist pending detector progress for team {}; retrying later",
                    teamId, exception);
            return false;
        }
    }

    synchronized void onLoaded(DetectorBlockEntity detector) {
        restoreProgressAfterReload(detector);
        resetForReload(!progressUpdates.isEmpty());
    }

    synchronized void onUnloaded(DetectorBlockEntity detector, long gameTime) {
        try {
            flushProgress(detector, gameTime, true);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to flush detector progress before unloading {}", detector.getBlockPos(), exception);
        } finally {
            retainProgressAfterUnload(detector);
            progressUpdates.clear();
        }
        stackWatcher = null;
        cachedTasksByKey.clear();
        activeTasksByKey.clear();
        pendingKeyAmounts.clear();
        cacheDirty = true;
        activeCacheDirty = true;
        stateDirty = true;
        fullScanPending = true;
        reconnectPending = false;
        immediateProgressFlushPending = false;
        nextPartialProgressFlushGameTime = Long.MIN_VALUE;
    }

    private void scanIfNeeded(DetectorBlockEntity detector, long gameTime) {
        restoreProgressAfterReload(detector);
        if (isOwnerTeamDefinitelyUnavailable(detector)) {
            progressUpdates.clear();
            immediateProgressFlushPending = false;
            discardRecoveredProgress(detector);
        }
        if (reconnectPending) {
            reconnectPending = false;
            invalidateCachesPreservingProgress();
        }
        if (detector.isNetworkConflict()) {
            stateDirty = hasPendingScanWork();
            return;
        }
        if (!stateDirty && !hasPendingScanWork()) {
            return;
        }

        boolean processed = scan(detector, gameTime);
        stateDirty = !processed || hasPendingScanWork();
    }

    private boolean scan(DetectorBlockEntity detector, long gameTime) {
        DetectionContext context = resolveContext(detector, true);
        if (context == null) {
            return false;
        }

        updateTaskCacheIfNeeded(context.file);
        updateActiveCacheIfNeeded(context.teamData);
        if (fullScanPending) {
            pendingKeyAmounts.clear();
            processFullScan(context.teamData, context.availableStacks, gameTime);
            fullScanPending = false;
        } else if (!pendingKeyAmounts.isEmpty()) {
            processPendingKeyUpdates(context.teamData, gameTime);
            pendingKeyAmounts.clear();
        }
        return true;
    }

    private void flushProgressIfReady(DetectorBlockEntity detector, long gameTime) {
        flushProgress(detector, gameTime, false);
    }

    private void flushProgress(DetectorBlockEntity detector, long gameTime, boolean force) {
        if (progressUpdates.isEmpty() || !force && !isFlushReady(gameTime)) {
            return;
        }
        DetectionContext context = resolveContext(detector, false);
        if (context == null) {
            if (!force) {
                stateDirty = true;
            }
            return;
        }

        boolean[] completedTask = {false};
        try (DetectorProgressSyncContext.Scope ignored = DetectorProgressSyncContext.suppressTeamDirtyNotifications()) {
            progressUpdates.drain(MAX_PROGRESS_UPDATES_PER_TICK, (task, targetProgress) -> {
                long currentProgress = context.teamData.getProgress(task);
                if (targetProgress <= currentProgress) {
                    return;
                }
                if (currentProgress < task.getMaxProgress() && targetProgress >= task.getMaxProgress()) {
                    completedTask[0] = true;
                }
                context.teamData.setProgress(task, targetProgress);
            });
        } finally {
            if (completedTask[0]) {
                DetectorEntityList.markActiveCacheDirtyForTeam(context.teamData.getTeamId());
            }
        }

        if (progressUpdates.isEmpty()) {
            immediateProgressFlushPending = false;
            nextPartialProgressFlushGameTime = Long.MIN_VALUE;
        }
    }

    private DetectionContext resolveContext(DetectorBlockEntity detector, boolean requireInventory) {
        if (requireInventory && (!detector.getMainNode().isReady()
                || !detector.getMainNode().isActive() || detector.isNetworkConflict())) {
            return null;
        }
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        UUID ownerTeamId = detector.ownerTeamId;
        if (file == null || !TeamOwnershipValidator.isUsableTeam(ownerTeamId)) {
            return null;
        }
        TeamData teamData = file.getNullableTeamData(ownerTeamId);
        if (teamData == null || teamData.isLocked()) {
            return null;
        }
        if (!requireInventory) {
            return new DetectionContext(file, teamData, null);
        }

        IGrid grid = detector.getMainNode().getGrid();
        if (grid == null || grid.getStorageService() == null || grid.getStorageService().getInventory() == null) {
            return null;
        }
        return new DetectionContext(file, teamData, grid.getStorageService().getInventory().getAvailableStacks());
    }

    private void updateTaskCacheIfNeeded(ServerQuestFile file) {
        if (!cacheDirty) {
            return;
        }
        cachedTasksByKey.clear();
        if (stackWatcher != null) {
            stackWatcher.reset();
        }

        for (Task task : file.getSubmitTasks()) {
            if (task.consumesResources()) {
                continue;
            }
            AEKey key = taskKey(task);
            if (key == null) {
                continue;
            }
            cachedTasksByKey.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(task);
            if (stackWatcher != null) {
                stackWatcher.add(key);
            }
        }
        cacheDirty = false;
        activeCacheDirty = true;
    }

    private void updateActiveCacheIfNeeded(TeamData data) {
        if (!activeCacheDirty) {
            return;
        }
        activeTasksByKey.clear();
        for (Map.Entry<AEKey, List<Task>> entry : cachedTasksByKey.entrySet()) {
            List<Task> active = null;
            for (Task task : entry.getValue()) {
                if (data.isCompleted(task) || !data.canStartTasks(task.getQuest())) {
                    continue;
                }
                if (active == null) {
                    active = new ArrayList<>(1);
                }
                active.add(task);
            }
            if (active != null) {
                activeTasksByKey.put(entry.getKey(), List.copyOf(active));
            }
        }
        activeCacheDirty = false;
    }

    private void processFullScan(TeamData data, KeyCounter availableStacks, long gameTime) {
        for (Map.Entry<AEKey, List<Task>> entry : activeTasksByKey.entrySet()) {
            long amount = availableStacks == null ? 0L : availableStacks.get(entry.getKey());
            queueProgressForKey(data, entry.getKey(), amount, gameTime);
        }
    }

    private void processPendingKeyUpdates(TeamData data, long gameTime) {
        for (Map.Entry<AEKey, Long> entry : pendingKeyAmounts.entrySet()) {
            queueProgressForKey(data, entry.getKey(), entry.getValue(), gameTime);
        }
    }

    private void queueProgressForKey(TeamData data, AEKey key, long amount, long gameTime) {
        if (amount <= 0L) {
            return;
        }
        List<Task> tasks = activeTasksByKey.get(key);
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            long persistedProgress = data.getProgress(task);
            long targetProgress = Math.min(task.getMaxProgress(), amount);
            if (targetProgress <= persistedProgress) {
                continue;
            }
            progressUpdates.offerMax(task, targetProgress);
            if (targetProgress >= task.getMaxProgress()) {
                immediateProgressFlushPending = true;
            } else if (nextPartialProgressFlushGameTime == Long.MIN_VALUE) {
                nextPartialProgressFlushGameTime = gameTime + PARTIAL_PROGRESS_FLUSH_INTERVAL;
            }
        }
    }

    private boolean isFlushReady(long gameTime) {
        return immediateProgressFlushPending
                || nextPartialProgressFlushGameTime != Long.MIN_VALUE && gameTime >= nextPartialProgressFlushGameTime;
    }

    private boolean hasPendingScanWork() {
        return fullScanPending || !pendingKeyAmounts.isEmpty();
    }

    private void clearDerivedState() {
        pendingKeyAmounts.clear();
        progressUpdates.clear();
        immediateProgressFlushPending = false;
        nextPartialProgressFlushGameTime = Long.MIN_VALUE;
    }

    private void resetForReload(boolean preserveProgress) {
        cacheDirty = true;
        activeCacheDirty = true;
        pendingKeyAmounts.clear();
        if (preserveProgress) {
            immediateProgressFlushPending = !progressUpdates.isEmpty();
            nextPartialProgressFlushGameTime = Long.MIN_VALUE;
        } else {
            clearDerivedState();
        }
        stateDirty = true;
        fullScanPending = true;
    }

    private boolean isOwnerTeamDefinitelyUnavailable(DetectorBlockEntity detector) {
        TeamOwnershipValidator.Status status = TeamOwnershipValidator.getStatus(detector.ownerTeamId);
        return status == TeamOwnershipValidator.Status.NONE
                || status == TeamOwnershipValidator.Status.EMPTY;
    }

    private void restoreProgressAfterReload(DetectorBlockEntity detector) {
        MinecraftServer server = getServer(detector);
        UUID teamId = detector.ownerTeamId;
        if (server == null || teamId == null) {
            return;
        }

        TeamOwnershipValidator.Status status = TeamOwnershipValidator.getStatus(teamId);
        if (status == TeamOwnershipValidator.Status.NONE
                || status == TeamOwnershipValidator.Status.EMPTY) {
            DetectorProgressRecoveryStore.discard(server, teamId);
            return;
        }
        if (status != TeamOwnershipValidator.Status.USABLE || ServerQuestFile.INSTANCE == null) {
            return;
        }

        for (DetectorProgressRecoveryStore.PendingProgress pending
                : DetectorProgressRecoveryStore.copyFor(server, teamId)) {
            try {
                Task task = ServerQuestFile.INSTANCE.getTask(pending.taskId());
                if (task == null || task.consumesResources() || taskKey(task) == null) {
                    DetectorProgressRecoveryStore.remove(server, teamId, pending.taskId());
                    continue;
                }
                long targetProgress = Math.min(task.getMaxProgress(), pending.targetProgress());
                if (targetProgress <= 0L) {
                    DetectorProgressRecoveryStore.remove(server, teamId, pending.taskId());
                    continue;
                }
                progressUpdates.offerMax(task, targetProgress);
                immediateProgressFlushPending = true;
                DetectorProgressRecoveryStore.remove(server, teamId, pending.taskId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to restore detector progress for task {} and team {}",
                        pending.taskId(), teamId, exception);
            }
        }
    }

    private void retainProgressAfterUnload(DetectorBlockEntity detector) {
        if (progressUpdates.isEmpty()) {
            return;
        }
        MinecraftServer server = getServer(detector);
        UUID teamId = detector.ownerTeamId;
        if (server == null || teamId == null) {
            LOGGER.warn("Discarding pending detector progress at {} because its server context is unavailable",
                    detector.getBlockPos());
            return;
        }
        progressUpdates.drain(Integer.MAX_VALUE, (task, targetProgress) ->
                DetectorProgressRecoveryStore.retain(server, teamId, task.getId(), targetProgress));
    }

    private void discardRecoveredProgress(DetectorBlockEntity detector) {
        MinecraftServer server = getServer(detector);
        if (server != null && detector.ownerTeamId != null) {
            DetectorProgressRecoveryStore.discard(server, detector.ownerTeamId);
        }
    }

    private static MinecraftServer getServer(DetectorBlockEntity detector) {
        return detector.getLevel() instanceof ServerLevel serverLevel ? serverLevel.getServer() : null;
    }

    private static AEKey taskKey(Task task) {
        if (task instanceof FluidTask fluidTask) {
            return AEFluidKey.of(fluidTask.getFluid());
        }
        if (task instanceof ItemTask itemTask) {
            return AEItemKey.of(itemTask.getItemStack());
        }
        return null;
    }

    private record DetectionContext(ServerQuestFile file, TeamData teamData, KeyCounter availableStacks) {
    }
}
