package io.github.qihua233.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.Tristate;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import io.github.qihua233.ae2_ftbquest_detector.utility.IFluidTaskExtension;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidTask.class)
@SuppressWarnings("null")
public class FluidTaskMixin implements IFluidTaskExtension {

    @Unique
    public Tristate consumeFluid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(long id, Quest quest, CallbackInfo ci) {
        this.consumeFluid = Tristate.DEFAULT;

    }

    //@Inject(method = "consumesResources", at = @At("HEAD"), remap = false, cancellable = true)
    /**
     * @author mod_author
     * @reason fix
     */
    @Overwrite(remap = false)
    public boolean consumesResources() {
        FluidTask self = (FluidTask)(Object)this;
        return this.consumeFluid.get(self.getQuest().getChapter().consumeItems());
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
    private void writeNBT(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        String consumeFluidName = consumeFluid.name();
        tag.putString("consume_fluid", consumeFluidName);
    }

    @Inject(method = "readData", at = @At("TAIL"), remap = false)
    private void readNBT(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (tag.contains("consume_fluid")) {
            consumeFluid = Tristate.valueOf(tag.getString("consume_fluid"));
        }
    }

    @Inject(method = "writeNetData", at = @At("TAIL"), remap = false)
    private void writeNet(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        Enum<?> value = consumeFluid;
        buffer.writeEnum(value);
    }

    @Inject(method = "readNetData", at = @At("TAIL"), remap = false)
    private void readNet(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        consumeFluid = buf.readEnum(Tristate.class);
    }
}
