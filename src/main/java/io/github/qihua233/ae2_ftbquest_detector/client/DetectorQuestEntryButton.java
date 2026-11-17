package io.github.qihua233.ae2_ftbquest_detector.client;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.TabButton;
import io.github.qihua233.ae2_ftbquest_detector.network.CheckmarkSweepPayload;
import io.github.qihua233.ae2_ftbquest_detector.quest.CheckmarkSweepService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.ref.WeakReference;

/** Entry point for detector features in the FTB Quests bottom-right sidebar. */
public final class DetectorQuestEntryButton extends TabButton {
    private static WeakReference<ClientPacketListener> cooldownConnection = new WeakReference<>(null);
    private static long nextClickTick;

    public DetectorQuestEntryButton(Panel panel) {
        super(panel, Component.translatable("ae2_ftbquest_detector.quest_entry.title"), Icons.CHECK);
    }

    @Override
    public boolean isEnabled() {
        var client = Minecraft.getInstance();
        return client.level != null && client.getConnection() != null
                && (cooldownConnection.get() != client.getConnection()
                || client.level.getGameTime() >= nextClickTick);
    }

    @Override
    public void onClicked(MouseButton button) {
        if (button.isLeft() && isEnabled()) {
            var client = Minecraft.getInstance();
            cooldownConnection = new WeakReference<>(client.getConnection());
            nextClickTick = client.level.getGameTime() + CheckmarkSweepService.REQUEST_COOLDOWN_TICKS;
            playClickSound();
            PacketDistributor.sendToServer(CheckmarkSweepPayload.INSTANCE);
        }
    }
}
