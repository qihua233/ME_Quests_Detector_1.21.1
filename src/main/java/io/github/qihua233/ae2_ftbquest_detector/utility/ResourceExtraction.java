package io.github.qihua233.ae2_ftbquest_detector.utility;

import java.util.Objects;
import java.util.function.LongUnaryOperator;

public final class ResourceExtraction {
    private ResourceExtraction() {
    }

    public static long execute(long requested, LongUnaryOperator simulation, LongUnaryOperator modulation) {
        if (requested <= 0L) {
            return 0L;
        }
        Objects.requireNonNull(simulation, "simulation");
        Objects.requireNonNull(modulation, "modulation");

        long simulated = clamp(simulation.applyAsLong(requested), requested);
        if (simulated == 0L) {
            return 0L;
        }
        return clamp(modulation.applyAsLong(simulated), simulated);
    }

    private static long clamp(long amount, long maximum) {
        return Math.max(0L, Math.min(amount, maximum));
    }
}
