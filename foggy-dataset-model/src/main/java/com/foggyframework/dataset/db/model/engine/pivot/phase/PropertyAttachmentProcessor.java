package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.PropertyAttacher;
import com.foggyframework.dataset.db.model.engine.pivot.algo.PropertyResolver;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Phase 2.7: Properties 后置贴合。
 *
 * <p>通过 lookupProvider 回调获取维度属性 lookup table，
 * 然后调用 PropertyAttacher 贴合到结果集。</p>
 *
 * <p>lookupProvider 由 PivotPipeline 注入，封装了辅助维度查询逻辑。</p>
 */
public class PropertyAttachmentProcessor implements PivotPhase2Processor {

    /**
     * 属性 lookup 提供器。参数: resolvedProps → 返回: Map<dimKey, Map<keyValue, Map<propField, propValue>>>
     */
    private final Function<List<PropertyResolver.ResolvedProperty>,
            Map<String, Map<Object, Map<String, Object>>>> lookupProvider;

    public PropertyAttachmentProcessor(
            Function<List<PropertyResolver.ResolvedProperty>,
                    Map<String, Map<Object, Map<String, Object>>>> lookupProvider) {
        this.lookupProvider = lookupProvider;
    }

    @Override
    public void process(PivotPhase2Context ctx) {
        if (ctx.getResolvedProps().isEmpty()) {
            return;
        }
        ctx.getLogger().debug("[Pivot] Phase 2.7: Property attachment for {} properties",
                ctx.getResolvedProps().size());
        Map<String, Map<Object, Map<String, Object>>> lookupTables =
                lookupProvider.apply(ctx.getResolvedProps());
        PropertyAttacher.attach(ctx.getResultSet(), ctx.getResolvedProps(), lookupTables);
    }
}
