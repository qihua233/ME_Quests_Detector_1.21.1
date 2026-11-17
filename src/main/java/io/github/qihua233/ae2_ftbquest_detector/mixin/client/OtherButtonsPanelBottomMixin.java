package io.github.qihua233.ae2_ftbquest_detector.mixin.client;

import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftbquests.client.gui.quests.OtherButtonsPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.OtherButtonsPanelBottom;
import io.github.qihua233.ae2_ftbquest_detector.client.DetectorQuestEntryButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OtherButtonsPanelBottom.class, remap = false)
public abstract class OtherButtonsPanelBottomMixin extends OtherButtonsPanel {
    protected OtherButtonsPanelBottomMixin(Panel panel) {
        super(panel);
    }

    @Inject(method = "addWidgets", at = @At("TAIL"))
    private void ae2_ftbquest_detector$addQuestEntry(CallbackInfo ci) {
        add(new DetectorQuestEntryButton(this));
    }
}
