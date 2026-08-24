package com.foggyframework.analytics.console.agent;

import java.util.List;

public interface AnalyticsConsoleAgentGateway {

    Accepted start(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            StartCommand command);

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

    record Turn(
            String askInvocationRef,
            String displayState,
            boolean definitiveTerminal,
            String assistantMessage,
            String failureCode) {
    }
}
