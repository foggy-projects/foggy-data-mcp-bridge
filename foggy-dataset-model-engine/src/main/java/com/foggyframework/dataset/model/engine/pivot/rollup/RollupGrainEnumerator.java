package com.foggyframework.dataset.model.engine.pivot.rollup;

import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;

import java.util.*;

/**
 * Rollup Grain 枚举器
 *
 * <p>根据 PivotOptions 和行/列字段列表，枚举所有需要辅助聚合查询的分组粒度。</p>
 *
 * <p>枚举规则（参考 04_non_additive_rollup_design.md §五）：</p>
 * <ul>
 *   <li>Row subtotals: 按前缀层级生成 grain，从内层向外</li>
 *   <li>Column subtotals: 同理</li>
 *   <li>Grand total: 所有 rows 汇总</li>
 *   <li>交叉: rowSubtotalGrains × columnSubtotalGrains</li>
 *   <li>去重: 按 grainKey 去重</li>
 * </ul>
 */
public class RollupGrainEnumerator {

    /**
     * 枚举所有需要的辅助查询 grain
     *
     * @param rowFields 行轴字段名列表
     * @param colFields 列轴字段名列表
     * @param options   Pivot 行为开关
     * @return 去重后的 grain 列表（不含叶子 grain）
     */
    public static List<RollupGrain> enumerate(
            List<String> rowFields,
            List<String> colFields,
            PivotOptions options) {

        Set<String> seenKeys = new LinkedHashSet<>();
        List<RollupGrain> grains = new ArrayList<>();

        // 行轴小计前缀 grain 列表
        List<List<String>> rowSubtotalPrefixes = new ArrayList<>();
        if (options.isRowSubtotals() && rowFields.size() > 1) {
            for (int level = rowFields.size() - 1; level >= 1; level--) {
                rowSubtotalPrefixes.add(rowFields.subList(0, level));
            }
        }

        // 列轴小计前缀 grain 列表
        List<List<String>> colSubtotalPrefixes = new ArrayList<>();
        if (options.isColumnSubtotals() && colFields.size() > 1) {
            for (int level = colFields.size() - 1; level >= 1; level--) {
                colSubtotalPrefixes.add(colFields.subList(0, level));
            }
        }

        // 1. 行轴小计 grain: rowPrefix + colFields 全部
        for (List<String> rowPrefix : rowSubtotalPrefixes) {
            List<String> fields = new ArrayList<>(rowPrefix);
            fields.addAll(colFields);
            addIfNew(grains, seenKeys, fields);
        }

        // 2. 列轴小计 grain: rowFields 全部 + colPrefix
        for (List<String> colPrefix : colSubtotalPrefixes) {
            List<String> fields = new ArrayList<>(rowFields);
            fields.addAll(colPrefix);
            addIfNew(grains, seenKeys, fields);
        }

        // 3. 行列小计交叉 grain: rowPrefix + colPrefix
        for (List<String> rowPrefix : rowSubtotalPrefixes) {
            for (List<String> colPrefix : colSubtotalPrefixes) {
                List<String> fields = new ArrayList<>(rowPrefix);
                fields.addAll(colPrefix);
                addIfNew(grains, seenKeys, fields);
            }
        }

        // 4. Grand total grains
        if (options.isGrandTotal()) {
            // rows 全部汇总: 只保留 colFields
            if (!colFields.isEmpty()) {
                addIfNew(grains, seenKeys, new ArrayList<>(colFields));
            }

            // grand + column subtotals 交叉
            for (List<String> colPrefix : colSubtotalPrefixes) {
                addIfNew(grains, seenKeys, new ArrayList<>(colPrefix));
            }

            // 全表总计: 空 grain
            addIfNew(grains, seenKeys, Collections.emptyList());
        }

        return grains;
    }

    private static void addIfNew(List<RollupGrain> grains, Set<String> seenKeys, List<String> fields) {
        RollupGrain grain = new RollupGrain(fields);
        if (seenKeys.add(grain.getGrainKey())) {
            grains.add(grain);
        }
    }
}
