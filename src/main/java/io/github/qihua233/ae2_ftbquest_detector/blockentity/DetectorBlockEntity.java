package io.github.qihua233.ae2_ftbquest_detector.blockentity;


import appeng.api.networking.*;
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
import io.github.qihua233.ae2_ftbquest_detector.utility.FtbRuntime;
import io.github.qihua233.ae2_ftbquest_detector.utility.TeamDisplayNameResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;


import java.util.*;

@SuppressWarnings("null")
public class DetectorBlockEntity extends AENetworkedBlockEntity implements IStorageWatcherNode, IGridServiceProvider{

    public IStackWatcher stackWatcher;
    public UUID ownerTeamId;
    public String ownerTeamNameCache;
    public Set<UUID> shortNameWarnedPlayers = new HashSet<>();

    /**
     * All submit tasks that accept this key type, indexed by the key they want. Rebuilt only when
     * the quest file itself changes ({@link #cacheDirty}). Acts as the "candidate set" backing
     * the stack watcher — we still want to watch keys for locked tasks so that later, when the
     * quest unlocks, we already have up-to-date counters.
     */
    private Map<AEKey, List<Task>> cachedTasksByKey = new HashMap<>();
    /**
     * Sub-view of {@link #cachedTasksByKey} filtered to only currently-actionable tasks: not
     * consuming, not completed, quest prerequisites satisfied. Rebuilt cheaply whenever
     * {@link #activeCacheDirty} flips — typically on team progress change via
     * {@code TeamDataMixin.markDirty}. The hot detection loop iterates this map, so the per-task
     * {@code canStartTasks} / {@code isCompleted} cost is paid once per invalidation instead of
     * once per detection tick.
     */
    private Map<AEKey, List<Task>> activeTasksByKey = new HashMap<>();
    private boolean cacheDirty = true;
    private boolean activeCacheDirty = true;
    private boolean stateDirty = true;
    private boolean reconnectPending = false;

    /**
     * True when more than one detector shares this AE2 network. Mirrors the AE2 ME Controller
     * behaviour of refusing to boot a network with multiple controllers: a conflicted detector
     * reports POWERED=false and bails out of every detect/submit path.
     */
    private boolean networkConflict = false;

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
        stackWatcher.setWatchAll(true);
        if(!cachedTasksByKey.isEmpty()) {
            for(var key : cachedTasksByKey.keySet()) {
                stackWatcher.add(key);
            }
        }
    }


    /**
     * Invoked from AE2 while iterating the storage watcher set ({@code StorageService#postWatcherUpdate}).
     * Do not call {@link #detectTask} or {@link #updateTaskCacheIfNeeded} here: they mutate {@link IStackWatcher}
     * and cause {@link java.util.ConcurrentModificationException}. {@link MinecraftServer#execute} can also
     * run the runnable immediately on the server thread, so it does not reliably defer past this iteration.
     */
    @Override
    public void onStackChange(AEKey key, long l) {
        markStateDirty();
    }


    public void setOwnerTeam(Player player) {
        UUID previous = this.ownerTeamId;
        try {
            TeamManagerImpl.INSTANCE.getTeamForPlayer((ServerPlayer) player).ifPresent((team) ->
                    {
                        ownerTeamId = team.getId();
                        ownerTeamNameCache = TeamDisplayNameResolver.resolveRawTeamName(ownerTeamId, null);
                        shortNameWarnedPlayers.clear();
                        markCacheDirty();
                    }
            );
        } catch (Throwable ignored) {
        }
        if (!java.util.Objects.equals(previous, this.ownerTeamId)) {
            DetectorEntityList.notifyOwnerTeamIdChanged(this, previous);
        }
    }


    public DetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DETECTOR_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isNetworkConflict() {
        return networkConflict;
    }

    public void detectTask(AEKey key, long num) {
        if (!FtbRuntime.isAvailable()) return;
        if (!getMainNode().isReady() || !getMainNode().isActive()) return;
        if (networkConflict) return;

        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || ownerTeamId == null) return;

        updateTaskCacheIfNeeded(file);
        if(TeamManagerImpl.INSTANCE.getTeamMap().get(ownerTeamId) == null) return;

        TeamData data = file.getNullableTeamData(ownerTeamId);
        if (data == null || data.isLocked()) return;

        updateActiveCacheIfNeeded(data);

        List<Task> relevantTasks = activeTasksByKey.get(key);
        if (relevantTasks != null) {
            for (int i = 0; i < relevantTasks.size(); i++) {
                Task task = relevantTasks.get(i);
                long c = Math.min(task.getMaxProgress(), num);
                if (c > data.getProgress(task)) {
                    data.setProgress(task, c);
                    markStateDirty();
                }
            }
        }
    }

    int tickCount = 0;

    public void tick() {
        if (this.isRemoved() || this.level == null) return;
        if (!FtbRuntime.isAvailable()) return;
        
        tickCount = (tickCount + 1 ) % io.github.qihua233.ae2_ftbquest_detector.Config.detectorTickRate;
        if(tickCount == 0)
        {
            if (reconnectPending) {
                reconnectPending = false;
                markCacheDirty();
            }
            if (networkConflict) {
                return;
            }
            if (stateDirty) {
                stateDirty = !performFullDetectionInternal();
            }
        }
    }



    private void updateTaskCacheIfNeeded(ServerQuestFile file) {
        if (!cacheDirty) return;

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
                if (stackWatcher != null) stackWatcher.add(fluidKey);
            }
            else if (task instanceof ItemTask itemTask) {
                AEItemKey itemKey = AEItemKey.of(itemTask.getItemStack());
                cachedTasksByKey.computeIfAbsent(itemKey, k -> new ArrayList<>()).add(task);
                if (stackWatcher != null) stackWatcher.add(itemKey);
            }
        }

        cacheDirty = false;
        activeCacheDirty = true;
    }

    /**
     * Rebuilds {@link #activeTasksByKey} from {@link #cachedTasksByKey} keeping only tasks whose
     * quest prerequisites are currently satisfied and that are not yet completed. Caller must
     * have a valid non-null {@link TeamData}. Cheap compared to {@link #updateTaskCacheIfNeeded}:
     * skips the {@code consumesResources} check (already filtered at cache build), but walks
     * FTB Quests' {@code canStartTasks} dependency graph once per task.
     */
    private void updateActiveCacheIfNeeded(TeamData data) {
        if (!activeCacheDirty) return;

        activeTasksByKey.clear();
        for (Map.Entry<AEKey, List<Task>> entry : cachedTasksByKey.entrySet()) {
            List<Task> all = entry.getValue();
            List<Task> active = null;
            for (int i = 0; i < all.size(); i++) {
                Task task = all.get(i);
                if (data.isCompleted(task)) continue;
                if (!data.canStartTasks(task.getQuest())) continue;
                if (active == null) active = new ArrayList<>(1);
                active.add(task);
            }
            if (active != null) {
                activeTasksByKey.put(entry.getKey(), active);
            }
        }

        activeCacheDirty = false;
    }

    public void markCacheDirty() {
        this.cacheDirty = true;
        this.activeCacheDirty = true;
        this.stateDirty = true;
    }

    /**
     * Invalidates the unlock/completion filter without touching the primary task cache. Called
     * from {@code TeamDataMixin} on every {@code TeamData.markDirty}: cheap signal, rebuild is
     * lazy (only happens on the next detection pass).
     */
    public void markActiveCacheDirty() {
        this.activeCacheDirty = true;
        this.stateDirty = true;
    }

    public void markStateDirty() {
        this.stateDirty = true;
    }

    public void requestReconnect() {
        this.reconnectPending = true;
        this.stateDirty = true;
    }

    public void performFullDetection() {
        performFullDetectionInternal();
    }

    private boolean performFullDetectionInternal() {
        if (!FtbRuntime.isAvailable()) return false;
        if (!getMainNode().isReady() || !getMainNode().isActive()) return false;
        if (networkConflict) return false;

        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || ownerTeamId == null) return false;

        if(TeamManagerImpl.INSTANCE.getTeamMap().get(ownerTeamId) == null) return false;

        updateTaskCacheIfNeeded(file);

        TeamData data = file.getNullableTeamData(ownerTeamId);
        if (data == null || data.isLocked()) return false;

        IGrid grid = getMainNode().getGrid();
        if (grid == null) return false;

        var storageService = grid.getStorageService();
        if (storageService == null) return false;

        var inventory = storageService.getInventory();
        if (inventory == null) return false;

        KeyCounter keyCounter = inventory.getAvailableStacks();
        if (keyCounter == null) return true;

        updateActiveCacheIfNeeded(data);
        if (activeTasksByKey.isEmpty()) {
            return true;
        }

        boolean progressChanged = false;
        for (Map.Entry<AEKey, List<Task>> entry : activeTasksByKey.entrySet()) {
            AEKey key = entry.getKey();
            long num = keyCounter.get(key);
            if (num <= 0) continue;
            List<Task> relevantTasks = entry.getValue();
            for (int i = 0; i < relevantTasks.size(); i++) {
                Task task = relevantTasks.get(i);
                long c = Math.min(task.getMaxProgress(), num);
                if (c > data.getProgress(task)) {
                    data.setProgress(task, c);
                    progressChanged = true;
                }
            }
        }
        if (progressChanged) {
            markStateDirty();
        }
        return true;
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
        // When our own state/grid changes, any peers on the same grid may see a different
        // conflict count now, so refresh them too. Uses the grid we're currently on.
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

    /**
     * Recomputes {@link #networkConflict}, updates the {@link DetectorBlock#POWERED} block state
     * only when it actually flips (avoids spurious chunk rebuilds on flicker), and triggers a
     * rescan if we're freshly active and un-conflicted.
     */
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
            markStateDirty();
        }
        if (conflictChanged) {
            setChanged();
        }
    }

    /**
     * When this detector is about to leave its grid (chunk unload / removal), tell the peers
     * that were sharing the grid so a lingering "conflict" can clear immediately on the
     * remaining ones instead of waiting for the next AE2 node-state event.
     */
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
