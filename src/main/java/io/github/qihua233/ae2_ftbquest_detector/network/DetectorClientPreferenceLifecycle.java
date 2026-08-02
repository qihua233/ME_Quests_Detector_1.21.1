package io.github.qihua233.ae2_ftbquest_detector.network;

import io.github.qihua233.ae2_ftbquest_detector.integration.jade.DetectorJadeProgressPreferences;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class DetectorClientPreferenceLifecycle {
    private DetectorClientPreferenceLifecycle() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DetectorClientPreferenceLifecycle::onPlayerLoggedOut);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DetectorJadeProgressPreferences.remove(event.getEntity().getUUID());
    }
}
