package io.github.qihua233.ae2_ftbquest_detector.integration.jade;

import io.github.qihua233.ae2_ftbquest_detector.block.DetectorBlock;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class JadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DetectorProvider.INSTANCE, DetectorBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(DetectorProvider.INSTANCE, DetectorBlock.class);
    }
}
