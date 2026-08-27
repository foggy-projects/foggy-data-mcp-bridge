package com.foggyframework.analytics.console.agent;

import java.time.Instant;
import java.util.List;

/** Write-ahead journal for Ask submissions that may outlive catalog persistence. */
public interface AnalyticsConsoleAskRecoveryRepository {

    void record(Entry entry);

    List<Entry> unresolved();

    static AnalyticsConsoleAskRecoveryRepository none() {
        return new AnalyticsConsoleAskRecoveryRepository() {
            @Override
            public void record(Entry entry) {
            }

            @Override
            public List<Entry> unresolved() {
                return List.of();
            }
        };
    }

    enum State {
        PREPARED,
        ACCEPTED,
        CATALOGED
    }

    record Entry(
            String operationId,
            String conversationId,
            String askRequestId,
            String externalConversationRef,
            State state,
            String askInvocationRef,
            String runtimeExecutionId,
            String runtimeTaskId,
            Instant recordedAt) {
    }
}
