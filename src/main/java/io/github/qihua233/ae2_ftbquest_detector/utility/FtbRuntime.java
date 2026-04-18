package io.github.qihua233.ae2_ftbquest_detector.utility;

/**
 * Resolves FTB Quests / FTB Teams presence once; avoids repeated {@code Class.forName} on hot paths.
 */
public final class FtbRuntime {
    private static final boolean AVAILABLE = compute();

    private FtbRuntime() {
    }

    private static boolean compute() {
        try {
            Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
            Class.forName("dev.ftb.mods.ftbteams.data.TeamManagerImpl");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }
}
