package io.github.qihua233.ae2_ftbquest_detector.mixin;

import dev.ftb.mods.ftbquests.quest.TeamData;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorEntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = TeamData.class, remap = false)
public class TeamDataMixin {

    @Inject(
            method = "markDirty",
            at = @At("TAIL")
    )
    public void markDirtyMixin(CallbackInfo ci) {
        TeamData self = (TeamData) (Object) this;
        UUID teamId = self.getTeamId();
        if (teamId == null) {
            return;
        }
        List<DetectorBlockEntity> list = DetectorEntityList.copyForTeam(teamId);
        int size = list.size();
        if (size == 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            DetectorBlockEntity be = list.get(i);
            if (be != null && !be.isRemoved()) {
                be.markActiveCacheDirty();
            }
        }
    }
}
