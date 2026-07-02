package com.foggyframework.dataviewer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表格实例默认查询配置解析请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDefaultQueryConfigRequest {

    private String queryModel;

    private String tableInstanceId;

    private String userId;

    private String tenantId;

    private List<String> roleIds;

    @Builder.Default
    private Boolean includeFallback = true;
}
