package com.foggyframework.dataset.mcp.experience;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ExperienceRecipeGovernanceEvidence {
    public static final String OWNER_SIGNOFF_ARTIFACT = "owner_signoff";
    public static final String SCHEMA_VALIDATION_ARTIFACT = "schema_validation";
    public static final String VALIDATION_REPORT_ARTIFACT = "validation_report";
    public static final String POSITIVE_NEGATIVE_EXAMPLES_ARTIFACT = "positive_negative_examples";
    public static final String PERMISSION_SCOPE_ARTIFACT = "permission_scope";

    private static final Set<String> REQUIRED_PUBLISH_ARTIFACT_TYPES = Set.of(
            OWNER_SIGNOFF_ARTIFACT,
            SCHEMA_VALIDATION_ARTIFACT,
            VALIDATION_REPORT_ARTIFACT,
            POSITIVE_NEGATIVE_EXAMPLES_ARTIFACT,
            PERMISSION_SCOPE_ARTIFACT);

    private String ownerSignoffStatus = "pending";
    private String schemaValidationStatus = "pending";
    private String validationEvidenceStatus = "pending";
    private String positiveNegativeExamplesStatus = "pending";
    private String permissionScopeStatus = "pending";
    private List<ExperienceRecipeEvidenceArtifact> evidenceArtifacts = new ArrayList<>();

    public static ExperienceRecipeGovernanceEvidence passed() {
        ExperienceRecipeGovernanceEvidence evidence = new ExperienceRecipeGovernanceEvidence();
        evidence.ownerSignoffStatus = "passed";
        evidence.schemaValidationStatus = "passed";
        evidence.validationEvidenceStatus = "passed";
        evidence.positiveNegativeExamplesStatus = "passed";
        evidence.permissionScopeStatus = "passed";
        evidence.setEvidenceArtifacts(defaultPassedArtifacts());
        return evidence;
    }

    public boolean publishGatePassed() {
        return isPassed(ownerSignoffStatus)
                && isPassed(schemaValidationStatus)
                && isPassed(validationEvidenceStatus)
                && isPassed(positiveNegativeExamplesStatus)
                && isPassed(permissionScopeStatus)
                && requiredArtifactsPresent();
    }

    public boolean requiredArtifactsPresent() {
        Set<String> present = new LinkedHashSet<>();
        for (ExperienceRecipeEvidenceArtifact artifact : evidenceArtifacts) {
            if (artifact != null && artifact.validForPublishGate()) {
                present.add(artifact.normalizedType());
            }
        }
        return present.containsAll(REQUIRED_PUBLISH_ARTIFACT_TYPES);
    }

    private static boolean isPassed(String status) {
        return status != null && "passed".equals(status.trim().toLowerCase(Locale.ROOT));
    }

    private static List<ExperienceRecipeEvidenceArtifact> defaultPassedArtifacts() {
        return List.of(
                ExperienceRecipeEvidenceArtifact.of(
                        OWNER_SIGNOFF_ARTIFACT,
                        "foggy://experience-recipes/evidence/owner-signoff",
                        fixedSha256('1'),
                        "registry_admin"),
                ExperienceRecipeEvidenceArtifact.of(
                        SCHEMA_VALIDATION_ARTIFACT,
                        "foggy://experience-recipes/evidence/schema-validation",
                        fixedSha256('2'),
                        "registry_admin"),
                ExperienceRecipeEvidenceArtifact.of(
                        VALIDATION_REPORT_ARTIFACT,
                        "foggy://experience-recipes/evidence/validation-report",
                        fixedSha256('3'),
                        "registry_admin"),
                ExperienceRecipeEvidenceArtifact.of(
                        POSITIVE_NEGATIVE_EXAMPLES_ARTIFACT,
                        "foggy://experience-recipes/evidence/positive-negative-examples",
                        fixedSha256('4'),
                        "registry_admin"),
                ExperienceRecipeEvidenceArtifact.of(
                        PERMISSION_SCOPE_ARTIFACT,
                        "foggy://experience-recipes/evidence/permission-scope",
                        fixedSha256('5'),
                        "registry_admin"));
    }

    private static String fixedSha256(char hexDigit) {
        return "sha256:" + String.valueOf(hexDigit).repeat(64);
    }

    public String getOwnerSignoffStatus() {
        return ownerSignoffStatus;
    }

    public void setOwnerSignoffStatus(String ownerSignoffStatus) {
        this.ownerSignoffStatus = ownerSignoffStatus;
    }

    public String getSchemaValidationStatus() {
        return schemaValidationStatus;
    }

    public void setSchemaValidationStatus(String schemaValidationStatus) {
        this.schemaValidationStatus = schemaValidationStatus;
    }

    public String getValidationEvidenceStatus() {
        return validationEvidenceStatus;
    }

    public void setValidationEvidenceStatus(String validationEvidenceStatus) {
        this.validationEvidenceStatus = validationEvidenceStatus;
    }

    public String getPositiveNegativeExamplesStatus() {
        return positiveNegativeExamplesStatus;
    }

    public void setPositiveNegativeExamplesStatus(String positiveNegativeExamplesStatus) {
        this.positiveNegativeExamplesStatus = positiveNegativeExamplesStatus;
    }

    public String getPermissionScopeStatus() {
        return permissionScopeStatus;
    }

    public void setPermissionScopeStatus(String permissionScopeStatus) {
        this.permissionScopeStatus = permissionScopeStatus;
    }

    public List<ExperienceRecipeEvidenceArtifact> getEvidenceArtifacts() {
        return evidenceArtifacts.stream()
                .map(ExperienceRecipeEvidenceArtifact::copy)
                .toList();
    }

    public void setEvidenceArtifacts(List<ExperienceRecipeEvidenceArtifact> evidenceArtifacts) {
        if (evidenceArtifacts == null) {
            this.evidenceArtifacts = new ArrayList<>();
            return;
        }
        this.evidenceArtifacts = evidenceArtifacts.stream()
                .filter(artifact -> artifact != null)
                .map(ExperienceRecipeEvidenceArtifact::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
