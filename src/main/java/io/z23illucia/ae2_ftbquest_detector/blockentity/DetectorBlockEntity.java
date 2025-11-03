package io.z23illucia.ae2_ftbquest_detector.blockentity;


import appeng.api.networking.*;

import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import dev.architectury.fluid.FluidStack;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.z23illucia.ae2_ftbquest_detector.block.DetectorBlock;
import io.z23illucia.ae2_ftbquest_detector.registry.ModBlockEntities;

import io.z23illucia.ae2_ftbquest_detector.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;


import java.util.*;

import static io.z23illucia.ae2_ftbquest_detector.registry.ModItems.DETECTOR_BLOCK_ITEM;

public class DetectorBlockEntity extends AEBaseBlockEntity implements IInWorldGridNodeHost, IStorageWatcherNode, IGridServiceProvider{

    public IStackWatcher stackWatcher;
    public UUID ownerTeamId;

    private Map<AEKey, List<Task>> cachedTasksByKey = new HashMap<>();
    private long lastCacheUpdate = 0;
    private boolean cacheDirty = true;
    private boolean stateDirty = true;

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerTeamId != null) {
            tag.putUUID("TeamId", ownerTeamId);
        }
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }


    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        if (tag.hasUUID("TeamId")) {
            ownerTeamId = tag.getUUID("TeamId");
        }
    }

    private final IManagedGridNode managedGridNode = GridHelper.createManagedNode(
                    this,
                    DetectorBlockEntityListener.INSTANCE
            ).setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IStorageWatcherNode.class, this)
            .setVisualRepresentation(DETECTOR_BLOCK_ITEM.get())
            ;

    @Override
    public void updateWatcher(IStackWatcher iStackWatcher) {
        stackWatcher = iStackWatcher;
        if(cachedTasksByKey.isEmpty())
            stackWatcher.setWatchAll(true);
        else{
            for(var key : cachedTasksByKey.keySet())
            {
                stackWatcher.add(key);
            }
        }
    }



    @Override
    public void onStackChange(AEKey key, long l) {
        detectTask(key, l);
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
        if (file == null) return;

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
                    }
                }
            }
        }
    }

    int tickCount = 0;

    public void tick() {
        tickCount = (tickCount + 1 ) % 40;
        if(tickCount == 0 && stateDirty)
        {
            performFullDetection();
        }
    }



    private void updateTaskCacheIfNeeded(ServerQuestFile file) {
        if (!cacheDirty) return;

        cachedTasksByKey.clear();
        List<Task> tasksToCheck = file.getSubmitTasks();
        file.markDirty();
        for (Task task : tasksToCheck) {
            if (task instanceof FluidTask fluidTask) {
                AEFluidKey fluidKey = AEFluidKey.of(fluidTask.getFluid());
                cachedTasksByKey.computeIfAbsent(fluidKey, k -> new ArrayList<>()).add(task);
            }
            else if (task instanceof ItemTask itemTask) {
                AEItemKey itemKey = AEItemKey.of(itemTask.getItemStack());
                cachedTasksByKey.computeIfAbsent(itemKey, k -> new ArrayList<>()).add(task);
            }
        }

        cacheDirty = false;
    }

    public void markCacheDirty() {
        this.cacheDirty = true;
    }

    public void markStateDirty() {
        this.stateDirty = true;
    }
    /**
     * 主动扫描整个库存并检测所有相关任务
     * 适用于外部调用的完整检测
     */
    public void performFullDetection() {
        if(!stateDirty) return;
        stateDirty = false;

        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || ownerTeamId == null) return;

        if(TeamManagerImpl.INSTANCE.getTeamMap().get(ownerTeamId) == null) return;

        updateTaskCacheIfNeeded(file);

        TeamData data = file.getNullableTeamData(ownerTeamId);
        if (data == null || data.isLocked()) return;

        KeyCounter keyCounter = Objects.requireNonNull(getGridNode(null)).getGrid().getStorageService().getInventory().getAvailableStacks();
        if(keyCounter != null)
        {
            for(var key:cachedTasksByKey.keySet())
            {
                List<Task> relevantTasks = cachedTasksByKey.get(key);
                for (Task task : relevantTasks) {
                    if (data.canStartTasks(task.getQuest())) {
                        long num = keyCounter.get(key);
                        long c = Math.min(task.getMaxProgress(), num);
                        if (c > data.getProgress(task)) {
                            data.setProgress(task, c);
                        }
                    }
                }

            }
        }
    }

    private boolean nodeInitialized = false;
    @Override
    public void onLoad() {
        if (!nodeInitialized && level instanceof ServerLevel serverLevel) {
            nodeInitialized = true;
            managedGridNode.create(serverLevel, this.getBlockPos());
            //Node.
            DetectorEntityList.register(this);
            //System.out.println(managedGridNode.isActive());
        }

    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        DetectorEntityList.unregister(this);
        managedGridNode.destroy();
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction direction) {
        return managedGridNode.getNode();
    }



}

class DetectorBlockEntityListener implements IGridNodeListener<DetectorBlockEntity> {
    public static final DetectorBlockEntityListener INSTANCE = new DetectorBlockEntityListener();

    @Override
    public void onSaveChanges(DetectorBlockEntity detectorBlockEntity, IGridNode iGridNode) {

    }

    @Override
    public void onStateChanged(DetectorBlockEntity nodeOwner, IGridNode node, State reason) {
        //System.out.println(node.isPowered()+" "+reason);
        nodeOwner.getLevel().setBlock(
                nodeOwner.getBlockPos(),
                nodeOwner.getBlockState().setValue(DetectorBlock.POWERED, node.isPowered()),
                3
        );
        // for example: change block state of nodeOwner to indicate state
        // send node owner to clients
    }

}

