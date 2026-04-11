package io.github.qihua233.ae2_ftbquest_detector;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue COMMON_JADE_SHOW_OWNER_INFO;
    public static final ModConfigSpec.BooleanValue COMMON_JADE_SHOW_TASK_PROGRESS;

    static {
        COMMON_BUILDER.push("common");

        COMMON_JADE_SHOW_OWNER_INFO = COMMON_BUILDER
                .comment("Show owner team information in Jade tooltip")
                .define("jadeShowOwnerInfo", true);

        COMMON_JADE_SHOW_TASK_PROGRESS = COMMON_BUILDER
                .comment("Show task completion progress in Jade tooltip")
                .define("jadeShowTaskProgress", true);

        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    public static final int detectorTickRate = 40;
    public static boolean jadeShowOwnerInfo = true;
    public static boolean jadeShowTaskProgress = true;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            jadeShowOwnerInfo = COMMON_JADE_SHOW_OWNER_INFO.get();
            jadeShowTaskProgress = COMMON_JADE_SHOW_TASK_PROGRESS.get();
        }
    }
}
