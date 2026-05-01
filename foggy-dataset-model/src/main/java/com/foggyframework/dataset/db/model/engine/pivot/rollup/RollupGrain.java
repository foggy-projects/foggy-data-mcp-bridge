package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 辅助查询的分组粒度
 *
 * <p>表示一个特定的 GROUP BY 粒度，用于生成辅助聚合查询。</p>
 */
public class RollupGrain {

    /** 要 GROUP BY 的字段列表 */
    private final List<String> groupByFields;

    /** 去重用 key（字段名按分隔符拼接） */
    private final String grainKey;

    public RollupGrain(List<String> groupByFields) {
        this.groupByFields = List.copyOf(groupByFields);
        this.grainKey = String.join("\u001F", groupByFields);
    }

    public List<String> getGroupByFields() { return groupByFields; }
    public String getGrainKey() { return grainKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return grainKey.equals(((RollupGrain) o).grainKey);
    }

    @Override
    public int hashCode() {
        return grainKey.hashCode();
    }

    @Override
    public String toString() {
        return "RollupGrain[" + groupByFields.stream().collect(Collectors.joining(", ")) + "]";
    }
}
