package io.z23illucia.ae2_ftbquest_detector.blockentity;


import appeng.api.networking.*;

import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import dev.architectury.hooks.level.entity.PlayerHooks;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.util.FTBQuestsInventoryListener;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyType;
import io.z23illucia.ae2_ftbquest_detector.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import dev.ftb.mods.ftbquests.quest.Quest;

import java.util.*;

public class DetectorBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IStorageWatcherNode, IGridServiceProvider{

    public IStackWatcher stackWatcher;
    public UUID ownerTeamId;

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerTeamId != null) {
            tag.putUUID("TeamId", ownerTeamId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("TeamId")) {
            ownerTeamId = tag.getUUID("TeamId");
        }
    }

    private final IManagedGridNode managedGridNode = GridHelper.createManagedNode(
                    this,
                    DetectorBlockEntityListener.INSTANCE
            ).setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IStorageWatcherNode.class, this)
            ;

    @Override
    public void updateWatcher(IStackWatcher iStackWatcher) {
        stackWatcher = iStackWatcher;

        stackWatcher.setWatchAll(true);
        //System.out.println("update");
    }



    @Override
    public void onStackChange(AEKey key, long l) {
        var players = level.getEntitiesOfClass(
                Player.class,
                new AABB(this.getBlockPos()).inflate(100)
        );

        MutableComponent msg = null;
        if(key instanceof AEItemKey itemKey){
            ItemStack stack = itemKey.toStack();
            stack.setCount((int)l);
            msg = Component.literal(
                    "- " + stack.getCount() + "x " + stack.getDisplayName().getString()
            );
            for (Player p : players)
            {
                p.sendSystemMessage(Component.literal("storage change"));
                p.sendSystemMessage(msg);
                detectTask(stack);
            }
        }
        else if(key instanceof AEFluidKey fluidKey){
            var fluidStack = fluidKey.toStack((int)l).getDisplayName();
            msg = Component.literal("- " + l + " mb x ").append(fluidStack);
        }

    }


    public void setOwnerTeam(Player player)
    {
        FTBTeamsAPI.api().getManager().getTeamForPlayer((ServerPlayer) player).ifPresent((team) ->
                {
                    ownerTeamId = team.getId();
                    TeamBlockBindingHelper.addBoundBlock(ownerTeamId, (ServerLevel) level,getBlockPos());
                }
                );

    }


    public DetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DETECTOR_BLOCK_ENTITY.get(), pos, state);
    }

    public void detectTask(ItemStack item) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file != null) {
            List<Task> tasksToCheck =file.getSubmitTasks();
            if (!tasksToCheck.isEmpty()) {
                TeamData data = file.getNullableTeamData(ownerTeamId);
                if (data != null && !data.isLocked()) {
                    for (Task task : tasksToCheck) {
                        if (task instanceof ItemTask && data.canStartTasks(task.getQuest())) {
                            long c = Math.min(task.getMaxProgress(), item.getCount());
                            if (c > data.getProgress(task)) {
                                data.setProgress(task, c);
                            }
                        }

                    }

                }

                };


        }
    }



    private boolean nodeInitialized = false;
    @Override
    public void onLoad() {
        if (!nodeInitialized && level instanceof ServerLevel serverLevel) {
            nodeInitialized = true;

            managedGridNode.create(serverLevel, this.getBlockPos());
            //Node.
            System.out.println(managedGridNode.isActive());
        }

    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        managedGridNode.destroy();
        if (ownerTeamId != null && !level.isClientSide) {
            TeamBlockBindingHelper.removeBoundBlock(ownerTeamId, (ServerLevel) level,getBlockPos());
        }
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

        // for example: change block state of nodeOwner to indicate state
        // send node owner to clients
    }

}

class MyTeamProperties {
    public static final TeamProperty<List<BoundBlock>> BOUND_BLOCKS =
            new TeamProperty<List<BoundBlock>>(
                    new ResourceLocation("aeftbquests", "bound_blocks"),
                    () -> new ArrayList<>()
            ) {
                @Override
                public TeamPropertyType<List<BoundBlock>> getType() {
                    return null;
                }

                @Override
                public Optional<List<BoundBlock>> fromString(String s) {
                    return Optional.empty();
                }

                @Override
                public void write(FriendlyByteBuf friendlyByteBuf) {

                }
            };
}

