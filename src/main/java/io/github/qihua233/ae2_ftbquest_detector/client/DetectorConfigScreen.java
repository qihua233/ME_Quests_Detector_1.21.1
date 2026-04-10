package io.github.qihua233.ae2_ftbquest_detector.client;

import io.github.qihua233.ae2_ftbquest_detector.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
public class DetectorConfigScreen extends Screen {
    private final Screen parent;
    private boolean jadeShowOwnerInfo;
    private boolean jadeShowTaskProgress;

    public DetectorConfigScreen(Screen parent) {
        super(Component.translatable("ae2_ftbquest_detector.configuration.title"));
        this.parent = parent;
        this.jadeShowOwnerInfo = Config.JADE_SHOW_OWNER_INFO.get();
        this.jadeShowTaskProgress = Config.JADE_SHOW_TASK_PROGRESS.get();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int y = this.height / 2 - 30;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.jadeShowOwnerInfo)
                .create(left, y, 200, 20,
                        Component.translatable("ae2_ftbquest_detector.configuration.jadeShowOwnerInfo"),
                        (button, value) -> this.jadeShowOwnerInfo = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.jadeShowTaskProgress)
                .create(left, y + 24, 200, 20,
                        Component.translatable("ae2_ftbquest_detector.configuration.jadeShowTaskProgress"),
                        (button, value) -> this.jadeShowTaskProgress = value));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.saveAndClose())
                .bounds(left, y + 58, 98, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(left + 102, y + 58, 98, 20)
                .build());
    }

    private void saveAndClose() {
        Config.JADE_SHOW_OWNER_INFO.set(this.jadeShowOwnerInfo);
        Config.JADE_SHOW_TASK_PROGRESS.set(this.jadeShowTaskProgress);
        Config.jadeShowOwnerInfo = this.jadeShowOwnerInfo;
        Config.jadeShowTaskProgress = this.jadeShowTaskProgress;
        Config.SPEC.save();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 52, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
