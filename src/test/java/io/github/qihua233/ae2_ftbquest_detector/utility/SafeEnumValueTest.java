package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeEnumValueTest {
    @Test
    void parsesKnownValue() {
        assertEquals(Mode.ENABLED, SafeEnumValue.parse(Mode.class, "ENABLED", Mode.DEFAULT));
    }

    @Test
    void fallsBackForUnknownValue() {
        assertEquals(Mode.DEFAULT, SafeEnumValue.parse(Mode.class, "removed_value", Mode.DEFAULT));
    }

    @Test
    void fallsBackForBlankValue() {
        assertEquals(Mode.DEFAULT, SafeEnumValue.parse(Mode.class, " ", Mode.DEFAULT));
    }

    private enum Mode {
        DEFAULT,
        ENABLED
    }
}
