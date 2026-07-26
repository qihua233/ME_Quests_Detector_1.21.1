package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IStackWatcher;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.utility.CoalescingLongQueue;
import io.github.qihua233.ae2_ftbquest_detector.utility.DetectorProgressSyncContext;
import io.github.qihua233.ae2_ftbquest_detector.utility.FtbRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class DetectorDetectionService {
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
        clearDerivedState();
        requestFullScan();
    }

    synchronized void markActiveCacheDirty() {
        activeCacheDirty = true;
        requestFullScan();
    }

    synchronized void requestFullScan() {
        fullScanPending = true;
        stateDirty = true;
    }

    synchronized void onLoaded() {
        markCacheDirty();
    }

    synchronized void onUnloaded() {
        stackWatcher = null;
        cachedTasksByKey.clear();
        activeTasksByKey.clear();
        pendingKeyAmounts.clear();
        progressUpdates.clear();
        cacheDirty = true;
        activeCacheDirty = true;
        stateDirty = true;
        fullScanPending = true;
        reconnectPending = false;
        immediateProgressFlushPending = false;
        nextPartialProgressFlushGameTime = Long.MIN_VALUE;
    }

    private void scanIfNeeded(DetectorBlockEntity detector, long gameTime) {
        if (reconnectPending) {
            reconnectPending = false;
            markCacheDirty();
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
        if (progressUpdates.isEmpty() || !isFlushReady(gameTime)) {
            return;
        }
        DetectionContext context = resolveContext(detector, false);
        if (context == null) {
            stateDirty = true;
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
        if (!detector.getMainNode().isReady() || !detector.getMainNode().isActive() || detector.isNetworkConflict()) {
            return null;
        }
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        UUID ownerTeamId = detector.ownerTeamId;
        if (file == null || ownerTeamId == null || TeamManagerImpl.INSTANCE.getTeamMap().get(ownerTeamId) == null) {
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
