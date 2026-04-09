package io.z23illucia.ae2_ftbquest_detector.blockentity;


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
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.z23illucia.ae2_ftbquest_detector.block.DetectorBlock;
import io.z23illucia.ae2_ftbquest_detector.registry.ModBlockEntities;
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

    private Map<AEKey, List<Task>> cachedTasksByKey = new HashMap<>();
    private boolean cacheDirty = true;
    private boolean stateDirty = true;
    private boolean reconnectPending = false;
    private boolean listed = false;

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerTeamId != null) {
            tag.putUUID("TeamId", ownerTeamId);
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
        }
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, DetectorBlockEntityListener.INSTANCE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
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



    @Override
    public void onStackChange(AEKey key, long l) {
        markStateDirty();
    }


    public void setOwnerTeam(Player player)
    {
        FTBTeamsAPI.api().getManager().getTeamForPlayer((ServerPlayer) player).ifPresent((team) ->
                {
                    ownerTeamId = team.getId();
                    markCacheDirty();
                }
                );

    }


    public DetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DETECTOR_BLOCK_ENTITY.get(), pos, state);
    }

    public void detectTask(AEKey key, long num) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || ownerTeamId == null) return;

        // 更新缓存（如果需要）
        updateTaskCacheIfNeeded(file);
        if(TeamManagerImpl.INSTANCE.getTeamMap().get(ownerTeamId) == null) return;

        TeamData data = file.getNullableTeamData(ownerTeamId);
        if (data == null || data.isLocked()) return;


        // 只检查与当前key相关的任务
        List<Task> relevantTasks = cachedTasksByKey.get(key);
        if (relevantTasks != null) {
            for (Task task : relevantTasks) {
                if (data.canStartTasks(task.getQuest())) {
                    long c = Math.min(task.getMaxProgress(), num);
                    if (c > data.getProgress(task)) {
                        data.setProgress(task, c);
                        markStateDirty();
                    }
                }
            }
        }
    }

    int tickCount = 0;

    public void tick() {
        tickCount = (tickCount + 1 ) % io.z23illucia.ae2_ftbquest_detector.Config.detectorTickRate;
        if(tickCount == 0)
        {
            if (reconnectPending) {
                reconnectPending = false;
                if (getMainNode().isReady()) {
                    getMainNode().setExposedOnSides(EnumSet.noneOf(Direction.class));
                    getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
                }
                markCacheDirty();
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
        file.markDirty();
        
        if (stackWatcher != null) {
            stackWatcher.reset();
        }
        
        for (Task task : tasksToCheck) {
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
    }

    public void markCacheDirty() {
        this.cacheDirty = true;
        this.stateDirty = true;
        if (this.getMainNode().isReady()) {
            try {
                appeng.api.networking.IGrid grid = this.getMainNode().getGrid();
                if (grid != null) {
                    grid.getTickManager().wakeDevice(this.getMainNode().getNode());
                }
            } catch (Exception e) {
            }
        }
    }

    public void markStateDirty() {
        this.stateDirty = true;
    }

    public void requestReconnect() {
        this.reconnectPending = true;
        this.stateDirty = true;
    }
    /**
     * 主动扫描整个库存并检测所有相关任务
     * 适用于外部调用的完整检测
     */
    public void performFullDetection() {
        performFullDetectionInternal();
    }

    private boolean performFullDetectionInternal() {
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

        if(keyCounter != null)
        {
            boolean progressChanged = false;
            for(var key:cachedTasksByKey.keySet())
            {
                List<Task> relevantTasks = cachedTasksByKey.get(key);
                for (Task task : relevantTasks) {
                    if (data.canStartTasks(task.getQuest())) {
                        long num = keyCounter.get(key);
                        long c = Math.min(task.getMaxProgress(), num);
                        if (c > data.getProgress(task)) {
                            data.setProgress(task, c);
                            progressChanged = true;
                        }
                    }
                }
            }
            if (progressChanged) {
                markStateDirty();
            }
        }
        return true;
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        requestReconnect();
        if (!listed) {
            DetectorEntityList.register(this);
            listed = true;
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (listed) {
            DetectorEntityList.unregister(this);
            listed = false;
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (listed) {
            DetectorEntityList.unregister(this);
            listed = false;
        }
        super.setRemoved();
    }



}

class DetectorBlockEntityListener implements IGridNodeListener<DetectorBlockEntity> {
    public static final DetectorBlockEntityListener INSTANCE = new DetectorBlockEntityListener();

    @Override
    public void onSaveChanges(DetectorBlockEntity detectorBlockEntity, IGridNode iGridNode) {

    }

    @Override
    @SuppressWarnings("null")
    public void onStateChanged(DetectorBlockEntity nodeOwner, IGridNode node, State reason) {
        var level = nodeOwner.getLevel();
        if (level != null) {
            level.setBlock(
                    nodeOwner.getBlockPos(),
                    nodeOwner.getBlockState().setValue(DetectorBlock.POWERED, node.isPowered()),
                    3
            );
        }
    }

}
