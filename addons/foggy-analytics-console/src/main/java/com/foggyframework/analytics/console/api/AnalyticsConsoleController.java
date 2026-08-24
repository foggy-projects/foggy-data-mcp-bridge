package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleFolder;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/analytics-console/api/v1")
public class AnalyticsConsoleController {

    private final AnalyticsConsoleService service;
    private final AnalyticsConsoleSubjectResolver subjects;

    public AnalyticsConsoleController(
            AnalyticsConsoleService service,
            AnalyticsConsoleSubjectResolver subjects) {
        this.service = service;
        this.subjects = subjects;
    }

    @GetMapping("/session")
    public AnalyticsConsoleEnvelope<Session> session(HttpServletRequest request) {
        AnalyticsConsoleSubject subject = subject(request);
        return ok(new Session(
                subject.subjectRef(),
                subject.displayName(),
                subject.roles().stream().map(Enum::name).sorted().toList()));
    }

    @GetMapping("/folders")
    public AnalyticsConsoleEnvelope<List<AnalyticsConsoleFolder>> folders(
            HttpServletRequest request) {
        return ok(service.folders(subject(request)));
    }

    @PostMapping("/folders")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleFolder> createFolder(
            @Valid @RequestBody AnalyticsConsoleRequests.CreateFolder body,
            HttpServletRequest request) {
        return ok(service.createFolder(
                subject(request), body.name(), body.parentFolderId()));
    }

    @GetMapping("/assets")
    public AnalyticsConsoleEnvelope<List<AnalyticsConsoleAsset>> assets(
            HttpServletRequest request) {
        return ok(service.assets(subject(request)));
    }

    @GetMapping("/assets/{assetId}")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleService.AssetDetail> asset(
            @PathVariable String assetId,
            HttpServletRequest request) {
        return ok(service.asset(subject(request), assetId));
    }

    @PostMapping("/assets/drafts")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleAsset> createDraft(
            @Valid @RequestBody AnalyticsConsoleRequests.CreateDraft body,
            HttpServletRequest request) {
        return ok(service.createDraft(
                subject(request),
                new AnalyticsConsoleService.CreateDraft(
                        body.title(),
                        body.description(),
                        body.folderId(),
                        body.kind(),
                        body.bundleRef(),
                        body.artifactRef(),
                        body.expectedBundleRevision(),
                        body.definitionContent())));
    }

    @PutMapping("/assets/{assetId}/definition")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleAsset> saveDefinition(
            @PathVariable String assetId,
            @Valid @RequestBody AnalyticsConsoleRequests.SaveDefinition body,
            HttpServletRequest request) {
        return ok(service.saveDefinition(
                subject(request),
                assetId,
                body.expectedBundleRevision(),
                body.definitionContent()));
    }

    @PostMapping("/assets/{assetId}:validate")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleAsset> validate(
            @PathVariable String assetId,
            @Valid @RequestBody AnalyticsConsoleRequests.ExactRevision body,
            HttpServletRequest request) {
        return ok(service.validate(
                subject(request), assetId, body.expectedBundleRevision()));
    }

    @PostMapping("/assets/{assetId}:preview")
    public AnalyticsConsoleEnvelope<AnalyticsRenderResult> preview(
            @PathVariable String assetId,
            @Valid @RequestBody AnalyticsConsoleRequests.Preview body,
            HttpServletRequest request) {
        return ok(service.preview(
                subject(request),
                assetId,
                body.expectedBundleRevision(),
                body.parameters() == null ? Map.of() : body.parameters(),
                body.timezone(),
                body.locale()));
    }

    @PostMapping("/assets/{assetId}:publish")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleAsset> publish(
            @PathVariable String assetId,
            @Valid @RequestBody AnalyticsConsoleRequests.ExactRevision body,
            HttpServletRequest request) {
        return ok(service.publish(
                subject(request), assetId, body.expectedBundleRevision()));
    }

    @PutMapping("/assets/{assetId}/audience")
    public AnalyticsConsoleEnvelope<AnalyticsConsoleAsset> updateAudience(
            @PathVariable String assetId,
            @Valid @RequestBody AnalyticsConsoleRequests.UpdateAudience body,
            HttpServletRequest request) {
        return ok(service.updateAudience(
                subject(request),
                assetId,
                body.visibility(),
                body.viewerSubjectRefs() == null ? Set.of() : body.viewerSubjectRefs()));
    }

    private AnalyticsConsoleSubject subject(HttpServletRequest request) {
        return subjects.resolve(request);
    }

    private static <T> AnalyticsConsoleEnvelope<T> ok(T value) {
        return AnalyticsConsoleEnvelope.ok(value, "console-" + UUID.randomUUID());
    }

    public record Session(String subjectRef, String displayName, List<String> roles) {
    }
}
