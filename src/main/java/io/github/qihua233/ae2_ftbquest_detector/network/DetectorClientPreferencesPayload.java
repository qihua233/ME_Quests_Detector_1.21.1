package io.github.qihua233.ae2_ftbquest_detector.network;

import com.mojang.logging.LogUtils;
import io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector;
import io.github.qihua233.ae2_ftbquest_detector.integration.jade.DetectorJadeProgressPreferences;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public record DetectorClientPreferencesPayload(boolean showTaskProgress) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Type<DetectorClientPreferencesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2_ftbquest_detector.MODID, "client_preferences"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DetectorClientPreferencesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBoolean(payload.showTaskProgress),
                    buffer -> new DetectorClientPreferencesPayload(buffer.readBoolean()));

    public static void handle(DetectorClientPreferencesPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> DetectorJadeProgressPreferences.update(
                    context.player().getUUID(), payload.showTaskProgress));
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to schedule detector client preference handling", exception);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
