package com.foggyframework.fsscript.support;

import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.ImportFsscriptExp;
import com.foggyframework.fsscript.exp.NCountExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.FsscriptImportBinding;
import com.foggyframework.fsscript.parser.spi.FsscriptSourceContentRevision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
@Slf4j
public class FsscriptImpl implements Fsscript {
    FsscriptClosureDefinition fsscriptClosureDefinition;

    Exp exp;

    private final String sourceContentRevision;

    public FsscriptClosureDefinition getFsscriptClosureDefinition() {
        return fsscriptClosureDefinition;
    }

    @Override
    public String getPath() {
        return fsscriptClosureDefinition.getFsscriptClosureDefinitionSpace().getPath();
    }

    @Override
    public Optional<String> getSourceContentRevision() {
        return Optional.ofNullable(sourceContentRevision);
    }

    @Override
    public List<FsscriptImportBinding> getDirectImportBindings() {
        List<FsscriptImportBinding> imports = new ArrayList<>();
        if (exp instanceof ImportFsscriptExp importExp) {
            addImport(imports, importExp);
        } else if (exp instanceof NCountExp expressions) {
            for (Exp expression : expressions.getValue()) {
                if (expression instanceof ImportFsscriptExp importExp) {
                    addImport(imports, importExp);
                }
            }
        }
        return List.copyOf(imports);
    }

    private static void addImport(
            List<FsscriptImportBinding> imports,
            ImportFsscriptExp importExpression) {
        if (importExpression.getFsscript() != null) {
            imports.add(new FsscriptImportBinding(
                    importExpression.getFile(),
                    importExpression.getFsscript()));
        }
    }

    @Override
    public ExpEvaluator newInstance(ApplicationContext appCtx) {
        return DefaultExpEvaluator.newInstance(appCtx, fsscriptClosureDefinition.newFoggyClosure());
    }

    @Override
    public boolean hasImport(Fsscript fscript) {
        if (exp instanceof ImportFsscriptExp) {

            return ((ImportFsscriptExp) exp).hasImport(fscript, true, true);
        } else if (exp instanceof NCountExp) {
            for (Exp exp1 : ((NCountExp) exp).value) {
                if (exp1 instanceof ImportFsscriptExp) {
                    if (((ImportFsscriptExp) exp1).hasImport(fscript, true, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public FsscriptImpl(FsscriptClosureDefinition fsscriptClosureDefinition, Exp exp) {
        this(fsscriptClosureDefinition, exp, null);
    }

    public FsscriptImpl(
            FsscriptClosureDefinition fsscriptClosureDefinition,
            Exp exp,
            String compiledSource) {
        this.fsscriptClosureDefinition = fsscriptClosureDefinition;
        this.exp = exp;
        this.sourceContentRevision = compiledSource == null
                ? null
                : FsscriptSourceContentRevision.calculate(compiledSource);
    }

    @Override
    public Object eval(ExpEvaluator ee) {
        try {
            return exp.evalValue(ee);
        } catch (RuntimeException e) {
            log.error("执行fsscript异常: {}", fsscriptClosureDefinition, e);
            throw ErrorUtils.toRuntimeException(e);
        }
    }



    @Override
    public String toString() {
        return "FsscriptImpl{" +
                "fsscriptClosureDefinition=" + fsscriptClosureDefinition +
                '}';
    }
}
