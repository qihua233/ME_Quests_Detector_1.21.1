package io.github.qihua233.ae2_ftbquest_detector.utility;

/** Resolves the user-facing detector state from team, network, and block state. */
public final class DetectorStatusPolicy {
    private DetectorStatusPolicy() {
    }

    public enum State {
        NO_OWNER_TEAM,
        INVALID_OWNER_TEAM,
        TEMPORARILY_UNAVAILABLE,
        NETWORK_CONFLICT,
        OFFLINE,
        ACTIVE
    }

    public static State resolve(
            TeamOwnershipValidator.Status teamStatus,
            boolean networkConflict,
            boolean powered
    ) {
        if (teamStatus == null) {
            return State.INVALID_OWNER_TEAM;
        }
        return switch (teamStatus) {
            case NONE -> State.NO_OWNER_TEAM;
            case INVALID -> State.INVALID_OWNER_TEAM;
            case TEMPORARILY_UNAVAILABLE -> State.TEMPORARILY_UNAVAILABLE;
            case USABLE -> {
                if (networkConflict) {
                    yield State.NETWORK_CONFLICT;
                }
                yield powered ? State.ACTIVE : State.OFFLINE;
            }
        };
    }
}
