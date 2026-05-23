package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class MemoryGridPolicySupport {

    private MemoryGridPolicySupport() {
    }

    static String ownerContextHash(SemanticRequestContext context) {
        String seed = context == null ? "" : String.valueOf(context.getNamespace()) + "|"
                + String.valueOf(context.getAuthorization());
        return sha256(seed);
    }

    static String fieldAccessHash(SemanticRequestContext context) {
        Set<String> fieldAccess = context == null ? null : context.getFieldAccess();
        if (fieldAccess == null) {
            return null;
        }
        return sha256(fieldAccess.stream().sorted().collect(Collectors.joining("|")));
    }

    static String schemaHash(Map<String, MemoryGridResultResolver.Column> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        String seed = schema.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + ":"
                        + entry.getValue().type() + ":"
                        + entry.getValue().joinAllowed() + ":"
                        + entry.getValue().derivedAllowed() + ":"
                        + entry.getValue().outputAllowed() + ":"
                        + entry.getValue().sensitive())
                .collect(Collectors.joining("|"));
        return sha256(seed);
    }

    private static String sha256(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": SHA-256 is unavailable.", e);
        }
    }
}
