package com.foggyframework.dataviewer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 前端元数据契约 (frontend-meta v1)
 * <p>
 * 面向前端渲染和代码生成的标准 JSON 结构。
 * 与 V3 语义元数据的区别：fields 为数组（保证顺序）、无 prompt/meta/models 嵌套。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontendMeta {

    /** 元数据契约版本，固定 "v1" */
    private String metaVersion;

    /** QM 模型名称 */
    private String model;

    /** 模型显示名称 */
    private String caption;

    /** 模型说明 */
    private String description;

    /** 字段数组（保证输出顺序） */
    private List<FieldMeta> fields;

    /** 默认配置 */
    private DefaultsMeta defaults;

    /** 模型级能力声明 */
    private CapabilitiesMeta capabilities;

    /** 全局参数和定制参数入口 */
    private ParamsMeta params;

    // ── 嵌套结构 ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMeta {
        /** 字段唯一标识（对应 V3 fieldName） */
        private String name;
        /** 显示名称（对应 V3 name） */
        private String title;
        /** 数据类型 */
        private String type;
        /** 字段分类 */
        private String category;
        /** 过滤器类型 */
        private String filterType;
        /** 是否可筛选 */
        private Boolean filterable;
        /** 是否可排序 */
        private Boolean sortable;
        /** 是否度量 */
        private Boolean measure;
        /** 是否可聚合 */
        private Boolean aggregatable;
        /** 默认聚合方式 */
        private String aggregation;
        /** 底层数据库列名 */
        private String sourceColumn;
        /** 字典标识 */
        private String dictId;
        /** 字典模式：static / remote */
        private String dictMode;
        /** 是否计算字段 */
        private Boolean calculated;
        /** 是否层级维度 */
        private Boolean hierarchical;
        /** 可用层级操作 */
        private List<String> hierarchyOps;
        /** 维度成员远程查询配置 */
        private MemberLookupMeta memberLookup;
        /** 前端 UI 提示 */
        private UiHintsMeta uiHints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberLookupMeta {
        private boolean enabled;
        /** DSL slice 使用的值字段 */
        private String selectionFieldName;
        /** 前端展示字段 */
        private String displayFieldName;
        private Boolean searchable;
        private Boolean pageable;
        private Integer defaultLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UiHintsMeta {
        private Boolean visible;
        private Boolean required;
        private Boolean nullable;
        private String format;
        private Integer width;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefaultsMeta {
        private List<String> visibleColumns;
        private List<String> searchFields;
        private Integer pageSize;
        private List<Map<String, String>> orderBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapabilitiesMeta {
        @Builder.Default
        private boolean pageable = true;
        @Builder.Default
        private boolean sortable = true;
        @Builder.Default
        private boolean filterable = true;
        @Builder.Default
        private boolean aggregatable = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamsMeta {
        private Map<String, Object> global;
        private Map<String, Object> custom;
    }
}
