package io.z23illucia.ae2_ftbquest_detector.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DetectorEntityList {
    private static final List<DetectorBlockEntity> TRACKED_ENTITIES = Collections.synchronizedList(new ArrayList<>());


    public static void register(DetectorBlockEntity be) {
        TRACKED_ENTITIES.add(be);
    }

    public static void unregister(DetectorBlockEntity be) {
        TRACKED_ENTITIES.remove(be);
    }

    public static List<DetectorBlockEntity> getAll() {
        return List.copyOf(TRACKED_ENTITIES); // 返回不可修改视图
    }
}
