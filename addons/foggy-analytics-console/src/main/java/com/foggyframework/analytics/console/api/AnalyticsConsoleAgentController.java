package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentGateway;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentService;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversation;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/analytics-console/api/v1")
public class AnalyticsConsoleAgentController {

    private final AnalyticsConsoleAgentService agents;
    private final AnalyticsConsoleSubjectResolver subjects;

    public AnalyticsConsoleAgentController(
            AnalyticsConsoleAgentService agents,
            AnalyticsConsoleSubjectResolver subjects) {
        this.agents = agents;
        this.subjects = subjects;
    }

    @PostMapping("/assets/{assetId}/agent/asks")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleConversation> start(
            @PathVariable String assetId,
            @Valid @RequestBody StartAsk body,
            HttpServletRequest request) {
        return ok(agents.startDesign(subjects.resolve(request), assetId, body.prompt()));
    }

    @GetMapping("/agent/question-profiles")
    public AnalyticsConsoleEnvelope<List<AnalyticsConsoleAgentService.QuestionProfile>>
            questionProfiles(HttpServletRequest request) {
        return ok(agents.questionProfiles(subjects.resolve(request)));
    }

    @PostMapping("/agent/questions")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleConversation> startQuestion(
            @Valid @RequestBody StartQuestion body,
            HttpServletRequest request) {
        return ok(agents.startQuestion(
                subjects.resolve(request), body.profileId(), body.prompt()));
    }

    @PostMapping("/agent/conversations/{conversationId}/turns")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleConversation> continueConversation(
            @PathVariable String conversationId,
            @Valid @RequestBody ContinueAsk body,
            HttpServletRequest request) {
        return ok(agents.continueConversation(
                subjects.resolve(request), conversationId, body.prompt()));
    }

    @GetMapping("/agent/conversations/{conversationId}/turns")
    public AnalyticsConsoleEnvelope<List<AnalyticsConsoleAgentGateway.Turn>> turns(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        return ok(agents.turns(subjects.resolve(request), conversationId));
    }

    private static <T> AnalyticsConsoleEnvelope<T> ok(T data) {
        return AnalyticsConsoleEnvelope.ok(data, "console-" + UUID.randomUUID());
    }

    public record StartAsk(@NotBlank String prompt) {
    }

    public record StartQuestion(@NotBlank String profileId, @NotBlank String prompt) {
    }

    public record ContinueAsk(@NotBlank String prompt) {
    }
}
