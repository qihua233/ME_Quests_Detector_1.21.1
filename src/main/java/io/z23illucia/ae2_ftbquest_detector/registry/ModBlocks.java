package io.z23illucia.ae2_ftbquest_detector.registry;


import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import io.z23illucia.ae2_ftbquest_detector.block.DetectorBlock;
import static io.z23illucia.ae2_ftbquest_detector.Ae2_ftbquest_detector.MODID;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final RegistryObject<Block> DETECTOR_BLOCK = BLOCKS.register("me_quests_detector", DetectorBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}

