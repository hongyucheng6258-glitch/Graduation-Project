package com.campus.platform.module.drawgame.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class DrawGuessRoundTimer {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2,
            new DrawGuessTimerThreadFactory());

    ScheduledFuture<?> start(Runnable tick) {
        return executor.scheduleAtFixedRate(tick, 1, 1, TimeUnit.SECONDS);
    }

    void cancel(ScheduledFuture<?> timer) {
        timer.cancel(false);
    }

    void scheduleRoomCleanup(Runnable cleanup) {
        executor.schedule(cleanup, 30, TimeUnit.MINUTES);
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private static final class DrawGuessTimerThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "draw-guess-timer-" + (++sequence));
            thread.setDaemon(true);
            return thread;
        }
    }
}
