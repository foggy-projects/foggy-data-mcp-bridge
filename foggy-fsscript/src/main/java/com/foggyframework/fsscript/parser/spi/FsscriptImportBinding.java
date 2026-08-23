package com.foggyframework.fsscript.parser.spi;

import java.util.Objects;

/** One logical import location resolved to the exact compiled child script. */
public record FsscriptImportBinding(String location, Fsscript fsscript) {

    public FsscriptImportBinding {
        Objects.requireNonNull(location, "location");
        if (location.isBlank()) {
            throw new IllegalArgumentException("import location must be non-blank");
        }
        fsscript = Objects.requireNonNull(fsscript, "fsscript");
    }
}
