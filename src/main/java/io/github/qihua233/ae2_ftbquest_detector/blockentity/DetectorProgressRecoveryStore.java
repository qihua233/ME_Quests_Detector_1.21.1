package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps detector progress that could not be written before a chunk unload.
 * The ledger is stored in the overworld SavedData so it survives normal server restarts.
 */
final class DetectorProgressRecoveryStore {
    static final int MAX_PENDING_ENTRIES = 8192;
    private static final String DATA_ID = "ae2_ftbquest_detector_progress";
    private static final SavedData.Factory<PendingProgressSavedData> FACTORY =
            new SavedData.Factory<>(PendingProgressSavedData::new, PendingProgressSavedData::load);

    private DetectorProgressRecoveryStore() {
    }

    static void retain(MinecraftServer server, UUID teamId, long taskId, long targetProgress) {
        if (server == null || teamId == null || taskId <= 0L || targetProgress <= 0L) {
            return;
        }
        PendingProgressSavedData data = getOrCreate(server);
        if (data != null) {
            data.merge(teamId, taskId, targetProgress);
        }
    }

    static List<PendingProgress> copyFor(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return List.of();
        }
        PendingProgressSavedData data = getExisting(server);
        return data == null ? List.of() : data.copyFor(teamId);
    }

    static void remove(MinecraftServer server, UUID teamId, long taskId) {
        if (server == null || teamId == null || taskId <= 0L) {
            return;
        }
        PendingProgressSavedData data = getExisting(server);
        if (data != null) {
            data.remove(teamId, taskId);
        }
    }

    static void removeIfAtMost(MinecraftServer server, UUID teamId, long taskId, long committedProgress) {
        if (server == null || teamId == null || taskId <= 0L || committedProgress <= 0L) {
            return;
        }
        PendingProgressSavedData data = getExisting(server);
        if (data != null) {
            data.removeIfAtMost(teamId, taskId, committedProgress);
        }
    }

    static void discard(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return;
        }
        PendingProgressSavedData data = getExisting(server);
        if (data != null) {
            data.discard(teamId);
        }
    }

    private static PendingProgressSavedData getOrCreate(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return null;
        }
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    private static PendingProgressSavedData getExisting(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return null;
        }
        return overworld.getDataStorage().get(FACTORY, DATA_ID);
    }

    record PendingProgress(long taskId, long targetProgress) {
    }

    private static final class PendingProgressSavedData extends SavedData {
        private final PendingProgressLedger ledger = new PendingProgressLedger();

        private static PendingProgressSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            PendingProgressSavedData data = new PendingProgressSavedData();
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                if (!entry.hasUUID("teamId") || !entry.contains("taskId", Tag.TAG_LONG)
                        || !entry.contains("targetProgress", Tag.TAG_LONG)) {
                    continue;
                }
                long taskId = entry.getLong("taskId");
                long targetProgress = entry.getLong("targetProgress");
                if (taskId > 0L && targetProgress > 0L) {
                    data.ledger.merge(entry.getUUID("teamId"), taskId, targetProgress);
                }
            }
            return data;
        }

        private void merge(UUID teamId, long taskId, long targetProgress) {
            ledger.merge(teamId, taskId, targetProgress);
            setDirty();
        }

        private List<PendingProgress> copyFor(UUID teamId) {
            return ledger.copyFor(teamId);
        }

        private void remove(UUID teamId, long taskId) {
            int before = ledger.size();
            ledger.remove(teamId, taskId);
            if (ledger.size() != before) {
                setDirty();
            }
        }

        private void removeIfAtMost(UUID teamId, long taskId, long committedProgress) {
            int before = ledger.size();
            ledger.removeIfAtMost(teamId, taskId, committedProgress);
            if (ledger.size() != before) {
                setDirty();
            }
        }

        private void discard(UUID teamId) {
            int before = ledger.size();
            ledger.discard(teamId);
            if (ledger.size() != before) {
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag entries = new ListTag();
            for (PendingProgressEntry entry : ledger.snapshot()) {
                CompoundTag value = new CompoundTag();
                value.putUUID("teamId", entry.teamId());
                value.putLong("taskId", entry.taskId());
                value.putLong("targetProgress", entry.targetProgress());
                entries.add(value);
            }
            tag.put("entries", entries);
            return tag;
        }
    }

    private record PendingProgressEntry(UUID teamId, long taskId, long targetProgress) {
    }

    static final class PendingProgressLedger {
        private final int maxEntries;
        private final LinkedHashMap<PendingKey, Long> values = new LinkedHashMap<>();

        PendingProgressLedger() {
            this(MAX_PENDING_ENTRIES);
        }

        PendingProgressLedger(int maxEntries) {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries must be positive");
            }
            this.maxEntries = maxEntries;
        }

        synchronized void merge(UUID teamId, long taskId, long targetProgress) {
            PendingKey key = new PendingKey(teamId, taskId);
            Long previous = values.get(key);
            if (previous != null) {
                if (targetProgress > previous) {
                    values.put(key, targetProgress);
                }
                return;
            }
            while (values.size() >= maxEntries) {
                Iterator<PendingKey> iterator = values.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            values.put(key, targetProgress);
        }

        synchronized List<PendingProgress> copyFor(UUID teamId) {
            List<PendingProgress> result = new ArrayList<>();
            for (Map.Entry<PendingKey, Long> entry : values.entrySet()) {
                if (entry.getKey().teamId().equals(teamId)) {
                    result.add(new PendingProgress(entry.getKey().taskId(), entry.getValue()));
                }
            }
            return result;
        }

        synchronized List<PendingProgressEntry> snapshot() {
            List<PendingProgressEntry> result = new ArrayList<>(values.size());
            for (Map.Entry<PendingKey, Long> entry : values.entrySet()) {
                PendingKey key = entry.getKey();
                result.add(new PendingProgressEntry(key.teamId(), key.taskId(), entry.getValue()));
            }
            return result;
        }

        synchronized void remove(UUID teamId, long taskId) {
            values.remove(new PendingKey(teamId, taskId));
        }

        synchronized void removeIfAtMost(UUID teamId, long taskId, long committedProgress) {
            PendingKey key = new PendingKey(teamId, taskId);
            Long targetProgress = values.get(key);
            if (targetProgress != null && targetProgress <= committedProgress) {
                values.remove(key);
            }
        }

        synchronized void discard(UUID teamId) {
            values.keySet().removeIf(key -> key.teamId().equals(teamId));
        }

        synchronized boolean isEmpty() {
            return values.isEmpty();
        }

        synchronized int size() {
            return values.size();
        }

        private record PendingKey(UUID teamId, long taskId) {
        }
    }
}
