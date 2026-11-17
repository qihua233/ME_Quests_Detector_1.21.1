package io.github.qihua233.ae2_ftbquest_detector.network;

import io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector;
import io.github.qihua233.ae2_ftbquest_detector.quest.CheckmarkSweepService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** The request carries no team or task IDs; the server resolves the sender's team. */
public record CheckmarkSweepPayload() implements CustomPacketPayload {
    public static final CheckmarkSweepPayload INSTANCE = new CheckmarkSweepPayload();
    public static final Type<CheckmarkSweepPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2_ftbquest_detector.MODID, "checkmark_sweep"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CheckmarkSweepPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public static void handle(CheckmarkSweepPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CheckmarkSweepService.start(player);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
