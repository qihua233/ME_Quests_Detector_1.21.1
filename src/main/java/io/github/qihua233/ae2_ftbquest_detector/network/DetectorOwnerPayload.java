package io.github.qihua233.ae2_ftbquest_detector.network;

import io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector;
import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.utility.TeamDisplayNameResolver;
import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.UUID;

public record DetectorOwnerPayload(UUID teamId, String rawTeamName) implements CustomPacketPayload {
    private static final int MAX_TEAM_NAME_LENGTH = 256;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Type<DetectorOwnerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2_ftbquest_detector.MODID, "detector_owner"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DetectorOwnerPayload> STREAM_CODEC =
            StreamCodec.of(DetectorOwnerPayload::encode, DetectorOwnerPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, DetectorOwnerPayload payload) {
        buffer.writeUUID(payload.teamId);
        buffer.writeUtf(payload.rawTeamName, MAX_TEAM_NAME_LENGTH);
    }

    private static DetectorOwnerPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DetectorOwnerPayload(buffer.readUUID(), buffer.readUtf(MAX_TEAM_NAME_LENGTH));
    }

    public static void handle(DetectorOwnerPayload payload, IPayloadContext context) {
        try {
            // FTB Teams and client chat state are game-thread data, never touch them on Netty.
            context.enqueueWork(() -> handleOnClientThread(payload, context));
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to schedule detector owner payload handling", exception);
        }
    }

    private static void handleOnClientThread(DetectorOwnerPayload payload, IPayloadContext context) {
        try {
            String teamName = TeamDisplayNameResolver.formatDisplayName(
                    payload.rawTeamName, payload.teamId, Config.teamNameDisplayMode);
            if (teamName != null) {
                context.player().displayClientMessage(
                        Component.translatable("ae2-ftbquests-detector.detector.owner_is", teamName), true);
            }
        } catch (RuntimeException exception) {
            // A stale client-side team snapshot must not terminate the connection.
            LOGGER.warn("Failed to display detector owner payload", exception);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
