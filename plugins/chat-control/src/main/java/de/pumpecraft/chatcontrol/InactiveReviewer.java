package de.pumpecraft.chatcontrol;

import java.util.concurrent.CompletableFuture;

/** Prüfer, der nichts prüft: ohne PumpeAI oder ohne Schlüssel gilt allein der Wortfilter. */
final class InactiveReviewer implements ChatReviewer {
    static final InactiveReviewer INSTANCE = new InactiveReviewer();

    private InactiveReviewer() {
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public FilterResult inspect(String message) {
        return FilterResult.allow();
    }

    @Override
    public CompletableFuture<FilterResult> review(String message) {
        return CompletableFuture.completedFuture(FilterResult.allow());
    }
}
