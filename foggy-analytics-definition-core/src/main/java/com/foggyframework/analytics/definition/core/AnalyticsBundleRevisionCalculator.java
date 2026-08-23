package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Canonical v1 content revision for an Analytics Bundle directory. */
public final class AnalyticsBundleRevisionCalculator {

    public static final String MANIFEST_FILE = "manifest.json";
    private static final byte[] REVISION_SCHEME =
            "foggy-analytics-bundle-revision-v1\0".getBytes(StandardCharsets.UTF_8);

    private final AnalyticsBundleManifestJsonCodec manifestCodec;

    public AnalyticsBundleRevisionCalculator() {
        this(new AnalyticsBundleManifestJsonCodec());
    }

    public AnalyticsBundleRevisionCalculator(AnalyticsBundleManifestJsonCodec manifestCodec) {
        this.manifestCodec = Objects.requireNonNull(manifestCodec, "manifestCodec");
    }

    public AnalyticsBundleRevision calculate(Path bundleRoot) throws IOException {
        Path root = verifiedRoot(bundleRoot);
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream
                    .filter(path -> !path.equals(root))
                    .peek(this::rejectSymlink)
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .toList();
        }
        Path manifestPath = root.resolve(MANIFEST_FILE);
        if (!files.contains(manifestPath)) {
            throw new IOException("Analytics Bundle requires root manifest.json");
        }

        MessageDigest digest = sha256();
        digest.update(REVISION_SCHEME);
        for (Path file : files) {
            String relative = relative(root, file);
            byte[] pathBytes = relative.getBytes(StandardCharsets.UTF_8);
            byte[] content = canonicalContent(relative, file);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(pathBytes.length).array());
            digest.update(pathBytes);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(content.length).array());
            digest.update(content);
        }
        return new AnalyticsBundleRevision(
                "sha256:" + HexFormat.of().formatHex(digest.digest()));
    }

    private byte[] canonicalContent(String relative, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (MANIFEST_FILE.equals(relative)) {
            AnalyticsBundleManifest manifest = manifestCodec.read(bytes);
            return manifestCodec.stableRevisionProjection(manifest);
        }
        if (relative.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return canonicalJsonText(bytes);
        }
        return bytes;
    }

    private byte[] canonicalJsonText(byte[] bytes) throws CharacterCodingException {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            throw new IllegalArgumentException("Analytics JSON files must not contain a UTF-8 BOM");
        }
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .getBytes(StandardCharsets.UTF_8);
    }

    private Path verifiedRoot(Path bundleRoot) throws IOException {
        Objects.requireNonNull(bundleRoot, "bundleRoot");
        Path normalized = bundleRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Analytics Bundle root must be a real directory");
        }
        Path real = normalized.toRealPath();
        if (!normalized.equals(real)) {
            throw new IOException("Analytics Bundle root must not traverse symbolic links");
        }
        return real;
    }

    private void rejectSymlink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new UnsafeBundlePathException(
                    "Analytics Bundle must not contain symbolic links: " + path);
        }
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

    public static final class UnsafeBundlePathException extends RuntimeException {
        public UnsafeBundlePathException(String message) {
            super(message);
        }
    }
}
