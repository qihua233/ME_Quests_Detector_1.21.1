package io.github.qihua233.ae2_ftbquest_detector;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue COMMON_JADE_SHOW_OWNER_INFO;
    public static final ModConfigSpec.BooleanValue COMMON_JADE_SHOW_TASK_PROGRESS;
    public static final ModConfigSpec.EnumValue<TeamNameDisplayMode> COMMON_TEAM_NAME_DISPLAY_MODE;

    static {
        COMMON_BUILDER.push("common");

        COMMON_JADE_SHOW_OWNER_INFO = COMMON_BUILDER
                .comment("Show owner team information in Jade tooltip")
                .define("jadeShowOwnerInfo", true);

        COMMON_JADE_SHOW_TASK_PROGRESS = COMMON_BUILDER
                .comment("Show task completion progress in Jade tooltip")
                .define("jadeShowTaskProgress", true);

        COMMON_TEAM_NAME_DISPLAY_MODE = COMMON_BUILDER
                .comment(
                        "How to format the owner team in Jade and action bar:",
                        "NAME_AND_SHORT_ID — display name plus short id (e.g. MyTeam#A1B2C3D4)",
                        "NAME_ONLY — resolved team display name only",
                        "SHORT_ID_ONLY — short id derived from team UUID only")
                .defineEnum("teamNameDisplayMode", TeamNameDisplayMode.NAME_AND_SHORT_ID);

        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    public static final int detectorTickRate = 40;
    public static boolean jadeShowOwnerInfo = true;
    public static boolean jadeShowTaskProgress = true;
    public static TeamNameDisplayMode teamNameDisplayMode = TeamNameDisplayMode.NAME_AND_SHORT_ID;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            jadeShowOwnerInfo = COMMON_JADE_SHOW_OWNER_INFO.get();
            jadeShowTaskProgress = COMMON_JADE_SHOW_TASK_PROGRESS.get();
            teamNameDisplayMode = COMMON_TEAM_NAME_DISPLAY_MODE.get();
        }
    }
}
