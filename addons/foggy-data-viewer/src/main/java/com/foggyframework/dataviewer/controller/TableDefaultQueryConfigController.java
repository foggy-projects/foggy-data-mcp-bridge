package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;
import com.foggyframework.dataviewer.service.TableDefaultQueryConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表格实例默认查询配置 API。
 */
@RestController
@RequestMapping("/data-viewer/api/table-defaults")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TableDefaultQueryConfigController {

    private final TableDefaultQueryConfigService service;

    @GetMapping("/default")
    public RX<TableDefaultQueryConfig> getDefault(
            @RequestParam String queryModel,
            @RequestParam(value = "tableInstanceId", required = false) String tableInstanceId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "roleIds", required = false) List<String> roleIds,
            @RequestParam(value = "includeFallback", required = false, defaultValue = "true") Boolean includeFallback) {
        try {
            TableDefaultQueryConfigRequest request = TableDefaultQueryConfigRequest.builder()
                    .queryModel(queryModel)
                    .tableInstanceId(tableInstanceId)
                    .userId(userId)
                    .tenantId(tenantId)
                    .roleIds(roleIds)
                    .includeFallback(includeFallback)
                    .build();
            return service.resolve(request)
                    .map(RX::ok)
                    .orElse(RX.ok(null));
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }
}
