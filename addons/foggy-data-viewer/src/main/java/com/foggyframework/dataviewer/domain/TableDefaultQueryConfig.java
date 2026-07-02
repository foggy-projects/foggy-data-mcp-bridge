package com.foggyframework.dataviewer.domain;

import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表格实例默认查询配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDefaultQueryConfig {

    /** 同一 QM 下的业务表格实例标识 */
    private String tableInstanceId;

    /** QM 模型名 */
    private String queryModel;

    /** UI 默认展示列，用户可调整 */
    private List<String> defaultVisibleColumns;

    /** 默认排序 */
    private List<OrderRequestDef> defaultOrderBy;

    /** 默认分页大小 */
    private Integer defaultPageSize;

    /** 默认过滤条件 */
    private List<SliceRequestDef> defaultSlices;

    private Integer version;

    /** USER / TENANT / ROLE / SYSTEM / FALLBACK */
    private String source;
}
