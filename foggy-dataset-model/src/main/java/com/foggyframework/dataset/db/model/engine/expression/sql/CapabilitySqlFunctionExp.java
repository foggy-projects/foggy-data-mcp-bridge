package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityFunctionRenderer;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilitySqlFragment;
import com.foggyframework.dataset.db.model.engine.compose.capability.FunctionDescriptor;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.NullExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL expression node for capability-registered sql_scalar functions.
 *
 * <p>At eval time, recursively evaluates child argument expressions to
 * SQL fragments, builds an arg-name→SQL map, invokes the registered
 * {@link CapabilityFunctionRenderer}, and converts the resulting
 * {@link CapabilitySqlFragment} into the engine's {@link SqlFragment}.</p>
 *
 * @since 8.4.0
 */
public class CapabilitySqlFunctionExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final String functionName;
    private final List<Exp> args;
    private final transient CapabilityFunctionRenderer renderer;
    private final transient FunctionDescriptor descriptor;
    private final String dialect;

    public CapabilitySqlFunctionExp(
            String functionName,
            List<Exp> args,
            CapabilityFunctionRenderer renderer,
            FunctionDescriptor descriptor,
            String dialect) {
        super(functionName);
        this.functionName = functionName;
        this.args = args;
        this.renderer = renderer;
        this.descriptor = descriptor;
        this.dialect = dialect;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        // 1. Evaluate child expressions to SqlFragment
        List<SqlFragment> argFragments = new ArrayList<>(args.size());
        for (Exp arg : args) {
            if (arg instanceof EmptyExp) continue;
            if (arg instanceof NullExp) {
                argFragments.add(SqlFragment.ofLiteral("NULL"));
                continue;
            }
            SqlFragment fragment = (SqlFragment) arg.evalResult(evaluator);
            if (fragment != null) {
                argFragments.add(fragment);
            }
        }

        // 2. Build arg-name → SQL-string map
        Map<String, String> argsMap = new LinkedHashMap<>();
        var argsSchema = descriptor.getArgsSchema();
        for (int i = 0; i < argFragments.size() && i < argsSchema.size(); i++) {
            String argName = (String) argsSchema.get(i).get("name");
            argsMap.put(argName, argFragments.get(i).getSql());
        }

        // 3. Invoke renderer
        CapabilitySqlFragment result = renderer.render(argsMap, dialect);

        // 4. Validate renderer return
        if (result == null) {
            throw new SecurityException(
                    "Renderer for function '" + functionName + "' did not return a CapabilitySqlFragment");
        }

        // 5. Convert to engine SqlFragment
        SqlFragment engineFragment = new SqlFragment();
        engineFragment.setSql(result.getSql());
        // Merge column references from all arguments
        for (SqlFragment af : argFragments) {
            engineFragment.getReferencedColumns().addAll(af.getReferencedColumns());
        }
        // Note: bind params from CapabilitySqlFragment are tracked in result.getParams()
        // but the engine SqlFragment doesn't have a params field —
        // params are embedded in the SQL via the renderer (like DATE_FORMAT patterns).
        // For true parameterized queries, the renderer should use literal placeholders
        // and the caller should collect params from the CapabilitySqlFragment.

        return engineFragment;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    @Override
    public String toString() {
        String argsStr = args.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return "[CapabilitySqlFunction:" + functionName + "(" + argsStr + ")]";
    }
}
