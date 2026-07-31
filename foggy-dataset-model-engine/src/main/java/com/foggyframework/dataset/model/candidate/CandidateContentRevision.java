package com.foggyframework.dataset.model.candidate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Canonical content identity shared by candidate execution and Runtime workspaces. */
public final class CandidateContentRevision {

    private static final List<String> SOURCE_SUFFIXES =
            List.of(".tm", ".qm", ".fsscript");

    private CandidateContentRevision() {
    }

    public static boolean isCandidateResource(Path path) {
        String filename = path == null || path.getFileName() == null
                ? ""
                : path.getFileName().toString();
        return SOURCE_SUFFIXES.stream().anyMatch(filename::endsWith);
    }

    public static String calculate(Path root, List<Path> files) throws IOException {
        MessageDigest digest = sha256();
        List<Path> ordered = new ArrayList<>(files == null ? List.of() : files);
        ordered.sort(Comparator.comparing(path -> relative(root, path)));
        byte[] buffer = new byte[8192];
        for (Path file : ordered) {
            byte[] relative = relative(root, file).getBytes(StandardCharsets.UTF_8);
            updatePath(digest, relative);
            long size = Files.size(file);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(size).array());
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
        }
        return revision(digest);
    }

    public static String calculate(Map<String, byte[]> resources) {
        MessageDigest digest = sha256();
        if (resources != null) {
            resources.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        byte[] path = entry.getKey().getBytes(StandardCharsets.UTF_8);
                        byte[] content = entry.getValue();
                        updatePath(digest, path);
                        digest.update(ByteBuffer.allocate(Long.BYTES)
                                .putLong(content.length).array());
                        digest.update(content);
                    });
        }
        return revision(digest);
    }

    private static void updatePath(MessageDigest digest, byte[] relative) {
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(relative.length).array());
        digest.update(relative);
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String revision(MessageDigest digest) {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }
}
