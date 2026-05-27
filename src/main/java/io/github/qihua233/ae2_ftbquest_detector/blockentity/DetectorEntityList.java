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

/** 跟踪已加载检测器，并按队伍建立索引。 */
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

    /** ownerTeamId 改变后同步更新队伍索引。 */
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

    /** 返回指定队伍下检测器的可遍历快照。 */
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

    public static void markActiveCacheDirtyForTeam(UUID teamId) {
        List<DetectorBlockEntity> list = copyForTeam(teamId);
        int size = list.size();
        for (int i = 0; i < size; i++) {
            DetectorBlockEntity be = list.get(i);
            if (be != null && !be.isRemoved()) {
                be.markActiveCacheDirty();
            }
        }
    }

    /** 返回同一 AE2 网络中的检测器快照。 */
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
