package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.exception.TooManyPivotCellsException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基数熔断器 (Cardinality Circuit Breaker)
 *
 * <p>在 Phase 2 内存运算前执行两段式校验，防止不受控的笛卡尔积导致 OOM。</p>
 *
 * <p>校验逻辑：
 * <ol>
 *   <li>前置预估校验：计算 estimated_output_rows × estimated_output_cols，含小计膨胀系数</li>
 *   <li>运行时防御：如果用户没有配置 limit，在叶子节点强加默认上限</li>
 * </ol>
 * </p>
 */
public class CardinalityBreaker {

    /** 默认最大 cell 数阈值 */
    public static final long DEFAULT_MAX_PIVOT_CELLS = 100_000;

    /** 默认行轴叶子节点限制 */
    public static final int DEFAULT_ROW_LIMIT = 1000;

    /** 默认列轴叶子节点限制 */
    public static final int DEFAULT_COL_LIMIT = 500;

    /** 小计膨胀系数：每增加一个层级，预估增加 10% 的行/列 */
    private static final double SUBTOTAL_EXPANSION_FACTOR = 0.1;

    private final long maxPivotCells;

    public CardinalityBreaker() {
        this(DEFAULT_MAX_PIVOT_CELLS);
    }

    public CardinalityBreaker(long maxPivotCells) {
        this.maxPivotCells = maxPivotCells;
    }

    /**
     * 前置预估校验
     *
     * @param rowDomainSize  行域基数
     * @param colDomainSize  列域基数
     * @param pivot          Pivot 请求（用于获取层级数和小计配置）
     * @throws TooManyPivotCellsException 如果预估 cell 数超过阈值
     */
    public void checkEstimate(long rowDomainSize, long colDomainSize, PivotRequest pivot) {
        PivotOptions options = pivot.getOptions();
        boolean rowSubtotals = options != null && options.isRowSubtotals();
        boolean colSubtotals = options != null && options.isColumnSubtotals();

        long estimatedRows = estimateWithExpansion(rowDomainSize, pivot.getRowLevelCount(), rowSubtotals);
        long estimatedCols = estimateWithExpansion(colDomainSize, pivot.getColumnLevelCount(), colSubtotals);
        long estimatedCells = estimatedRows * estimatedCols;

        if (estimatedCells > maxPivotCells) {
            throw new TooManyPivotCellsException(rowDomainSize, colDomainSize, estimatedCells, maxPivotCells);
        }
    }

    /**
     * 计算含小计膨胀的预估值
     */
    private long estimateWithExpansion(long domainSize, int levelCount, boolean hasSubtotals) {
        if (!hasSubtotals || levelCount <= 1) {
            return domainSize;
        }
        double expansionFactor = 1.0 + levelCount * SUBTOTAL_EXPANSION_FACTOR;
        return (long) Math.ceil(domainSize * expansionFactor);
    }

    /**
     * 提取行域成员集合
     *
     * @param resultSet  Phase 1 聚合结果
     * @param rowFields  行轴字段名列表
     * @return 行键元组集合
     */
    public static Set<List<Object>> extractRowDomain(List<Map<String, Object>> resultSet,
                                                      List<String> rowFields) {
        return resultSet.stream()
                .map(row -> rowFields.stream()
                        .map(row::get)
                        .collect(Collectors.toList()))
                .collect(Collectors.toSet());
    }

    /**
     * 提取列域成员集合
     */
    public static Set<List<Object>> extractColumnDomain(List<Map<String, Object>> resultSet,
                                                         List<String> colFields) {
        return extractRowDomain(resultSet, colFields); // 算法相同
    }
}
