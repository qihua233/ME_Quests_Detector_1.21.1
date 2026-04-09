package io.z23illucia.ae2_ftbquest_detector.blockentity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DetectorEntityList {
    private static final List<DetectorBlockEntity> TRACKED_ENTITIES = new CopyOnWriteArrayList<>();

    public static void register(DetectorBlockEntity be) {
        TRACKED_ENTITIES.add(be);
    }

    public static void unregister(DetectorBlockEntity be) {
        TRACKED_ENTITIES.remove(be);
    }

    public static List<DetectorBlockEntity> getAll() {
        return TRACKED_ENTITIES; 
    }
}
