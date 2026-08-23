package com.foggyframework.fsscript.parser.spi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identity of the exact normalized source text passed to the compiler. */
public final class FsscriptSourceContentRevision {

    private static final Pattern CANONICAL = Pattern.compile("sha256:[0-9a-f]{64}");

    private FsscriptSourceContentRevision() {
    }

    public static String calculate(String source) {
        Objects.requireNonNull(source, "source");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static boolean isCanonical(String revision) {
        return revision != null && CANONICAL.matcher(revision).matches();
    }
}
