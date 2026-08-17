package de.pumpecraft.ai;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Daemon-Threads für die HTTP-Aufrufe; sie halten den Server beim Stop nicht auf. */
final class AiThreads implements ThreadFactory {
    private final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "PumpeAI-" + counter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
