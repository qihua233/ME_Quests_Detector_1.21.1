package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.github.qihua233.ae2_ftbquest_detector.block.DetectorBlock;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModBlockEntities;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModItems;
import io.github.qihua233.ae2_ftbquest_detector.utility.TeamDisplayNameResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public class DetectorBlockEntity extends AENetworkedBlockEntity implements IStorageWatcherNode, IGridServiceProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    public UUID ownerTeamId;
    public String ownerTeamNameCache;
    public final Set<UUID> shortNameWarnedPlayers = ConcurrentHashMap.newKeySet();

    private final DetectorDetectionService detectionService = new DetectorDetectionService();
    private final DetectorStateRefreshQueue stateRefreshQueue = new DetectorStateRefreshQueue();

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
        detectionService.markCacheDirty();
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
        detectionService.updateWatcher(iStackWatcher);
    }

    /** AE2 正在遍历 watcher 时这里只缓存最新数量，避免回调里改 watcher。 */
    @Override
    public void onStackChange(AEKey key, long amount) {
        detectionService.onStackChange(key, amount);
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
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to resolve owner team for detector at {}", getBlockPos(), exception);
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
        try {
            stateRefreshQueue.runPending(this::refreshConflictAndBlockState);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to refresh detector block state at {}; retrying next tick", getBlockPos(), exception);
        }
        tickCount = (tickCount + 1) % io.github.qihua233.ae2_ftbquest_detector.Config.detectorTickRate;
        try {
            detectionService.tick(this, level.getGameTime(), tickCount == 0);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to process detector work at {}; pending work will be retried", getBlockPos(), exception);
        }
    }

    /** 重建当前可执行任务缓存。 */
    public void markCacheDirty() {
        detectionService.markCacheDirty();
    }

    /** 仅使可执行任务视图失效。 */
    public void markActiveCacheDirty() {
        detectionService.markActiveCacheDirty();
    }

    public void requestReconnect() {
        detectionService.requestReconnect();
    }

    @Override
    public void onReady() {
        stateRefreshQueue.activate();
        detectionService.onLoaded();
        super.onReady();
        requestReconnect();
        DetectorEntityList.register(this);
        requestConflictAndBlockStateRefresh();
    }

    @Override
    public void onChunkUnloaded() {
        stateRefreshQueue.deactivate();
        detectionService.onUnloaded();
        shortNameWarnedPlayers.clear();
        notifyGridPeersOnLeave();
        DetectorEntityList.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        stateRefreshQueue.deactivate();
        detectionService.onUnloaded();
        shortNameWarnedPlayers.clear();
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
        requestConflictAndBlockStateRefresh();
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            List<DetectorBlockEntity> peers = DetectorEntityList.findInGrid(grid);
            for (int i = 0; i < peers.size(); i++) {
                DetectorBlockEntity peer = peers.get(i);
                if (peer != this) {
                    peer.requestConflictAndBlockStateRefresh();
                }
            }
        }
    }

    private void requestConflictAndBlockStateRefresh() {
        stateRefreshQueue.request();
    }

    /** 刷新网络冲突状态，并同步更新 POWERED 方块状态。 */
    private void refreshConflictAndBlockState() {
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
            detectionService.requestFullScan();
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
                    peer.requestConflictAndBlockStateRefresh();
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Failed to notify detector grid peers while unloading {}", getBlockPos(), exception);
        }
    }
}
