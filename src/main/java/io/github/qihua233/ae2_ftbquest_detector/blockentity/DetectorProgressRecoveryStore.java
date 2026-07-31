package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeps detector progress that could not be written before a chunk unload.
 * The server key is weak so a stopped server cannot be retained by this store.
 */
final class DetectorProgressRecoveryStore {
    static final int MAX_PENDING_ENTRIES = 8192;
    private static final Map<MinecraftServer, PendingProgressLedger> LEDGERS = new WeakHashMap<>();

    private DetectorProgressRecoveryStore() {
    }

    static void retain(MinecraftServer server, UUID teamId, long taskId, long targetProgress) {
        if (server == null || teamId == null || taskId <= 0L || targetProgress <= 0L) {
            return;
        }
        synchronized (LEDGERS) {
            LEDGERS.computeIfAbsent(server, ignored -> new PendingProgressLedger())
                    .merge(teamId, taskId, targetProgress);
        }
    }

    static List<PendingProgress> copyFor(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return List.of();
        }
        synchronized (LEDGERS) {
            PendingProgressLedger ledger = LEDGERS.get(server);
            return ledger == null ? List.of() : ledger.copyFor(teamId);
        }
    }

    static void remove(MinecraftServer server, UUID teamId, long taskId) {
        if (server == null || teamId == null || taskId <= 0L) {
            return;
        }
        synchronized (LEDGERS) {
            PendingProgressLedger ledger = LEDGERS.get(server);
            if (ledger == null) {
                return;
            }
            ledger.remove(teamId, taskId);
            if (ledger.isEmpty()) {
                LEDGERS.remove(server);
            }
        }
    }

    static void discard(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return;
        }
        synchronized (LEDGERS) {
            PendingProgressLedger ledger = LEDGERS.get(server);
            if (ledger == null) {
                return;
            }
            ledger.discard(teamId);
            if (ledger.isEmpty()) {
                LEDGERS.remove(server);
            }
        }
    }

    record PendingProgress(long taskId, long targetProgress) {
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

        synchronized void remove(UUID teamId, long taskId) {
            values.remove(new PendingKey(teamId, taskId));
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
