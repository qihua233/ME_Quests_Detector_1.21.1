package io.github.qihua233.ae2_ftbquest_detector;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DETECTOR;
    public static final ModConfigSpec.IntValue DETECTOR_TICK_RATE;
    public static final ModConfigSpec.BooleanValue JADE_SHOW_OWNER_INFO;
    public static final ModConfigSpec.BooleanValue JADE_SHOW_TASK_PROGRESS;

    static {
        BUILDER.push("General");
        
        ENABLE_DETECTOR = BUILDER
                .comment("Enable or disable the AE2 FTB Quests Detector block")
                .define("enableDetector", true);
                
        DETECTOR_TICK_RATE = BUILDER
                .comment("How often (in ticks) the detector should perform a full scan of the AE2 network. Default: 20 (1 second)")
                .defineInRange("detectorTickRate", 20, 10, 1200);

        JADE_SHOW_OWNER_INFO = BUILDER
                .comment("Show owner team information in Jade tooltip")
                .define("jadeShowOwnerInfo", true);

        JADE_SHOW_TASK_PROGRESS = BUILDER
                .comment("Show task completion progress in Jade tooltip")
                .define("jadeShowTaskProgress", true);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableDetector;
    public static int detectorTickRate;
    public static boolean jadeShowOwnerInfo;
    public static boolean jadeShowTaskProgress;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            enableDetector = ENABLE_DETECTOR.get();
            detectorTickRate = DETECTOR_TICK_RATE.get();
            jadeShowOwnerInfo = JADE_SHOW_OWNER_INFO.get();
            jadeShowTaskProgress = JADE_SHOW_TASK_PROGRESS.get();
        }
    }
}
