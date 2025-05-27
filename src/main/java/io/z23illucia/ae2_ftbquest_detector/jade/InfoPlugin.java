package io.z23illucia.ae2_ftbquest_detector.jade;

import io.z23illucia.ae2_ftbquest_detector.block.DetectorBlock;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class InfoPlugin implements IWailaPlugin {
    public static ResourceLocation State;

    @Override
    public void register(IWailaCommonRegistration registration) {

        registration.registerBlockDataProvider(
                new MEQuestsDetectorComponentProvider(),
                DetectorBlockEntity.class
        );
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(
                new MEQuestsDetectorComponentProvider(),
                AbstractFurnaceBlock.class
        );
    }

}
