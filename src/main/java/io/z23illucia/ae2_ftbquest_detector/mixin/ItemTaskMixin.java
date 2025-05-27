package io.z23illucia.ae2_ftbquest_detector.mixin;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorEntityList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemTask.class)
public class ItemTaskMixin {

//    @ModifyVariable(
//            method = "submitTask(Ldev/ftb/mods/ftbquests/quest/TeamData;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V",
//            at = @At(value = "STORE", ordinal = 0), // 修改第一个 long 存储的地方（c）
//            ordinal = 0,
//            remap = false
//    )
//    private long modifyItemCount(long value) {
//        // 你的自定义逻辑
//        //long aeCount = AEQuestHelper.getItemCountFromNetwork(player, (ItemTask) (Object) this);
//        return 0;
//    }
    @Inject(
            method = "submitTask(Ldev/ftb/mods/ftbquests/quest/TeamData;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void injectAfterChangedAndBeforeLoop(TeamData data, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {

        ItemTask self = (ItemTask) (Object) this;
        if(self.consumesResources() && !data.isCompleted(self))
        {
            //System.out.println("consume注入点");
            for(var e: DetectorEntityList.getAll())
            {
                //System.out.println(e.getBlockPos());
                if(e.ownerTeamId.equals(data.getTeamId()))
                {
                    var Inventory = e.getGridNode(null).getGrid().getStorageService().getInventory();
                    for(var entry : Inventory.getAvailableStacks()) {
                        var key = entry.getKey();

                        if(key instanceof AEItemKey itemKey){
                            ItemStack stack = itemKey.toStack();
                            stack.setCount((int) entry.getLongValue());
                            long extrable = Inventory.extract(key,
                                    stack.getCount(),
                                    Actionable.SIMULATE,
                                    IActionSource.ofPlayer(player)
                            );
                            stack.setCount((int) extrable);
                            //System.out.println("real"+stack.getCount());
                            ItemStack stack1 = self.insert(data, stack, false);
                            //System.out.println("left"+stack1.getCount());
                            if (stack.getCount() != stack1.getCount()) {
                                try {

                                    Inventory.extract(key,
                                            stack.getCount() - stack1.getCount(),
                                            Actionable.MODULATE,
                                            IActionSource.ofPlayer(player)
                                    );
                                    System.out.println("!!!!!!"+stack.getCount()+"  "+stack1.getCount());
                                } catch (Exception err) {
                                    System.err.println("[Mixin Error] submitTask 注入失败！");
                                    err.printStackTrace();
                                }

                            }
                        }
                    }
                }

            }

        }

    }
}
