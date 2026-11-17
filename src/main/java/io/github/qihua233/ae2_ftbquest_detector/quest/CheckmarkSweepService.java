package io.github.qihua233.ae2_ftbquest_detector.quest;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.github.qihua233.ae2_ftbquest_detector.utility.QuestTaskEligibility;
import io.github.qihua233.ae2_ftbquest_detector.utility.TickCooldown;
import io.github.qihua233.ae2_ftbquest_detector.utility.UnlockingTaskSweep;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs player-requested checkmark sweeps on the authoritative server thread. */
public final class CheckmarkSweepService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TASK_CHECKS_PER_TICK = 128;
    public static final int REQUEST_COOLDOWN_TICKS = 60;
    private static final Map<MinecraftServer, Map<UUID, SweepJob>> JOBS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, TickCooldown<UUID>> REQUEST_COOLDOWNS = new IdentityHashMap<>();

    private CheckmarkSweepService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CheckmarkSweepService::onServerTick);
        NeoForge.EVENT_BUS.addListener(CheckmarkSweepService::onServerStopped);
    }

    public static void start(ServerPlayer player) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || file.server != player.getServer() || file.isLoading()) {
            LOGGER.warn("Cannot start checkmark sweep for player {}: quest data is unavailable or loading",
                    player.getUUID());
            return;
        }
        TeamData data = file.getTeamData(player).orElse(null);
        if (data == null || data.isLocked()) {
            LOGGER.warn("Cannot start checkmark sweep for player {}: team data is unavailable or locked",
                    player.getUUID());
            return;
        }

        Map<UUID, SweepJob> serverJobs = JOBS.computeIfAbsent(file.server, ignored -> new HashMap<>());
        if (serverJobs.containsKey(data.getTeamId())) {
            return;
        }
        TickCooldown<UUID> cooldown = REQUEST_COOLDOWNS.computeIfAbsent(
                file.server, ignored -> new TickCooldown<>(REQUEST_COOLDOWN_TICKS));
        if (!cooldown.tryAcquire(player.getUUID(), file.server.overworld().getGameTime())) {
            return;
        }

        List<Task> candidates = new ArrayList<>();
        file.forAllQuests(quest -> {
            List<Task> tasks = quest.getTasksAsList();
            if (!tasks.isEmpty() && tasks.stream().allMatch(CheckmarkTask.class::isInstance)) {
                for (Task task : tasks) {
                    if (!data.isCompleted(task)) {
                        candidates.add(task);
                    }
                }
            }
        });
        SweepJob job = new SweepJob(file, data, player.getUUID(), new UnlockingTaskSweep<>(candidates));
        if (job.sweep.isFinished()) {
            tell(player, "completed", 0);
        } else {
            serverJobs.put(data.getTeamId(), job);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        TickCooldown<UUID> cooldown = REQUEST_COOLDOWNS.get(server);
        if (cooldown != null) {
            cooldown.removeExpired(server.overworld().getGameTime());
        }
        Map<UUID, SweepJob> serverJobs = JOBS.get(server);
        if (serverJobs == null) {
            return;
        }
        var iterator = serverJobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            SweepJob job = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
            if (player == null || !job.isValidFor(player)) {
                LOGGER.info("Stopped checkmark sweep for team {}, player {}: player disconnected, team or quest data changed, or progress was locked",
                        entry.getKey(), job.playerId);
                iterator.remove();
                continue;
            }
            try {
                job.file.withPlayerContext(player,
                        () -> job.sweep.advance(TASK_CHECKS_PER_TICK, task -> job.submit(task, player)));
                if (job.sweep.isFinished()) {
                    tell(player, "completed", job.sweep.completedCount());
                    iterator.remove();
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to sweep checkmark tasks for team {}", entry.getKey(), exception);
                iterator.remove();
            }
        }
        if (serverJobs.isEmpty()) {
            JOBS.remove(server);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        JOBS.remove(event.getServer());
        REQUEST_COOLDOWNS.remove(event.getServer());
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("ae2_ftbquest_detector.checkmark_sweep." + key, args), false);
    }

    private record SweepJob(ServerQuestFile file, TeamData data, UUID playerId, UnlockingTaskSweep<Task> sweep) {
        private boolean isValidFor(ServerPlayer player) {
            return ServerQuestFile.INSTANCE == file && !file.isLoading() && !data.isLocked()
                    && file.getTeamData(player).orElse(null) == data;
        }

        private boolean submit(Task task, ServerPlayer player) {
            // A quest-file reload must never cause writes through stale task instances.
            if (file.getTask(task.getId()) != task) {
                throw new IllegalStateException("Quest tasks changed during the checkmark sweep");
            }
            Quest quest = task.getQuest();
            if (data.isLocked() || !data.areDependenciesComplete(quest)
                    || !QuestTaskEligibility.canSubmit(task, data)
                    || !quest.getTasks().stream().allMatch(CheckmarkTask.class::isInstance)) {
                return false;
            }
            // Use the same submission path as a manual click, including events and team synchronization.
            task.submitTask(data, player);
            // Completion callbacks can immediately reset repeatable quests. Still retire this task
            // from this one-shot sweep so it cannot repeatedly run those callbacks or rewards.
            return true;
        }
    }
}
