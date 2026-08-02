package io.github.qihua233.ae2_ftbquest_detector;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CLIENT_JADE_SHOW_OWNER_INFO;
    public static final ModConfigSpec.BooleanValue CLIENT_JADE_SHOW_TASK_PROGRESS;
    public static final ModConfigSpec.EnumValue<TeamNameDisplayMode> CLIENT_TEAM_NAME_DISPLAY_MODE;
    public static final ModConfigSpec.BooleanValue SERVER_JADE_TASK_PROGRESS_IGNORE_HIDDEN_TASKS;
    public static final ModConfigSpec.BooleanValue SERVER_JADE_TASK_PROGRESS_IGNORE_REPEATABLE_TASKS;

    static {
        CLIENT_BUILDER.push("client");

        CLIENT_JADE_SHOW_OWNER_INFO = CLIENT_BUILDER
                .comment("Show owner team information in Jade tooltip")
                .define("jadeShowOwnerInfo", true);

        CLIENT_JADE_SHOW_TASK_PROGRESS = CLIENT_BUILDER
                .comment("Show task completion progress in Jade tooltip")
                .define("jadeShowTaskProgress", true);

        CLIENT_TEAM_NAME_DISPLAY_MODE = CLIENT_BUILDER
                .comment(
                        "How to format the owner team in Jade:",
                        "NAME_AND_SHORT_ID — display name plus short id (e.g. MyTeam#A1B2C3D4)",
                        "NAME_ONLY — resolved team display name only",
                        "SHORT_ID_ONLY — short id derived from team UUID only")
                .defineEnum("teamNameDisplayMode", TeamNameDisplayMode.NAME_AND_SHORT_ID);

        CLIENT_BUILDER.pop();

        SERVER_BUILDER.push("jadeTaskProgress");

        SERVER_JADE_TASK_PROGRESS_IGNORE_HIDDEN_TASKS = SERVER_BUILDER
                .comment("Exclude tasks from hidden FTB Quests quests from Jade completed/total counts")
                .define("ignoreHiddenTasks", true);

        SERVER_JADE_TASK_PROGRESS_IGNORE_REPEATABLE_TASKS = SERVER_BUILDER
                .comment("Exclude tasks from repeatable FTB Quests quests from Jade completed/total counts")
                .define("ignoreRepeatableTasks", true);

        SERVER_BUILDER.pop();
    }

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static final int detectorTickRate = 40;
    public static volatile boolean jadeShowOwnerInfo = true;
    public static volatile boolean jadeShowTaskProgress = true;
    public static volatile TeamNameDisplayMode teamNameDisplayMode = TeamNameDisplayMode.NAME_AND_SHORT_ID;
    public static volatile boolean serverJadeTaskProgressIgnoreHiddenTasks;
    public static volatile boolean serverJadeTaskProgressIgnoreRepeatableTasks;

    public static void saveConfigs(boolean saveServerConfig) {
        CLIENT_SPEC.save();
        syncClientValues();
        if (saveServerConfig && SERVER_SPEC.isLoaded()) {
            SERVER_SPEC.save();
            syncServerValues();
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            syncClientValues();
        } else if (event.getConfig().getSpec() == SERVER_SPEC) {
            syncServerValues();
        }
    }

    private static void syncClientValues() {
        jadeShowOwnerInfo = CLIENT_JADE_SHOW_OWNER_INFO.get();
        jadeShowTaskProgress = CLIENT_JADE_SHOW_TASK_PROGRESS.get();
        teamNameDisplayMode = CLIENT_TEAM_NAME_DISPLAY_MODE.get();
    }

    private static void syncServerValues() {
        serverJadeTaskProgressIgnoreHiddenTasks = SERVER_JADE_TASK_PROGRESS_IGNORE_HIDDEN_TASKS.get();
        serverJadeTaskProgressIgnoreRepeatableTasks = SERVER_JADE_TASK_PROGRESS_IGNORE_REPEATABLE_TASKS.get();
    }
}
