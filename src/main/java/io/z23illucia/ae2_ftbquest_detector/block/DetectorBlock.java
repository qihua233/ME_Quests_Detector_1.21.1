package io.z23illucia.ae2_ftbquest_detector.block;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

public class DetectorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public DetectorBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5f).requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
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
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DetectorBlockEntity detector) {
            var node = detector.getGridNode(null);
            if (node == null || !node.isPowered()) {
                serverPlayer.sendSystemMessage(Component.literal("[Detector] 未连接到 AE 网络"));
                return InteractionResult.SUCCESS;
            }

            var grid = node.getGrid();
            var s = grid.getStorageService();
            var items = s.getInventory();

            for(var entry : items.getAvailableStacks()){
                var key = entry.getKey();
                if(key instanceof AEItemKey itemKey){
                    ItemStack stack = itemKey.toStack();
                    long count = entry.getLongValue();

                    serverPlayer.sendSystemMessage(Component.literal(
                            "- " + count + "x " + stack.getDisplayName().getString()
                    ));
                }
                else if(key instanceof AEFluidKey fluidKey){

                    long amount = entry.getLongValue();
                    var fluidStack = fluidKey.toStack((int)amount).getDisplayName();// 转换为 FluidStack

                    serverPlayer.sendSystemMessage(Component.literal("- " + amount + " mb x ").append(fluidStack));
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

