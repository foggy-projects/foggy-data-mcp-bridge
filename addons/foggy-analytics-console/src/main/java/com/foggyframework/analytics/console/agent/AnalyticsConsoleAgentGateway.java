package com.foggyframework.analytics.console.agent;

import java.time.Instant;
import java.util.List;

public interface AnalyticsConsoleAgentGateway {

    Accepted start(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            StartCommand command);

    default Accepted continueConversation(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            ContinueCommand command) {
        throw new UnsupportedOperationException("FAP conversation continuation is unavailable");
    }

    List<Turn> turns(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String requestId,
            String externalConversationRef);

    default TurnDetail turnDetail(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String askRequestId,
            String expectedAskInvocationRef,
            String expectedExternalConversationRef) {
        throw new UnsupportedOperationException("FAP Ask trace is unavailable");
    }

    record StartCommand(
            String requestId,
            String externalConversationRef,
            String prompt,
            String initialSystemInstruction,
            String workspaceRef,
            String modelConfigRef,
            String modelVariantId,
            String skillName,
            String capabilityName) {
    }

    record Accepted(
            String askInvocationRef,
            String runtimeExecutionId,
            String runtimeTaskId) {
    }

    record ContinueCommand(
            String requestId,
            String externalConversationRef,
            String runtimeExecutionId,
            String prompt,
            String modelVariantId,
            String skillName,
            String capabilityName) {
    }

    record Turn(
            String askInvocationRef,
            String operation,
            String displayState,
            boolean definitiveTerminal,
            String userMessage,
            String assistantMessage,
            String failureCode,
            Instant startedAt,
            Instant updatedAt,
            long durationMs) {

        public Turn(
                String askInvocationRef,
                String displayState,
                boolean definitiveTerminal,
                String assistantMessage,
                String failureCode) {
            this(
                    askInvocationRef,
                    "START",
                    displayState,
                    definitiveTerminal,
                    null,
                    assistantMessage,
                    failureCode,
                    null,
                    null,
                    0L);
        }
    }

    record TurnDetail(
            String askInvocationRef,
            String historyState,
            boolean eventsTruncated,
            List<AgentActivity> agentActivities,
            List<ToolCall> toolCalls) {

        public TurnDetail {
            agentActivities = agentActivities == null
                    ? List.of() : List.copyOf(agentActivities);
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    record AgentActivity(
            long sequence,
            String label,
            String state,
            Instant occurredAt,
            String errorCode) {
    }

    record ToolCall(
            long sequence,
            String functionRef,
            String state,
            Instant startedAt,
            Instant completedAt,
            Long durationMs,
            String errorCode) {
    }
}
