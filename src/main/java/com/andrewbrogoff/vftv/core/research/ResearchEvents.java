package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.ResearchLearnedPayload;
import com.andrewbrogoff.vftv.api.research.StageReachedPayload;
import java.util.function.Consumer;

/**
 * Where the resolution reports what it committed: the platform installs publishers that post {@code
 * ResearchLearnedEvent} and {@code StageReachedEvent}, which {@code core} never sees. A publisher
 * logs a throwing listener and returns; the state stands.
 */
public final class ResearchEvents {
    private static volatile Consumer<ResearchLearnedPayload> learned = p -> {};
    private static volatile Consumer<StageReachedPayload> staged = p -> {};

    private ResearchEvents() {}

    /** Installs the publishers; called once from the platform's wiring. */
    public static void install(
            Consumer<ResearchLearnedPayload> onLearned, Consumer<StageReachedPayload> onStaged) {
        learned = onLearned;
        staged = onStaged;
    }

    static void learned(ResearchLearnedPayload payload) {
        learned.accept(payload);
    }

    static void staged(StageReachedPayload payload) {
        staged.accept(payload);
    }
}
