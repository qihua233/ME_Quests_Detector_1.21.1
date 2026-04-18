package io.github.qihua233.ae2_ftbquest_detector;

/**
 * How the owner team is shown in Jade and action bar messages.
 */
public enum TeamNameDisplayMode {
    /** Display name plus short id, e.g. {@code MyTeam#A1B2C3D4} */
    NAME_AND_SHORT_ID,
    /** Resolved display name only */
    NAME_ONLY,
    /** First 8 hex chars of the team UUID (uppercase, no dashes) */
    SHORT_ID_ONLY
}
