package com.foggyframework.dataviewer.domain;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
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

import java.time.Instant;
import java.util.List;

/**
 * 保存的查询定义
 * <p>
 * 用户可以保存筛选条件、排序等查询配置，支持个人/部门/租户三级分享。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "saved_queries")
@CompoundIndexes({
        @CompoundIndex(name = "idx_model_owner", def = "{'model': 1, 'ownerId': 1}"),
        @CompoundIndex(name = "idx_model_dept_visibility", def = "{'model': 1, 'ownerDeptId': 1, 'visibility': 1}"),
        @CompoundIndex(name = "idx_model_tenant_visibility", def = "{'model': 1, 'ownerTenantId': 1, 'visibility': 1}")
})
public class SavedQueryDef {

    @Id
    private String id;

    /** QM 模型名 */
    private String model;

    /** 显示名称 */
    private String title;

    /** 描述（可选） */
    private String description;

    /** 查询列 */
    private List<String> columns;

    /** 过滤条件 */
    private List<SliceRequestDef> slice;

    /** 排序条件 */
    private List<OrderRequestDef> orderBy;

    /** 分组条件 */
    private List<GroupRequestDef> groupBy;

    /** 动态计算字段 */
    private List<CalculatedFieldDef> calculatedFields;

    /** 创建者 userId */
    private String ownerId;

    /** 创建者 deptId（用于部门分享查询） */
    private String ownerDeptId;

    /** 创建者 tenantId（用于租户分享查询） */
    private String ownerTenantId;

    /** 可见性范围 */
    private QueryVisibility visibility;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;
}
