package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.github.qihua233.ae2_ftbquest_detector.block.DetectorBlock;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModBlockEntities;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModItems;
import io.github.qihua233.ae2_ftbquest_detector.utility.DetectorProgressSyncContext;
import io.github.qihua233.ae2_ftbquest_detector.utility.FtbRuntime;
import io.github.qihua233.ae2_ftbquest_detector.utility.TeamDisplayNameResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public class DetectorBlockEntity extends AENetworkedBlockEntity implements IStorageWatcherNode, IGridServiceProvider {
    private static final int PARTIAL_PROGRESS_FLUSH_INTERVAL = io.github.qihua233.ae2_ftbquest_detector.Config.detectorTickRate * 3;

    public IStackWatcher stackWatcher;
    public UUID ownerTeamId;
    public String ownerTeamNameCache;
    public Set<UUID> shortNameWarnedPlayers = new HashSet<>();

    /** 非消耗提交任务缓存，按任务需要的 AEKey 分组。 */
    private final Map<AEKey, List<Task>> cachedTasksByKey = new HashMap<>();
    /** 当前已解锁且未完成的任务视图。 */
    private final Map<AEKey, List<Task>> activeTasksByKey = new HashMap<>();
    private final Map<AEKey, Long> pendingKeyAmounts = new HashMap<>();
    private final Map<Task, Long> bufferedTaskProgress = new IdentityHashMap<>();
    private boolean cacheDirty = true;
    private boolean activeCacheDirty = true;
    private boolean stateDirty = true;
    private boolean fullScanPending = true;
    private boolean reconnectPending = false;
    private boolean immediateProgressFlushPending = false;
    private long nextPartialProgressFlushGameTime = Long.MIN_VALUE;

    /** 同一 ME 网络存在多个检测器时停止工作。 */
    private boolean networkConflict = false;
    private int tickCount = 0;

    public DetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DETECTOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerTeamId != null) {
            tag.putUUID("TeamId", ownerTeamId);
        }
        if (ownerTeamNameCache != null && !ownerTeamNameCache.isBlank()) {
            tag.putString("TeamNameCache", ownerTeamNameCache);
        }
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.hasUUID("TeamId")) {
            ownerTeamId = tag.getUUID("TeamId");
        } else {
            ownerTeamId = null;
        }
        if (tag.contains("TeamNameCache")) {
            String value = tag.getString("TeamNameCache").trim();
            ownerTeamNameCache = value.isEmpty() ? null : value;
        } else {
            ownerTeamNameCache = null;
        }
        shortNameWarnedPlayers.clear();
        clearDerivedDetectionState();
        fullScanPending = true;
        stateDirty = true;
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("detector")
                .setInWorldNode(true)
                .setVisualRepresentation(ModItems.DETECTOR_BLOCK_ITEM.get())
                .setIdlePowerUsage(4.0)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .addService(IStorageWatcherNode.class, this);
    }

    @Override
    public void updateWatcher(IStackWatcher iStackWatcher) {
        stackWatcher = iStackWatcher;
        stackWatcher.reset();
        if (!cachedTasksByKey.isEmpty()) {
            for (AEKey key : cachedTasksByKey.keySet()) {
                stackWatcher.add(key);
            }
        }
    }

    /** AE2 正在遍历 watcher 时这里只缓存最新数量，避免回调里改 watcher。 */
    @Override
    public void onStackChange(AEKey key, long amount) {
        pendingKeyAmounts.put(key, amount);
        markStateDirty();
    }

    public void setOwnerTeam(Player player) {
        UUID previous = this.ownerTeamId;
        try {
            TeamManagerImpl.INSTANCE.getTeamForPlayer((ServerPlayer) player).ifPresent(team -> {
                ownerTeamId = team.getId();
                ownerTeamNameCache = TeamDisplayNameResolver.resolveRawTeamName(ownerTeamId, null);
                shortNameWarnedPlayers.clear();
                markCacheDirty();
            });
        } catch (Throwable ignored) {
        }
        if (!Objects.equals(previous, this.ownerTeamId)) {
            DetectorEntityList.notifyOwnerTeamIdChanged(this, previous);
        }
    }

    public boolean isNetworkConflict() {
        return networkConflict;
    }

    public void tick() {
        if (this.isRemoved() || this.level == null) {
            return;
        }
        if (!FtbRuntime.isAvailable()) {
            return;
        }

        tickCount = (tickCount + 1) % io.github.qihua233.ae2_ftbquest_detector.Config.detectorTickRate;
        if (tickCount != 0) {
            return;
        }

        if (reconnectPending) {
            reconnectPending = false;
            markCacheDirty();
        }
        if (networkConflict) {
            stateDirty = hasPendingWork();
            return;
        }
        if (!stateDirty && !hasPendingWork()) {
            return;
        }

        boolean processed = processDetectionWork(false);
        stateDirty = !processed || hasPendingWork();
    }

    private boolean hasPendingWork() {
        return fullScanPending || !pendingKeyAmounts.isEmpty() || !bufferedTaskProgress.isEmpty();
    }

    private void clearDerivedDetectionState() {
        pendingKeyAmounts.clear();
        bufferedTaskProgress.clear();
        immediateProgressFlushPending = false;
        nextPartialProgressFlushGameTime = Long.MIN_VALUE;
    }

    private void requestFullScan() {
        fullScanPending = true;
        markStateDirty();
    }

    private void updateTaskCacheIfNeeded(ServerQuestFile file) {
        if (!cacheDirty) {
            return;
        }

        cachedTasksByKey.clear();
        List<Task> tasksToCheck = file.getSubmitTasks();

        if (stackWatcher != null) {
            stackWatcher.reset();
        }

        for (Task task : tasksToCheck) {
            if (task.consumesResources()) {
                continue;
            }
            if (task instanceof FluidTask fluidTask) {
                AEFluidKey fluidKey = AEFluidKey.of(fluidTask.getFluid());
                cachedTasksByKey.computeIfAbsent(fluidKey, k -> new ArrayList<>()).add(task);
                if (stackWatcher != null) {
                    stackWatcher.add(fluidKey);
                }
            } else if (task instanceof ItemTask itemTask) {
                AEItemKey itemKey = AEItemKey.of(itemTask.getItemStack());
                cachedTasksByKey.computeIfAbsent(itemKey, k -> new ArrayList<>()).add(task);
                if (stackWatcher != null) {
                    stackWatcher.add(itemKey);
                }
            }
        }

        cacheDirty = false;
        activeCacheDirty = true;
    }

    /** 重建当前可执行任务缓存。 */
    private void updateActiveCacheIfNeeded(TeamData data) {
        if (!activeCacheDirty) {
            return;
        }

        activeTasksByKey.clear();
        for (Map.Entry<AEKey, List<Task>> entry : cachedTasksByKey.entrySet()) {
            List<Task> all = entry.getValue();
            List<Task> active = null;
            for (int i = 0; i < all.size(); i++) {
                Task task = all.get(i);
                if (data.isCompleted(task)) {
                    continue;
                }
                if (!data.canStartTasks(task.getQuest())) {
                    continue;
                }
                if (active == null) {
                    active = new ArrayList<>(1);
                }
                active.add(task);
            }
            if (active != null) {
                activeTasksByKey.put(entry.getKey(), active);
            }
        }

        activeCacheDirty = false;
    }

    public void markCacheDirty() {
        cacheDirty = true;
        activeCacheDirty = true;
        clearDerivedDetectionState();
        requestFullScan();
    }

    /** 仅使可执行任务视图失效。 */
    public void markActiveCacheDirty() {
        activeCacheDirty = true;
        requestFullScan();
    }

    public void markStateDirty() {
        stateDirty = true;
    }

    public void requestReconnect() {
        reconnectPending = true;
        markStateDirty();
    }

    public void performFullDetection() {
        requestFullScan();
        boolean processed = processDetectionWork(true);
        stateDirty = !processed || hasPendingWork();
    }

    private boolean processDetectionWork(boolean forceFlush) {
        if (!FtbRuntime.isAvailable()) {
            return false;
        }
        if (!getMainNode().isReady() || !getMainNode().isActive()) {
            return false;
        }
        if (networkConflict) {
            return false;
        }

        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || ownerTeamId == null) {
            return false;
        }

        if (TeamManagerImpl.INSTANCE.getTeamMap().get(ownerTeamId) == null) {
            return false;
        }

        updateTaskCacheIfNeeded(file);

        TeamData data = file.getNullableTeamData(ownerTeamId);
        if (data == null || data.isLocked()) {
            return false;
        }

        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        var storageService = grid.getStorageService();
        if (storageService == null) {
            return false;
        }

        var inventory = storageService.getInventory();
        if (inventory == null) {
            return false;
        }

        KeyCounter keyCounter = inventory.getAvailableStacks();

        updateActiveCacheIfNeeded(data);
        if (fullScanPending) {
            processFullScan(data, keyCounter);
            fullScanPending = false;
            pendingKeyAmounts.clear();
        } else if (!pendingKeyAmounts.isEmpty()) {
            processPendingKeyUpdates(data);
            pendingKeyAmounts.clear();
        }

        if (shouldFlushBufferedProgress(forceFlush)) {
            flushBufferedTaskProgress(data);
        }
        return true;
    }

    private void processFullScan(TeamData data, KeyCounter keyCounter) {
        if (activeTasksByKey.isEmpty()) {
            return;
        }

        for (Map.Entry<AEKey, List<Task>> entry : activeTasksByKey.entrySet()) {
            long amount = keyCounter == null ? 0L : keyCounter.get(entry.getKey());
            queueProgressForKey(data, entry.getKey(), amount);
        }
    }

    private void processPendingKeyUpdates(TeamData data) {
        for (Map.Entry<AEKey, Long> entry : pendingKeyAmounts.entrySet()) {
            queueProgressForKey(data, entry.getKey(), entry.getValue());
        }
    }

    private void queueProgressForKey(TeamData data, AEKey key, long amount) {
        if (amount <= 0L) {
            return;
        }

        List<Task> relevantTasks = activeTasksByKey.get(key);
        if (relevantTasks == null) {
            return;
        }

        for (int i = 0; i < relevantTasks.size(); i++) {
            Task task = relevantTasks.get(i);
            long target = Math.min(task.getMaxProgress(), amount);
            queueTaskProgress(data, task, target);
        }
    }

    private void queueTaskProgress(TeamData data, Task task, long targetProgress) {
        long persistedProgress = data.getProgress(task);
        long bufferedProgress = bufferedTaskProgress.getOrDefault(task, 0L);
        long knownProgress = Math.max(persistedProgress, bufferedProgress);
        if (targetProgress <= knownProgress) {
            return;
        }

        bufferedTaskProgress.put(task, targetProgress);
        if (persistedProgress < task.getMaxProgress() && targetProgress >= task.getMaxProgress()) {
            immediateProgressFlushPending = true;
            return;
        }

        if (nextPartialProgressFlushGameTime == Long.MIN_VALUE && level != null) {
            nextPartialProgressFlushGameTime = level.getGameTime() + PARTIAL_PROGRESS_FLUSH_INTERVAL;
        }
    }

    private boolean shouldFlushBufferedProgress(boolean forceFlush) {
        if (bufferedTaskProgress.isEmpty()) {
            return false;
        }
        if (forceFlush || immediateProgressFlushPending) {
            return true;
        }
        return level != null
                && nextPartialProgressFlushGameTime != Long.MIN_VALUE
                && level.getGameTime() >= nextPartialProgressFlushGameTime;
    }

    private void flushBufferedTaskProgress(TeamData data) {
        if (bufferedTaskProgress.isEmpty()) {
            immediateProgressFlushPending = false;
            nextPartialProgressFlushGameTime = Long.MIN_VALUE;
            return;
        }

        boolean completedTask = false;
        try (DetectorProgressSyncContext.Scope ignored = DetectorProgressSyncContext.suppressTeamDirtyNotifications()) {
            for (Map.Entry<Task, Long> entry : bufferedTaskProgress.entrySet()) {
                Task task = entry.getKey();
                long targetProgress = entry.getValue();
                long currentProgress = data.getProgress(task);
                if (targetProgress <= currentProgress) {
                    continue;
                }
                if (currentProgress < task.getMaxProgress() && targetProgress >= task.getMaxProgress()) {
                    completedTask = true;
                }
                data.setProgress(task, targetProgress);
            }
        }

        bufferedTaskProgress.clear();
        immediateProgressFlushPending = false;
        nextPartialProgressFlushGameTime = Long.MIN_VALUE;

        if (completedTask) {
            DetectorEntityList.markActiveCacheDirtyForTeam(data.getTeamId());
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        requestReconnect();
        DetectorEntityList.register(this);
    }

    @Override
    public void onChunkUnloaded() {
        notifyGridPeersOnLeave();
        DetectorEntityList.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        notifyGridPeersOnLeave();
        DetectorEntityList.unregister(this);
        super.setRemoved();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (level == null) {
            return;
        }
        refreshConflictAndBlockState();
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            List<DetectorBlockEntity> peers = DetectorEntityList.findInGrid(grid);
            for (int i = 0; i < peers.size(); i++) {
                DetectorBlockEntity peer = peers.get(i);
                if (peer != this) {
                    peer.refreshConflictAndBlockState();
                }
            }
        }
    }

    /** 刷新网络冲突状态，并同步更新 POWERED 方块状态。 */
    public void refreshConflictAndBlockState() {
        if (level == null || isRemoved()) {
            return;
        }
        boolean nodeActive = getMainNode().isActive();
        boolean conflict = false;
        if (nodeActive) {
            IGrid grid = getMainNode().getGrid();
            if (grid != null) {
                List<DetectorBlockEntity> peers = DetectorEntityList.findInGrid(grid);
                conflict = peers.size() > 1;
            }
        }
        boolean conflictChanged = conflict != this.networkConflict;
        this.networkConflict = conflict;

        boolean shouldPower = nodeActive && !conflict;
        BlockState current = getBlockState();
        if (current.hasProperty(DetectorBlock.POWERED)
                && current.getValue(DetectorBlock.POWERED) != shouldPower) {
            level.setBlock(getBlockPos(), current.setValue(DetectorBlock.POWERED, shouldPower), 3);
        }

        if (shouldPower) {
            requestFullScan();
        }
        if (conflictChanged) {
            setChanged();
        }
    }

    private void notifyGridPeersOnLeave() {
        try {
            IGrid grid = getMainNode().getGrid();
            if (grid == null) {
                return;
            }
            List<DetectorBlockEntity> peers = DetectorEntityList.findInGrid(grid);
            for (int i = 0; i < peers.size(); i++) {
                DetectorBlockEntity peer = peers.get(i);
                if (peer != this) {
                    peer.refreshConflictAndBlockState();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
