package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import appeng.api.networking.IGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tracks active {@link DetectorBlockEntity} instances and indexes them by {@code ownerTeamId}
 * for O(team) lookups instead of scanning every detector on the server.
 */
public class DetectorEntityList {
    private static final Set<DetectorBlockEntity> TRACKED_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<UUID, Set<DetectorBlockEntity>> BY_TEAM = new HashMap<>();

    public static void register(DetectorBlockEntity be) {
        synchronized (TRACKED_ENTITIES) {
            TRACKED_ENTITIES.add(be);
            addToTeamIndex(be);
        }
    }

    public static void unregister(DetectorBlockEntity be) {
        synchronized (TRACKED_ENTITIES) {
            removeFromTeamIndex(be, be.ownerTeamId);
            TRACKED_ENTITIES.remove(be);
        }
    }

    /**
     * Call after {@code ownerTeamId} changes while the block entity may already be registered
     * (e.g. placement, {@link DetectorBlockEntity#setOwnerTeam}).
     */
    public static void notifyOwnerTeamIdChanged(DetectorBlockEntity be, UUID previousTeamId) {
        synchronized (TRACKED_ENTITIES) {
            if (!TRACKED_ENTITIES.contains(be)) {
                return;
            }
            removeFromTeamIndex(be, previousTeamId);
            addToTeamIndex(be);
        }
    }

    private static void addToTeamIndex(DetectorBlockEntity be) {
        UUID id = be.ownerTeamId;
        if (id == null) {
            return;
        }
        BY_TEAM.computeIfAbsent(id, k -> new HashSet<>()).add(be);
    }

    private static void removeFromTeamIndex(DetectorBlockEntity be, UUID teamId) {
        if (teamId == null) {
            return;
        }
        Set<DetectorBlockEntity> set = BY_TEAM.get(teamId);
        if (set != null) {
            set.remove(be);
            if (set.isEmpty()) {
                BY_TEAM.remove(teamId);
            }
        }
    }

    /**
     * Snapshot of detectors owned by {@code teamId}; safe to iterate outside the lock.
     * Returns a caller-owned mutable list with a single allocation (no defensive copy).
     */
    public static List<DetectorBlockEntity> copyForTeam(UUID teamId) {
        if (teamId == null) {
            return List.of();
        }
        synchronized (TRACKED_ENTITIES) {
            Set<DetectorBlockEntity> set = BY_TEAM.get(teamId);
            if (set == null || set.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(set);
        }
    }

    /**
     * Snapshot of all detectors currently attached to {@code grid} (including the caller).
     * Used to detect multi-detector conflicts on a single AE2 network, similar to how the
     * AE2 ME Controller refuses to boot when multiple controllers share a network.
     *
     * <p>O(N) in the number of tracked detectors; N is small in practice (usually 0–2).
     */
    public static List<DetectorBlockEntity> findInGrid(IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        synchronized (TRACKED_ENTITIES) {
            List<DetectorBlockEntity> result = null;
            for (DetectorBlockEntity be : TRACKED_ENTITIES) {
                if (be == null || be.isRemoved()) {
                    continue;
                }
                IGrid g;
                try {
                    g = be.getMainNode().getGrid();
                } catch (Throwable t) {
                    continue;
                }
                if (g == grid) {
                    if (result == null) {
                        result = new ArrayList<>(2);
                    }
                    result.add(be);
                }
            }
            return result == null ? List.of() : result;
        }
    }
}
