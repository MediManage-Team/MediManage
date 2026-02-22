package org.example.MediManage.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppExecutors {

    private static final AtomicInteger BACKGROUND_COUNTER = new AtomicInteger();
    private static final AtomicInteger SCHEDULER_COUNTER = new AtomicInteger();

    private static final ExecutorService BACKGROUND = Executors.newCachedThreadPool(
            threadFactory("medimanage-bg-", BACKGROUND_COUNTER, true));

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            threadFactory("medimanage-scheduler-", SCHEDULER_COUNTER, true));

    private AppExecutors() {
    }

    public static ExecutorService background() {
        return BACKGROUND;
    }

    public static void runBackground(Runnable task) {
        BACKGROUND.execute(task);
    }

    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return SCHEDULER.schedule(task, delay, unit);
    }

    public static Thread newThread(String name, Runnable task, boolean daemon) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(daemon);
        return thread;
    }

    public static void shutdown() {
        BACKGROUND.shutdownNow();
        SCHEDULER.shutdownNow();
    }

    private static ThreadFactory threadFactory(String prefix, AtomicInteger counter, boolean daemon) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(daemon);
            return thread;
        };
    }
}
