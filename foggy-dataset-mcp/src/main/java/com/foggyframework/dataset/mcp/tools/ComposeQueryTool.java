package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.engine.compose.DataSetResult;
import com.foggyframework.dataset.db.model.engine.compose.DslQueryFunction;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.*;
import java.util.*;

/**
 * 组合查询工具 -- 执行 fsscript 编排脚本
 *
 * <p>AI 生成 fsscript 脚本，在沙箱中执行，通过 {@code dsl()} 函数编排多个 QM 查询。</p>
 *
 * <p><b>沙箱机制</b>：使用 JSR-223 {@code FsscriptScriptEngine}，
 * 不注入 {@code ApplicationContext}，从而阻断 {@code @bean} 和 {@code java:} 导入。
 * 仅通过 Bindings 注入白名单函数（{@code dsl}）。</p>
 *
 * <h3>示例调用</h3>
 * <pre>{@code
 * {
 *   "script": "const top = dsl({model:'SaleOrderQM', columns:['partner$id'], orderBy:['-amountTotal'], limit:10});\nconst detail = dsl({model:'CrmLeadQM', columns:['partner$caption','expectedRevenue'], slice:[{field:'partner$id',op:'in',value:top.column('partner$id')}]});\nreturn detail;"
 * }
 * }</pre>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComposeQueryTool implements McpTool {

    private final SemanticQueryServiceV3 queryService;

    @Override
    public String getName() {
        return "dataset.compose_query";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.QUERY);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String script = (String) arguments.get("script");

        if (script == null || script.isBlank()) {
            return RX.failB("Missing required parameter: script");
        }

        String traceId = context.getTraceId();
        String namespace = context.getNamespace();
        String authorization = context.getAuthorization();

        log.info("Executing compose query: traceId={}, namespace={}, scriptLength={}",
                traceId, namespace, script.length());

        try {
            // 1. 构建请求上下文
            SemanticRequestContext requestContext = SemanticRequestContext.of(namespace, authorization);

            // 2. 创建沙箱 ScriptEngine（JSR-223）
            //    不传 ApplicationContext → @bean 和 java: 导入自动失败
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("fsscript");
            if (engine == null) {
                return RX.failB("FSScript engine not available");
            }

            // 3. 创建 Bindings，注入白名单函数
            Bindings bindings = engine.createBindings();
            bindings.put("dsl", new DslQueryFunction(queryService, requestContext));
            // 不注入 applicationContext → 阻断 @bean / java: 导入

            ScriptContext scriptContext = new SimpleScriptContext();
            scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

            // 4. 执行脚本
            Object result = engine.eval(script, scriptContext);

            // 5. 提取结果
            //    脚本可能通过 export 或 return 返回值
            if (result == null) {
                // 检查 export 变量
                Object exported = bindings.get("result");
                if (exported != null) {
                    result = exported;
                }
            }

            // 6. 转换返回格式
            return convertResult(result);

        } catch (ScriptException e) {
            log.warn("Compose script execution failed: traceId={}, error={}", traceId, e.getMessage());
            return RX.failB("Script execution error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Compose query failed: traceId={}", traceId, e);
            return RX.failB("Compose query failed: " + e.getMessage());
        }
    }

    /**
     * 将脚本执行结果转换为统一的 RX 响应
     */
    private Object convertResult(Object result) {
        if (result instanceof DataSetResult ds) {
            // 返回完整的 SemanticQueryResponse（含 items, schema, pagination 等）
            return RX.ok(ds.getRawResponse());
        }
        if (result instanceof List) {
            // 脚本直接返回列表
            return RX.ok(result);
        }
        if (result instanceof Map) {
            // 脚本直接返回对象
            return RX.ok(result);
        }
        if (result == null) {
            return RX.failB("Compose script returned no result. Use 'return' to return a DataSetResult.");
        }
        // 其他类型直接包装
        return RX.ok(result);
    }
}
