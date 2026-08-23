package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.fsscript.exp.ImportFsscriptExp;
import com.foggyframework.fsscript.exp.NCountExp;
import com.foggyframework.fsscript.support.FsscriptImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FsscriptSourceClosureRevisionTest {

    @Test
    void capturesExactCompiledSourceAndResolvedImportClosure() {
        Fsscript first = root("root source", "child source");
        Fsscript same = root("root source", "child source");
        Fsscript changedImport = root("root source", "changed child source");

        assertEquals(
                FsscriptSourceClosureRevision.calculate(first),
                FsscriptSourceClosureRevision.calculate(same));
        assertNotEquals(
                FsscriptSourceClosureRevision.calculate(first),
                FsscriptSourceClosureRevision.calculate(changedImport));
        assertTrue(FsscriptSourceContentRevision.isCanonical(
                first.getSourceContentRevision().orElseThrow()));
    }

    @Test
    void importLocationToChildMappingParticipatesInClosureIdentity() {
        Fsscript first = rootWithTwoImports("left source", "right source", false);
        Fsscript swapped = rootWithTwoImports("left source", "right source", true);

        assertNotEquals(
                FsscriptSourceClosureRevision.calculate(first),
                FsscriptSourceClosureRevision.calculate(swapped));
    }

    @Test
    void failsClosedForDynamicScriptWithoutCompiledSourceEvidence() {
        Fsscript dynamic = new FsscriptImpl(
                null,
                new NCountExp(List.of()));

        assertTrue(FsscriptSourceClosureRevision.calculate(dynamic).isEmpty());
    }

    private static Fsscript root(String rootSource, String childSource) {
        Fsscript child = new FsscriptImpl(
                null,
                new NCountExp(List.of()),
                childSource);
        ImportFsscriptExp imported = new ImportFsscriptExp("./child.fsscript");
        imported.setFsscript(child);
        return new FsscriptImpl(
                null,
                new NCountExp(List.of(imported)),
                rootSource);
    }

    private static Fsscript rootWithTwoImports(
            String leftSource,
            String rightSource,
            boolean swapBindings) {
        Fsscript left = new FsscriptImpl(
                null,
                new NCountExp(List.of()),
                leftSource);
        Fsscript right = new FsscriptImpl(
                null,
                new NCountExp(List.of()),
                rightSource);
        ImportFsscriptExp first = new ImportFsscriptExp("./left.fsscript");
        first.setFsscript(swapBindings ? right : left);
        ImportFsscriptExp second = new ImportFsscriptExp("./right.fsscript");
        second.setFsscript(swapBindings ? left : right);
        return new FsscriptImpl(
                null,
                new NCountExp(List.of(first, second)),
                "import left; import right;");
    }
}
