package com.foggyframework.dataset.mcp.experience;

import java.util.Locale;

public class ExperienceRecipeGovernanceEvidence {
    private String ownerSignoffStatus = "pending";
    private String schemaValidationStatus = "pending";
    private String validationEvidenceStatus = "pending";
    private String positiveNegativeExamplesStatus = "pending";
    private String permissionScopeStatus = "pending";

    public static ExperienceRecipeGovernanceEvidence passed() {
        ExperienceRecipeGovernanceEvidence evidence = new ExperienceRecipeGovernanceEvidence();
        evidence.ownerSignoffStatus = "passed";
        evidence.schemaValidationStatus = "passed";
        evidence.validationEvidenceStatus = "passed";
        evidence.positiveNegativeExamplesStatus = "passed";
        evidence.permissionScopeStatus = "passed";
        return evidence;
    }

    public boolean publishGatePassed() {
        return isPassed(ownerSignoffStatus)
                && isPassed(schemaValidationStatus)
                && isPassed(validationEvidenceStatus)
                && isPassed(positiveNegativeExamplesStatus)
                && isPassed(permissionScopeStatus);
    }

    private static boolean isPassed(String status) {
        return status != null && "passed".equals(status.trim().toLowerCase(Locale.ROOT));
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
}
