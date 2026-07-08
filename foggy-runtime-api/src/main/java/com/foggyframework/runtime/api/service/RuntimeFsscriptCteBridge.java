package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.FsscriptRequest;
import com.foggyframework.runtime.api.service.RuntimeComposeRunner.RuntimeComposeRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeFsscriptCteBridge {

    private final RuntimeComposeRunner composeRunner;

    public RuntimeFsscriptCteBridge(RuntimeComposeRunner composeRunner) {
        this.composeRunner = composeRunner;
    }

    public PropertyHolder foggyHost(
            FsscriptRequest request,
            String headerNamespace,
            String authorization,
            Map<String, String> headers
    ) {
        PropertyFunction cte = cteBridgeEnabled(request)
                ? new CteFunctions(request, headerNamespace, authorization, headers)
                : new DeniedCteFunctions();
        return new FoggyHost(cte);
    }

    private boolean cteBridgeEnabled(FsscriptRequest request) {
        return booleanFlag(request.capabilities(), "cteBridge")
                || booleanFlag(request.options(), "cteBridge");
    }

    private ComposeResponse invokeCte(
            FsscriptRequest fsscriptRequest,
            String headerNamespace,
            String authorization,
            Map<String, String> headers,
            ComposeScriptService.Mode mode,
            String phase,
            Object[] args
    ) {
        RuntimeComposeRunResult result = composeRunner.run(mode, phase,
                RuntimeComposeInvocation.fromFsscriptCteArgs(
                        fsscriptRequest, headerNamespace, authorization, headers, args, phase));
        return result.response();
    }

    private static boolean booleanFlag(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return false;
        }
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private final class CteFunctions implements PropertyFunction {
        private final FsscriptRequest request;
        private final String headerNamespace;
        private final String authorization;
        private final Map<String, String> headers;

        private CteFunctions(
                FsscriptRequest request,
                String headerNamespace,
                String authorization,
                Map<String, String> headers
        ) {
            this.request = request;
            this.headerNamespace = headerNamespace;
            this.authorization = authorization;
            this.headers = headers;
        }

        @Override
        public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
            if ("validate".equals(methodName)) {
                return invokeCte(request, headerNamespace, authorization, headers,
                        ComposeScriptService.Mode.VALIDATE, "compose.validate", args);
            }
            if ("preview".equals(methodName)) {
                return invokeCte(request, headerNamespace, authorization, headers,
                        ComposeScriptService.Mode.PREVIEW, "compose.preview", args);
            }
            if ("execute".equals(methodName)) {
                return invokeCte(request, headerNamespace, authorization, headers,
                        ComposeScriptService.Mode.EXECUTE, "compose.execute", args);
            }
            throw new CteBridgeDeniedException("Unsupported foggy.cte function: " + methodName);
        }
    }

    private static final class DeniedCteFunctions implements PropertyFunction {
        @Override
        public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
            throw new CteBridgeDeniedException("foggy.cte." + methodName + " is disabled for this request.");
        }
    }

    private static final class FoggyHost implements PropertyHolder {
        private final PropertyFunction cte;

        private FoggyHost(PropertyFunction cte) {
            this.cte = cte;
        }

        @Override
        public Object getProperty(String name) {
            if ("cte".equals(name)) {
                return cte;
            }
            return PropertyHolder.NO_MATCH;
        }
    }

    public static final class CteBridgeDeniedException extends RuntimeException {
        private CteBridgeDeniedException(String message) {
            super(message);
        }
    }
}
