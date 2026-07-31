package io.github.qihua233.ae2_ftbquest_detector;

import io.github.qihua233.ae2_ftbquest_detector.registry.ModBlockEntities;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModBlocks;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModItems;
import io.github.qihua233.ae2_ftbquest_detector.registry.ModDataComponents;
import io.github.qihua233.ae2_ftbquest_detector.blockentity.DetectorTeamSyncHandler;
import io.github.qihua233.ae2_ftbquest_detector.network.DetectorNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;

@Mod(Ae2_ftbquest_detector.MODID)
@SuppressWarnings("null")
public class Ae2_ftbquest_detector {

    public static final String MODID = "ae2_ftbquest_detector";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public Ae2_ftbquest_detector(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDataComponents.register(modEventBus);

        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(Config::onLoad);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(DetectorNetwork::registerPayloads);

        DetectorTeamSyncHandler.register();

        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC, "ae2_ftbquest_detector-client.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC, "ae2_ftbquest_detector-server.toml");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DETECTOR_BLOCK_ENTITY.get(),
                (be, context) -> be
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.DETECTOR_BLOCK_ITEM.get());
        }
    }

}
