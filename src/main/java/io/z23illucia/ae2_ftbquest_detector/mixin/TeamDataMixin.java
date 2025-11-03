package io.z23illucia.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftbquests.quest.TeamData;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorEntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TeamData.class, remap = false)
public class TeamDataMixin {
    @Unique
    TeamData self = (TeamData)(Object)this;

    @Inject(
            method = "markDirty",
            at = @At("TAIL")
    )
    public void markDirtyMixin(CallbackInfo ci)
    {
        DetectorEntityList.getAll().stream()
                .filter(d -> d.ownerTeamId.equals(self.getTeamId()))
                .findFirst()
                .ifPresent(DetectorBlockEntity::markStateDirty);
    }
}
