package io.github.qihua233.ae2_ftbquest_detector.integration.jade;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the client-side Jade progress preference for each connected player. */
public final class DetectorJadeProgressPreferences {
    private static final ConcurrentHashMap<UUID, Boolean> VALUES = new ConcurrentHashMap<>();

    private DetectorJadeProgressPreferences() {
    }

    public static void update(UUID playerId, boolean showTaskProgress) {
        if (playerId != null) {
            VALUES.put(playerId, showTaskProgress);
        }
    }

    public static boolean shouldCompute(UUID playerId) {
        return playerId == null || VALUES.getOrDefault(playerId, true);
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            VALUES.remove(playerId);
        }
    }

    public static void clear() {
        VALUES.clear();
    }
}
