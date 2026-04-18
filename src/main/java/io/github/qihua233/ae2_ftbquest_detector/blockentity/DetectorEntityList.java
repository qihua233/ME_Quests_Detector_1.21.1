package io.github.qihua233.ae2_ftbquest_detector.blockentity;

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
            return List.copyOf(new ArrayList<>(set));
        }
    }
}
