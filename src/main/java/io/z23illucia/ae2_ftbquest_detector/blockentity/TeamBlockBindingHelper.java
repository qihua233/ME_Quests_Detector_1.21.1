package io.z23illucia.ae2_ftbquest_detector.blockentity;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamBlockBindingHelper {

    public static void addBoundBlock(UUID teamId, ServerLevel level, BlockPos pos) {
        BoundBlock bb = new BoundBlock(level.dimension(), pos);
        FTBTeamsAPI.api().getManager().getTeamByID(teamId).ifPresent(team -> {
            List<BoundBlock> list = new ArrayList<>(team.getProperty(MyTeamProperties.BOUND_BLOCKS));
            if (!list.contains(bb)) {
                list.add(bb);
                team.setProperty(MyTeamProperties.BOUND_BLOCKS, list);
            }
        });
    }

    public static void removeBoundBlock(UUID teamId, ServerLevel level, BlockPos pos) {
        BoundBlock bb = new BoundBlock(level.dimension(), pos);
        FTBTeamsAPI.api().getManager().getTeamByID(teamId).ifPresent(team -> {
            List<BoundBlock> list = new ArrayList<>(team.getProperty(MyTeamProperties.BOUND_BLOCKS));
            if (list.remove(bb)) {
                team.setProperty(MyTeamProperties.BOUND_BLOCKS, list);
            }
        });
    }

    public static List<DetectorBlockEntity> getBoundBlockEntities(MinecraftServer server, UUID teamId) {
        List<DetectorBlockEntity> result = new ArrayList<>();

        FTBTeamsAPI.api().getManager().getTeamByID(teamId).ifPresent(team -> {
            List<BoundBlock> list = team.getProperty(MyTeamProperties.BOUND_BLOCKS);

            for (BoundBlock bb : list) {
                ServerLevel level = server.getLevel(bb.dimension());
                if (level != null && level.hasChunkAt(bb.pos())) {
                    BlockEntity be = level.getBlockEntity(bb.pos());
                    if (be instanceof DetectorBlockEntity typed) {
                        result.add(typed);
                    }
                }
            }
        });

        return result;
    }


}
