package com.foggyframework.fsscript.parser.spi;

import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public interface Fsscript {

    Object eval(ExpEvaluator ee);

    default Object evalResult(ExpEvaluator ee) {
        Object t = eval(ee);
        if (t instanceof Exp.ReturnExpObject) {
            return ((Exp.ReturnExpObject) t).value;
        }
        return t;
    }

//    ExpEvaluator eval(ApplicationContext appCtx);

    default ExpEvaluator eval(ApplicationContext appCtx) {
        ExpEvaluator ee = newInstance(appCtx);
        eval(ee);
        return ee;
    }

    default <T> T evalResult(ApplicationContext appCtx, Map<String, Object> args) {
        ExpEvaluator ee = newInstance(appCtx);
        if (args != null) {
            ee.setMap2Var(args);
        }

        return (T) evalResult(ee);
    }

    FsscriptClosureDefinition getFsscriptClosureDefinition();

    String getPath();

    /**
     * Canonical digest of the exact normalized source text compiled into this
     * script. Dynamic/script-engine instances may return empty.
     */
    default Optional<String> getSourceContentRevision() {
        return Optional.empty();
    }

    /** Logical direct-import bindings resolved while this script was evaluated. */
    default List<FsscriptImportBinding> getDirectImportBindings() {
        return List.of();
    }

    /** Compatibility view of direct imported script objects. */
    default Set<Fsscript> getDirectImports() {
        LinkedHashSet<Fsscript> imports = new LinkedHashSet<>();
        getDirectImportBindings().forEach(binding -> imports.add(binding.fsscript()));
        return Set.copyOf(imports);
    }

    ExpEvaluator newInstance(ApplicationContext appCtx);

    boolean hasImport(Fsscript fscript);

}
