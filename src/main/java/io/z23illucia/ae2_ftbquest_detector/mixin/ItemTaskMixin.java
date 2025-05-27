package io.z23illucia.ae2_ftbquest_detector.mixin;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import io.z23illucia.ae2_ftbquest_detector.blockentity.TeamBlockBindingHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
        System.out.println("💡 这是 changed = false 后第一次调用前的注入点");
        ItemTask self = (ItemTask) (Object) this;
        if(self.consumesResources())
        {


        }
    }
}
