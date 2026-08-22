package de.pumpecraft.ai.support;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Daemon-Threads für die HTTP-Aufrufe; sie halten den Server beim Stop nicht auf. */
public final class DaemonThreads implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public DaemonThreads(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
