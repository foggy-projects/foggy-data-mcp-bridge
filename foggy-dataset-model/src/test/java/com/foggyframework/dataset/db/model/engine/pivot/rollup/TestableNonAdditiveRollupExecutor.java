package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;

import java.util.*;

/**
 * 测试辅助类：暴露 NonAdditiveRollupExecutor 的 package-private 方法
 *
 * <p>Stage 4 语义修正：addAxisDomainSlice 不再需要 grainFields 参数，
 * WHERE 约束始终基于完整的 axisFields tuple。</p>
 */
public class TestableNonAdditiveRollupExecutor {

    /**
     * 暴露 addAxisDomainSlice 以便单元测试直接调用
     */
    public static List<SemanticQueryRequest.SliceItem> exposedAddAxisDomainSlice(
            List<String> axisFields,
            Set<List<Object>> domain) {

        List<SemanticQueryRequest.SliceItem> sliceItems = new ArrayList<>();
        NonAdditiveRollupExecutor.addAxisDomainSlice(sliceItems, axisFields, domain);
        return sliceItems;
    }
}
