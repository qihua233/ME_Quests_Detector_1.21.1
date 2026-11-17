package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockingTaskSweepTest {
    @Test
    void followsAReverseOrderedDependencyChainAcrossBatches() {
        var sweep = new UnlockingTaskSweep<>(List.of(4, 3, 2, 1, 0));
        Set<Integer> completed = new HashSet<>();
        runToEnd(sweep, task -> (task == 0 || completed.contains(task - 1)) && completed.add(task));

        assertEquals(Set.of(0, 1, 2, 3, 4), completed);
        assertEquals(5, sweep.completedCount());
    }

    @Test
    void waitsForBothBranchesBeforeCompletingTheirDependant() {
        var sweep = new UnlockingTaskSweep<>(List.of("join", "right", "left", "root"));
        Map<String, Set<String>> dependencies = Map.of(
                "root", Set.of(), "left", Set.of("root"),
                "right", Set.of("root"), "join", Set.of("left", "right"));
        Set<String> completed = new HashSet<>();
        List<String> order = new ArrayList<>();
        runToEnd(sweep, task -> {
            if (!completed.containsAll(dependencies.get(task))) {
                return false;
            }
            completed.add(task);
            order.add(task);
            return true;
        });

        assertEquals("root", order.getFirst());
        assertEquals("join", order.getLast());
        assertEquals(4, sweep.completedCount());
    }

    @Test
    void stopsAtAnUnfulfilledExternalRequirementWithoutSkippingIndependentTasks() {
        var sweep = new UnlockingTaskSweep<>(List.of("after_item_quest", "independent"));
        Set<String> completed = new HashSet<>();
        runToEnd(sweep, task -> task.equals("independent") && completed.add(task));

        assertEquals(Set.of("independent"), completed);
        assertEquals(1, sweep.completedCount());
    }

    @Test
    void stopsWhenACycleHasNoUnlockedTask() {
        var sweep = new UnlockingTaskSweep<>(List.of("a", "b"));
        AtomicInteger checks = new AtomicInteger();
        runToEnd(sweep, task -> {
            checks.incrementAndGet();
            return false;
        });

        assertEquals(2, checks.get());
        assertEquals(0, sweep.completedCount());
    }

    @Test
    void respectsThePerTickBudgetAndKeepsPassProgressAcrossTicks() {
        var sweep = new UnlockingTaskSweep<>(List.of("blocked", "ready"));
        AtomicInteger checks = new AtomicInteger();
        Predicate<String> submit = task -> {
            checks.incrementAndGet();
            return task.equals("ready");
        };

        sweep.advance(1, submit);
        assertEquals(1, checks.get());
        assertFalse(sweep.isFinished());
        sweep.advance(1, submit);
        assertEquals(2, checks.get());
        assertEquals(1, sweep.completedCount());
        assertFalse(sweep.isFinished());
        sweep.advance(1, submit);
        assertEquals(3, checks.get());
        assertTrue(sweep.isFinished());
    }

    @Test
    void submitsEachCandidateOnlyOnceEvenWhenItsCompletionIsImmediatelyReset() {
        var sweep = new UnlockingTaskSweep<>(List.of("repeatable", "blocked", "repeatable"));
        AtomicInteger submissions = new AtomicInteger();
        runToEnd(sweep, task -> {
            if (task.equals("repeatable")) {
                submissions.incrementAndGet();
                return true;
            }
            return false;
        });

        assertEquals(1, submissions.get());
        assertEquals(1, sweep.completedCount());
    }

    @Test
    void finishesEmptySweepsWithoutInvokingSubmission() {
        var sweep = new UnlockingTaskSweep<String>(List.of());
        assertTrue(sweep.isFinished());
        sweep.advance(10, task -> { throw new AssertionError("No candidates"); });
        assertEquals(0, sweep.completedCount());
    }

    @Test
    void rejectsANonPositiveBudget() {
        var sweep = new UnlockingTaskSweep<>(List.of("task"));
        assertThrows(IllegalArgumentException.class, () -> sweep.advance(0, task -> true));
    }

    private static <T> void runToEnd(UnlockingTaskSweep<T> sweep, Predicate<T> submit) {
        for (int tick = 0; tick < 100 && !sweep.isFinished(); tick++) {
            sweep.advance(2, submit);
        }
        assertTrue(sweep.isFinished(), "A one-shot sweep must stop once no further progress is possible");
    }
}
