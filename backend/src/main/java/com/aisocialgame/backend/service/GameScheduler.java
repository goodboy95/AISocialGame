package com.aisocialgame.backend.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

@Component
public class GameScheduler {

    private final ScheduledExecutorService executor;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public GameScheduler() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("game-scheduler-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newScheduledThreadPool(4, factory);
    }

    public void schedule(long sessionId, Runnable task, Duration delay) {
        cancel(sessionId);
        long millis = Math.max(delay.toMillis(), 0);
        ScheduledFuture<?> future = executor.schedule(task, millis, TimeUnit.MILLISECONDS);
        scheduledTasks.put(sessionId, future);
    }

    public void cancel(long sessionId) {
        ScheduledFuture<?> future = scheduledTasks.remove(sessionId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void executeAsync(Runnable task) {
        executor.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
