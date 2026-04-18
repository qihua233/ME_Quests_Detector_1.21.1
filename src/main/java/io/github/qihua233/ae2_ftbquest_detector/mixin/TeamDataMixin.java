package io.github.qihua233.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftbquests.quest.TeamData;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorEntityList;
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
    public void markDirtyMixin(CallbackInfo ci) {
        var list = DetectorEntityList.copyForTeam(self.getTeamId());
        if (!list.isEmpty()) {
            list.getFirst().markStateDirty();
        }
    }
}
