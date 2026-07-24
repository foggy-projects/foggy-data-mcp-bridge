package com.foggyframework.dataset.model.engine.pivot.algo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pivot 算法共享工具方法
 */
public final class PivotAlgoUtils {

    private PivotAlgoUtils() {
    }

    /**
     * 根据列轴字段值构建 cell 键
     *
     * <p>当无列轴时返回空字符串；有列轴时将各列值用 "|" 拼接。</p>
     *
     * @param row       数据行
     * @param colFields 列轴字段名列表
     * @return cell 键字符串
     */
    public static String buildCellKey(Map<String, Object> row, List<String> colFields) {
        if (colFields.isEmpty()) return "";
        return colFields.stream()
                .map(f -> String.valueOf(row.getOrDefault(f, "")))
                .collect(Collectors.joining("|"));
    }
}
