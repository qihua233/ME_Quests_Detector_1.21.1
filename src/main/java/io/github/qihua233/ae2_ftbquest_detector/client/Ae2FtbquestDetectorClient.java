package io.github.qihua233.ae2_ftbquest_detector.client;

import io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Ae2_ftbquest_detector.MODID, dist = Dist.CLIENT)
public final class Ae2FtbquestDetectorClient {

    public Ae2FtbquestDetectorClient(ModContainer modContainer) {
        // Keep client GUI registration out of the shared mod entrypoint.
        DetectorClientPreferencesSync.register();
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> DetectorConfigScreen.create(parent));
    }
}
