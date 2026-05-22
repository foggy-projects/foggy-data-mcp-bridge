package com.foggyframework.dataset.mcp.experience;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

public final class Ed25519ExperienceRecipeArtifactSignatureVerifier
        implements ExperienceRecipeArtifactSignatureVerifier {
    private static final String ALGORITHM = "ed25519";
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);

    private final ExperienceRecipeArtifactTrustStore trustStore;
    private final Clock clock;

    public Ed25519ExperienceRecipeArtifactSignatureVerifier(ExperienceRecipeArtifactTrustStore trustStore) {
        this(trustStore, Clock.systemUTC());
    }

    public Ed25519ExperienceRecipeArtifactSignatureVerifier(
            ExperienceRecipeArtifactTrustStore trustStore,
            Clock clock) {
        if (trustStore == null) {
            throw new IllegalArgumentException("trustStore cannot be null");
        }
        this.trustStore = trustStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ExperienceRecipeArtifactVerificationResult verify(
            ExperienceRecipeEvidenceArtifact artifact,
            byte[] content,
            ExperienceRecipeArtifactSignatureContext context) {
        if (artifact == null) {
            return ExperienceRecipeArtifactVerificationResult.failed("evidence artifact cannot be null");
        }
        if (content == null) {
            return ExperienceRecipeArtifactVerificationResult.failed("artifact content cannot be null");
        }
        String actualHash = ExperienceRecipeArtifactHash.sha256(content);
        if (!actualHash.equalsIgnoreCase(trimToEmpty(artifact.getArtifactHash()))) {
            return ExperienceRecipeArtifactVerificationResult.failed("artifact hash mismatch for "
                    + artifact.getArtifactUri());
        }

        SignatureEnvelope envelope = parseSignature(artifact.getArtifactSignature());
        if (envelope.error() != null) {
            return ExperienceRecipeArtifactVerificationResult.failed(envelope.error());
        }
        if (!ALGORITHM.equals(envelope.algorithm())) {
            return ExperienceRecipeArtifactVerificationResult.failed(
                    "unsupported signature algorithm: " + envelope.algorithm());
        }

        ExperienceRecipeArtifactTrustKey key = trustStore.findByKeyId(envelope.keyId()).orElse(null);
        if (key == null) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key not found: " + envelope.keyId());
        }

        ExperienceRecipeArtifactVerificationResult trustResult = verifyTrustKey(key, artifact, context);
        if (!trustResult.verified()) {
            return trustResult;
        }

        try {
            byte[] signatureBytes = Base64.getUrlDecoder().decode(envelope.signature());
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(key.publicKey()));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(ExperienceRecipeArtifactSignaturePayload.canonicalBytes(context, artifact));
            if (!verifier.verify(signatureBytes)) {
                return ExperienceRecipeArtifactVerificationResult.failed("invalid Ed25519 signature");
            }
            return ExperienceRecipeArtifactVerificationResult.passed();
        } catch (IllegalArgumentException ex) {
            return ExperienceRecipeArtifactVerificationResult.failed("invalid signature encoding");
        } catch (GeneralSecurityException ex) {
            return ExperienceRecipeArtifactVerificationResult.failed("signature verification error: "
                    + ex.getClass().getSimpleName());
        }
    }

    private ExperienceRecipeArtifactVerificationResult verifyTrustKey(
            ExperienceRecipeArtifactTrustKey key,
            ExperienceRecipeEvidenceArtifact artifact,
            ExperienceRecipeArtifactSignatureContext context) {
        if (!ALGORITHM.equals(normalize(key.algorithm()))) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key algorithm mismatch: " + key.keyId());
        }
        if (key.status() != ExperienceRecipeArtifactTrustKey.Status.ENABLED || key.revokedAt() != null) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key is not enabled: " + key.keyId());
        }
        Instant signedAt = parseSignedAt(artifact.getSignedAt());
        if (signedAt == null) {
            return ExperienceRecipeArtifactVerificationResult.failed("invalid signedAt");
        }
        if (signedAt.isAfter(clock.instant().plus(FUTURE_SKEW))) {
            return ExperienceRecipeArtifactVerificationResult.failed("signedAt is in the future");
        }
        if (key.validFrom() != null && signedAt.isBefore(key.validFrom())) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key is not yet valid: " + key.keyId());
        }
        if (key.validTo() != null && signedAt.isAfter(key.validTo())) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key is expired: " + key.keyId());
        }
        if (!allows(key.purposes(), ExperienceRecipeArtifactSignaturePayload.PURPOSE)) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key purpose mismatch: " + key.keyId());
        }
        if (context == null) {
            return ExperienceRecipeArtifactVerificationResult.failed("signature context cannot be null");
        }
        if (!allows(key.tenantIds(), context.tenantId())) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key tenant mismatch: " + key.keyId());
        }
        if (!allows(key.ownerIds(), context.ownerId())) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key owner mismatch: " + key.keyId());
        }
        if (!allows(key.artifactTypes(), artifact.getArtifactType())) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key artifact type mismatch: "
                    + key.keyId());
        }
        if (!allows(key.signedBySubjects(), artifact.getSignedBy())) {
            return ExperienceRecipeArtifactVerificationResult.failed("trust key signedBy mismatch: " + key.keyId());
        }
        return ExperienceRecipeArtifactVerificationResult.passed();
    }

    private static SignatureEnvelope parseSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            return SignatureEnvelope.error("artifact signature cannot be blank");
        }
        String[] parts = signature.trim().split(":", 5);
        if (parts.length != 5 || !"sig".equals(parts[0]) || !"v1".equals(parts[1])) {
            return SignatureEnvelope.error("artifact signature must use sig:v1:<algorithm>:<keyId>:<signature>");
        }
        if (parts[2].isBlank() || parts[3].isBlank() || parts[4].isBlank()) {
            return SignatureEnvelope.error("artifact signature envelope cannot contain blank parts");
        }
        return new SignatureEnvelope(normalize(parts[2]), parts[3].trim(), parts[4].trim(), null);
    }

    private static Instant parseSignedAt(String signedAt) {
        if (signedAt == null || signedAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(signedAt.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean allows(Set<String> allowed, String actual) {
        return allowed != null && allowed.contains(normalize(actual));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record SignatureEnvelope(String algorithm, String keyId, String signature, String error) {
        static SignatureEnvelope error(String error) {
            return new SignatureEnvelope(null, null, null, error);
        }
    }
}
