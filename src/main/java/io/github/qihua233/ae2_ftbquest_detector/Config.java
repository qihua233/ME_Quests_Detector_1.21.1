package io.github.qihua233.ae2_ftbquest_detector;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DETECTOR;
    public static final ModConfigSpec.IntValue DETECTOR_TICK_RATE;

    static {
        BUILDER.push("General");
        
        ENABLE_DETECTOR = BUILDER
                .comment("Enable or disable the AE2 FTB Quests Detector block")
                .define("enableDetector", true);
                
        DETECTOR_TICK_RATE = BUILDER
                .comment("How often (in ticks) the detector should perform a full scan of the AE2 network. Default: 20 (1 second)")
                .defineInRange("detectorTickRate", 20, 10, 1200);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableDetector;
    public static int detectorTickRate;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            enableDetector = ENABLE_DETECTOR.get();
            detectorTickRate = DETECTOR_TICK_RATE.get();
        }
    }
}
