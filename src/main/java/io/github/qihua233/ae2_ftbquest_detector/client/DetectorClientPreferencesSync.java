package io.github.qihua233.ae2_ftbquest_detector.client;

import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.network.DetectorClientPreferencesPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DetectorClientPreferencesSync {
    private DetectorClientPreferencesSync() {
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn event) -> sendCurrent());
    }

    public static void sendCurrent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(
                    new DetectorClientPreferencesPayload(Config.jadeShowTaskProgress));
        }
    }
}
