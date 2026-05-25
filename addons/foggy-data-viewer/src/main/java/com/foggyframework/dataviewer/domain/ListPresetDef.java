package com.foggyframework.dataviewer.domain;

import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * 自定义列表方案。
 * <p>
 * v1 中 ownerId 来自前端传入的 userId，用作配置存储命名空间，不作为安全边界。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "list_presets")
@CompoundIndexes({
        @CompoundIndex(name = "idx_owner_model_business", def = "{'ownerId': 1, 'model': 1, 'businessKey': 1}"),
        @CompoundIndex(name = "idx_owner_model_default", def = "{'ownerId': 1, 'model': 1, 'businessKey': 1, 'isDefault': 1}")
})
public class ListPresetDef {

    @Id
    private String id;

    /** QM 模型名 */
    private String model;

    /** 同一模型在不同业务页面中的隔离 key */
    private String businessKey;

    /** 显示名称 */
    private String title;

    /** 描述 */
    private String description;

    /** 查询投影列 */
    private List<String> columns;

    /** UI 列偏好 */
    private List<ColumnViewSetting> columnSettings;

    /** 查询条件与排序 */
    private QueryConditionPreset query;

    /** 每页大小 */
    private Integer pageSize;

    /** 可见性范围，v1 只按 ownerId 查询 */
    private QueryVisibility visibility;

    /** 配置命名空间用户 */
    private String ownerId;

    private String ownerDeptId;

    private String ownerTenantId;

    @Field("isDefault")
    private Boolean isDefault;

    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnViewSetting {
        private String name;
        private Boolean visible;
        private Integer width;
        private Integer minWidth;
        private String fixed;
        private Integer order;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryConditionPreset {
        private List<SliceRequestDef> slice;
        private List<OrderRequestDef> orderBy;
    }
}
