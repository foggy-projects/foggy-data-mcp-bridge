package com.foggyframework.runtime.api.service;

import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;

final class TestFileSystemSupport {

    private TestFileSystemSupport() {
    }

    static Path createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (Exception failure) {
            Assumptions.assumeTrue(false,
                    "Symbolic links are unavailable: " + failure.getMessage());
            throw new AssertionError("unreachable", failure);
        }
    }
}
