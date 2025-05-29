package io.z23illucia.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.Tristate;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import io.z23illucia.ae2_ftbquest_detector.utility.IFluidTaskExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidTask.class)
public class FluidTaskMixin implements IFluidTaskExtension {

    // 🔍 访问原有字段（私有）
//    @Shadow
//    private long amount;

    // ✨ 添加你自己的字段，必须用 @Unique 避免字段冲突
    @Unique
    public Tristate consumeFluid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(long id, Quest quest, CallbackInfo ci) {
        this.consumeFluid = Tristate.TRUE;
        System.out.println("初始化");
    }

    // 💉 注入构造函数，在构造后标记 isModified
    @Inject(method = "consumesResources", at = @At("HEAD"), remap = false, cancellable = true)
    public void checkConsume(CallbackInfoReturnable<Boolean> cir) {
        //this.isModified = true;
        //System.out.println("[Mixin] FluidTask 构造完成，isModified 设置为 true");

        FluidTask self = (FluidTask)(Object)this;
        boolean result = this.consumeFluid.get(self.getQuest().getChapter().consumeItems());
        cir.setReturnValue(result);
        cir.cancel();
    }

    @Inject(method = "fillConfigGroup", at = @At("TAIL"), remap = false)
    public void fillConfig(ConfigGroup config, CallbackInfo ci) {
        //this.isModified = true;
        config.addEnum("consume_fluid", this.getConsumeFluid(), (v) ->
        {
            //System.out.println("set " + this.consumeFluid.displayName +" to " + v.displayName);
            this.consumeFluid = v;
            //System.out.println(System.identityHashCode(this));
            //this.setConsumeFluid(v);
        }, Tristate.NAME_MAP);
    }

    @Override
    public Tristate getConsumeFluid() {
        return consumeFluid;
    }

    public void setConsumeFluid(Tristate value) {
        consumeFluid = value;
    }

    @Inject(method = "writeData", at = @At("TAIL"), remap = false)
    private void writeNBT(CompoundTag tag, CallbackInfo ci) {
        tag.putString("consume_fluid", consumeFluid.name());
    }

    @Inject(method = "readData", at = @At("TAIL"), remap = false)
    private void readNBT(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("consume_fluid")) {
            consumeFluid = Tristate.valueOf(tag.getString("consume_fluid"));
        }
    }

    @Inject(method = "writeNetData", at = @At("TAIL"), remap = false)
    private void writeNet(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeEnum(consumeFluid);
    }

    @Inject(method = "readNetData", at = @At("TAIL"), remap = false)
    private void readNet(FriendlyByteBuf buf, CallbackInfo ci) {
        consumeFluid = buf.readEnum(Tristate.class);
    }
}
