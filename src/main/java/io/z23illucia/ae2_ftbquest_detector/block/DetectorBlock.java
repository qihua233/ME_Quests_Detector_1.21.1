package io.z23illucia.ae2_ftbquest_detector.block;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.datagen.providers.tags.ConventionTags;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.FTBTeams;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.ObjectUtils;

import javax.annotation.Nullable;
import java.util.Optional;

import static io.z23illucia.ae2_ftbquest_detector.registry.ModItems.DETECTOR_BLOCK_ITEM;


public class DetectorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public DetectorBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5f));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DetectorBlockEntity(pos, state);
    }


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        //System.out.println("use");

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DetectorBlockEntity detector) {
            var node = detector.getGridNode(null);
            if (node == null || !node.isPowered()) {
//                player.displayClientMessage(
//                        Component.translatable("ae2-ftbquests-detector.detector.uncharged"),
//                        true
//                );
                ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("ae2-ftbquests-detector.detector.uncharged")
                ));

            }
            else if(detector.ownerTeamId == null)
            {
                ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("ae2-ftbquests-detector.detector.no_owner")
                ));
            }
            else
            {
                try {
                    Optional<Team> optionalTeam = FTBTeamsAPI
                            .api()
                            .getManager()
                            .getTeamByID(detector.ownerTeamId);
                    optionalTeam.ifPresentOrElse(team -> {
                        ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                                Component.translatable("ae2-ftbquests-detector.detector.owner_is", team.getName().getString())
                        ));

                        for(var entry : node.getGrid().getStorageService().getInventory().getAvailableStacks()){
                            var key = entry.getKey();
                            var num = entry.getLongValue();

                            detector.detectTask(key, num);
                        }

                    }, () -> {
                        ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                                Component.translatable("ae2-ftbquests-detector.detector.invalid_owner")
                        ));

                    });
                }
                catch (Exception e)
                {
                    ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("ae2-ftbquests-detector.detector.invalid_owner"))
                    );
                }
            }

        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DetectorBlockEntity d) {
                d.setOwnerTeam(player);
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }
}

