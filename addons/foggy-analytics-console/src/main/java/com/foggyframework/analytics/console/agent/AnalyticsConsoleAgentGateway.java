package com.foggyframework.analytics.console.agent;

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
            String failureCode) {

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
                    failureCode);
        }
    }
}
