package com.foggyframework.dataviewer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 维度成员查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberQueryResponse {

    private String qmModel;
    private String fieldName;

    /** 前端生成 DSL slice 必须使用此字段（如 customer$id） */
    private String selectionFieldName;

    /** 前端展示字段（如 customer$caption） */
    private String displayFieldName;

    /** 是否层级维度 */
    private Boolean hierarchical;

    /** 当前字段允许的层级操作 */
    private List<String> hierarchyOps;

    /** 当前分页查询结果 */
    private List<MemberOption> items;

    /** 已选值回填结果 */
    private List<MemberOption> selectedItems;

    /** 总条数 */
    private long total;

    /** 是否还有下一页 */
    private boolean hasMore;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberOption {
        private Object value;
        private String label;
        private Object parentValue;
        private Integer depth;
        private Boolean hasChildren;
    }
}
