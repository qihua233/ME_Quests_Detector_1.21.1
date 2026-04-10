package io.z23illucia.ae2_ftbquest_detector.blockentity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class DetectorEntityList {
    private static final Set<DetectorBlockEntity> TRACKED_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());

    public static void register(DetectorBlockEntity be) {
        synchronized (TRACKED_ENTITIES) {
            TRACKED_ENTITIES.add(be);
        }
    }

    public static void unregister(DetectorBlockEntity be) {
        synchronized (TRACKED_ENTITIES) {
            TRACKED_ENTITIES.remove(be);
        }
    }

    public static Set<DetectorBlockEntity> getAll() {
        synchronized (TRACKED_ENTITIES) {
            return Set.copyOf(TRACKED_ENTITIES);
        }
    }
}