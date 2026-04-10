package io.github.qihua233.ae2_ftbquest_detector.block;


import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ItemInteractionResult;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;


@SuppressWarnings("null")
public class DetectorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public DetectorBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5f));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DetectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof DetectorBlockEntity detector) {
                detector.tick();
            }
        };
    }


    @Override
    protected ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                 @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DetectorBlockEntity detector) {
            if(detector.ownerTeamId == null)
            {
                Component message = Component.translatable("ae2-ftbquests-detector.detector.no_owner");
                ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                        message
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
                        Component message = Component.translatable("ae2-ftbquests-detector.detector.owner_is", team.getName().getString());
                        ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                                message
                        ));

                        detector.performFullDetection();

                    }, () -> {
                        Component message = Component.translatable("ae2-ftbquests-detector.detector.invalid_owner");
                        ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                                message
                        ));

                    });
                }
                catch (Exception e)
                {
                    Component message = Component.translatable("ae2-ftbquests-detector.detector.invalid_owner");
                    ((ServerPlayer) player).connection.send(new ClientboundSetActionBarTextPacket(
                            message)
                    );
                }
            }

        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DetectorBlockEntity d) {
                d.setOwner(player);
                java.util.UUID teamId = stack.get(io.github.qihua233.ae2_ftbquest_detector.registry.ModDataComponents.OWNER_TEAM_ID.get());
                if (teamId != null) {
                    d.ownerTeamId = teamId;
                    d.markCacheDirty();
                } else {
                    d.setOwnerTeam(player);
                }
                d.requestReconnect();
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DetectorBlockEntity detector) {
                detector.requestReconnect();
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }
}
