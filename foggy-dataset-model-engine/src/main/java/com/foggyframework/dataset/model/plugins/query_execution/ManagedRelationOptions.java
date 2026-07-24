package com.foggyframework.dataset.model.plugins.query_execution;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Set;

/**
 * 准备受管关系代数时的控制选项
 */
@Getter
@Builder
public class ManagedRelationOptions {
    
    /**
     * 目的说明，用于 debug
     */
    private final String purpose;

    /**
     * 是否要求返回的 SQL 必须可以被安全包裹在外层 CTE 中
     */
    private final boolean wrappableRequired;

    /**
     * 是否禁用内部查询缓存短路
     * <p>外层 Pivot 如果自己负责了缓存，或者外层有进一步的过滤逻辑，不应被内层的 L2 cache 截断流程。
     */
    private final boolean disableInnerCacheShortCircuit;

    /**
     * 是否要求返回的别名是稳定的
     */
    private final boolean requireStableAliases;

    /**
     * 要求的方言能力集合
     * <p>prepare 完成时会用 dialect 校验这些 capability，不满足则 fail-closed。</p>
     */
    @Singular("requireDialectCapability")
    private final Set<DialectCapability> requiredDialectCapabilities;

    /**
     * 方言能力枚举
     */
    public enum DialectCapability {
        CTE,
        WINDOW_FUNCTION
    }
}
