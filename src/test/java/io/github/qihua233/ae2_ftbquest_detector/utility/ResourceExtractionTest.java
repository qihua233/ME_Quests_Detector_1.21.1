package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceExtractionTest {
    @Test
    void returnsActualExtractionInsteadOfSimulationAmount() {
        long extracted = ResourceExtraction.execute(10L, requested -> 8L, requested -> 5L);

        assertEquals(5L, extracted);
    }

    @Test
    void skipsModulationWhenSimulationFindsNothing() {
        AtomicInteger modulationCalls = new AtomicInteger();

        long extracted = ResourceExtraction.execute(10L, requested -> 0L, requested -> {
            modulationCalls.incrementAndGet();
            return requested;
        });

        assertEquals(0L, extracted);
        assertEquals(0, modulationCalls.get());
    }

    @Test
    void ignoresNonPositiveRequests() {
        AtomicInteger calls = new AtomicInteger();

        long extracted = ResourceExtraction.execute(0L, requested -> {
            calls.incrementAndGet();
            return requested;
        }, requested -> requested);

        assertEquals(0L, extracted);
        assertEquals(0, calls.get());
    }
}
