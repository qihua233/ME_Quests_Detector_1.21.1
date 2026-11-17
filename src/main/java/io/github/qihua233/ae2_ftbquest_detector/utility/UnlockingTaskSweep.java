package io.github.qihua233.ae2_ftbquest_detector.utility;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.function.Predicate;

/** A bounded, one-shot sweep which retries blocked tasks only after making progress. */
public final class UnlockingTaskSweep<T> {
    private Deque<T> pending;
    private Deque<T> blocked = new ArrayDeque<>();
    private boolean passMadeProgress;
    private boolean finished;
    private int completedCount;

    public UnlockingTaskSweep(Collection<T> candidates) {
        pending = new ArrayDeque<>(new LinkedHashSet<>(candidates));
        finished = pending.isEmpty();
    }

    /** The predicate must return true only when a task was actually submitted. */
    public void advance(int budget, Predicate<T> submit) {
        if (budget <= 0) {
            throw new IllegalArgumentException("The sweep budget must be positive");
        }
        for (int i = 0; i < budget && !finished; i++) {
            T candidate = pending.removeFirst();
            if (submit.test(candidate)) {
                completedCount++;
                passMadeProgress = true;
            } else {
                blocked.addLast(candidate);
            }
            if (pending.isEmpty()) {
                if (!passMadeProgress || blocked.isEmpty()) {
                    finished = true;
                    blocked.clear();
                } else {
                    Deque<T> previous = pending;
                    pending = blocked;
                    blocked = previous;
                    passMadeProgress = false;
                }
            }
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public int completedCount() {
        return completedCount;
    }
}
