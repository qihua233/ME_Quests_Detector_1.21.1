package io.z23illucia.ae2_ftbquest_detector.jade;

import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class MEQuestsDetectorComponentProvider implements
        IBlockComponentProvider, IServerDataProvider<BlockAccessor>{


    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    ) {
        System.out.println("out");
        tooltip.append(
                Component.literal(
                       "test"
                )
        );
        if(accessor.getServerData().contains("State")){
            System.out.println("add");
            tooltip.append(
                    Component.translatable(
                            accessor.getServerData().getString("State")
                    )
            );
        }


    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        System.out.println("server");
        var detector = (DetectorBlockEntity) accessor.getBlockEntity();
        String msg;
        if(detector.getGridNode(null).isPowered())
        {
            msg = "设备已连接";
        }
        else{
            msg = "设备未连接";
        }
        data.putString("State", msg);
    }

    @Override
    public ResourceLocation getUid() {
        return InfoPlugin.State;
    }
}
