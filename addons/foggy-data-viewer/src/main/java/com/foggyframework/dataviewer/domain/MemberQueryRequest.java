package com.foggyframework.dataviewer.domain;

import lombok.Data;

import java.util.List;

/**
 * 维度成员查询请求
 * <p>
 * 前端只需传 qmModel + fieldName，内部自动映射到 synthetic member-QM。
 */
@Data
public class MemberQueryRequest {

    /** 业务 QM 名称（如 FactOrderQueryModel） */
    private String qmModel;

    /** 前端 schema 字段名（如 customer$caption） */
    private String fieldName;

    /** 搜索关键字 */
    private String keyword;

    /** 起始位置，默认 0 */
    private Integer start;

    /** 每页大小，默认 20 */
    private Integer limit;

    /** 回填已选值 */
    private List<Object> selectedValues;

    /** 层级查询 */
    private HierarchyParam hierarchy;

    @Data
    public static class HierarchyParam {
        /** 层级操作符（本版本正式支持 childrenOf） */
        private String op;
        /** 目标节点值 */
        private Object value;
    }
}
