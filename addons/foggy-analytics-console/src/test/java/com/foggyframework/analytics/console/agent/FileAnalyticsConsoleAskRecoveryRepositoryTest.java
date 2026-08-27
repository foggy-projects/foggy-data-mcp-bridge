package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FileAnalyticsConsoleAskRecoveryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesOnlyLatestUncatalogedAskOperations() {
        FileAnalyticsConsoleAskRecoveryRepository repository =
                new FileAnalyticsConsoleAskRecoveryRepository(
                        tempDir.resolve("ask-recovery.jsonl"), new ObjectMapper());

        repository.record(entry("op-1", AnalyticsConsoleAskRecoveryRepository.State.PREPARED));
        repository.record(entry("op-2", AnalyticsConsoleAskRecoveryRepository.State.ACCEPTED));
        repository.record(entry("op-1", AnalyticsConsoleAskRecoveryRepository.State.CATALOGED));

        assertThat(repository.unresolved())
                .extracting(AnalyticsConsoleAskRecoveryRepository.Entry::operationId)
                .containsExactly("op-2");
    }

    private static AnalyticsConsoleAskRecoveryRepository.Entry entry(
            String operationId, AnalyticsConsoleAskRecoveryRepository.State state) {
        return new AnalyticsConsoleAskRecoveryRepository.Entry(
                operationId,
                "conversation-1",
                "ask-1",
                "external-1",
                state,
                "invocation-1",
                "execution-1",
                "task-1",
                Instant.parse("2026-08-27T00:00:00Z"));
    }
}
