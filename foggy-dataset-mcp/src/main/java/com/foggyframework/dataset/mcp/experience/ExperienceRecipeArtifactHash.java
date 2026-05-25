package com.foggyframework.dataset.mcp.experience;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ExperienceRecipeArtifactHash {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ExperienceRecipeArtifactHash() {
    }

    static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            char[] hex = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                hex[i * 2] = HEX[value >>> 4];
                hex[i * 2 + 1] = HEX[value & 0x0f];
            }
            return "sha256:" + new String(hex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }
}
