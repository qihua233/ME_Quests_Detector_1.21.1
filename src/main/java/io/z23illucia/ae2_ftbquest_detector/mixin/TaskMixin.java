package io.z23illucia.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.z23illucia.ae2_ftbquest_detector.utility.IFluidTaskExtension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Task.class)
public class TaskMixin {

    @Inject(
            method = "submitTask(Ldev/ftb/mods/ftbquests/quest/TeamData;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void injectSubmitFluidTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {
        System.out.println("submit mixin");
        Task thisTask = (Task) (Object) this;
        if(thisTask instanceof FluidTask self)
        {
            System.out.println("submitFluid mixin" + self.submitItemsOnInventoryChange() );

            //System.out.println(System.identityHashCode(thisTask));
            try{
                //FluidStack stack = ((FluidTaskAccessor) self).getCachedFluidStack();
                //long amount = ((FluidTaskMixinAccessor) self).getAmount();
                //System.out.println("amount"+ amount);
            }
            catch (Exception e)
            {
                System.err.println("[Mixin Error] submitFluidTask 注入失败！");
                e.printStackTrace();
            }

        }
    }

    @Inject(
            method = "submitItemsOnInventoryChange",
            at = @At("HEAD"),
            remap = false,
            cancellable = true)
    private void check(CallbackInfoReturnable<Boolean> cir) {
        //System.out.println("check ifsubmit");
        Task thisTask = (Task) (Object) this;
        if(thisTask instanceof FluidTask self)
        {
            //System.out.println("check ifsubmitfluid:" + self.consumesResources());
            cir.setReturnValue(!self.consumesResources());
        }

    }
}

@Mixin(FluidTask.class)
interface FluidTaskMixinAccessor {
    @Accessor(value = "amount", remap = false)
    long getAmount();

//    @Accessor("consumeFluid")
//    boolean getConsumeFluid();
//    @Accessor("amount")
//    void setAmount(long value);


}
