package io.github.qihua233.ae2_ftbquest_detector.network;

import io.github.qihua233.ae2_ftbquest_detector.Ae2_ftbquest_detector;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DetectorNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private DetectorNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Ae2_ftbquest_detector.MODID)
                .versioned(PROTOCOL_VERSION)
                .playToClient(
                        DetectorOwnerPayload.TYPE,
                        DetectorOwnerPayload.STREAM_CODEC,
                        DetectorOwnerPayload::handle);
    }
}
