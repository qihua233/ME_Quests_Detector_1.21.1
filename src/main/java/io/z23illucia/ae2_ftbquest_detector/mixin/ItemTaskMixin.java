package io.z23illucia.ae2_ftbquest_detector.mixin;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import dev.ftb.mods.ftbquests.item.MissingItem;
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

//    }
    @Inject(
            method = "submitTask(Ldev/ftb/mods/ftbquests/quest/TeamData;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/server/level/ServerPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
//                    shift = At.Shift.AFTER
//            ),
            at = @At("HEAD"),
            remap = false
    )
    private void injectSubmitItemTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {

        ItemTask self = (ItemTask) (Object) this;
        if(self.consumesResources()
                && !teamData.isCompleted(self)
                && !self.isTaskScreenOnly()
                && !teamData.isCompleted(self)
                && !(self.getItemStack().getItem() instanceof MissingItem)
                && !(craftedItem.getItem() instanceof MissingItem)
        )

        {
            //System.out.println("consume注入点");
            for(var e: DetectorEntityList.getAll())
            {
                //System.out.println(e.getBlockPos());
                if(e.ownerTeamId.equals(teamData.getTeamId()))
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
                            ItemStack stack1 = self.insert(teamData, stack, false);
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
