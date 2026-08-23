package com.foggyframework.fsscript.parser.spi;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical digest of one compiled FSScript and its resolved import closure. */
public final class FsscriptSourceClosureRevision {

    private static final int MAX_SOURCE_COUNT = 1_024;

    private FsscriptSourceClosureRevision() {
    }

    public static Optional<String> calculate(Fsscript root) {
        if (root == null) {
            return Optional.empty();
        }
        Map<Fsscript, String> memo = new IdentityHashMap<>();
        Set<Fsscript> active = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        int[] sourceCount = {0};
        return Optional.ofNullable(calculate(root, memo, active, sourceCount));
    }

    private static String calculate(
            Fsscript script,
            Map<Fsscript, String> memo,
            Set<Fsscript> active,
            int[] sourceCount) {
        String cached = memo.get(script);
        if (cached != null) {
            return cached;
        }
        if (!active.add(script) || ++sourceCount[0] > MAX_SOURCE_COUNT) {
            return null;
        }
        try {
            String contentRevision = script.getSourceContentRevision()
                    .filter(FsscriptSourceContentRevision::isCanonical)
                    .orElse(null);
            if (contentRevision == null) {
                return null;
            }
            List<ImportRevision> imports = new ArrayList<>();
            List<FsscriptImportBinding> bindings = script.getDirectImportBindings();
            if (bindings == null) {
                return null;
            }
            for (FsscriptImportBinding binding : bindings) {
                if (binding == null) {
                    return null;
                }
                String childRevision = calculate(
                        binding.fsscript(),
                        memo,
                        active,
                        sourceCount);
                if (childRevision == null) {
                    return null;
                }
                imports.add(new ImportRevision(binding.location(), childRevision));
            }
            imports.sort(java.util.Comparator
                    .comparing(ImportRevision::location)
                    .thenComparing(ImportRevision::closureRevision));
            StringBuilder canonical = new StringBuilder();
            append(canonical, "source", contentRevision);
            for (ImportRevision imported : imports) {
                append(canonical, "location", imported.location());
                append(canonical, "closure", imported.closureRevision());
            }
            String revision = FsscriptSourceContentRevision.calculate(
                    canonical.toString());
            memo.put(script, revision);
            return revision;
        } finally {
            active.remove(script);
        }
    }

    private static void append(StringBuilder target, String field, String value) {
        target.append(field.length())
                .append(':')
                .append(field)
                .append('=')
                .append(value.length())
                .append(':')
                .append(value)
                .append('\n');
    }

    private record ImportRevision(String location, String closureRevision) {
    }
}
