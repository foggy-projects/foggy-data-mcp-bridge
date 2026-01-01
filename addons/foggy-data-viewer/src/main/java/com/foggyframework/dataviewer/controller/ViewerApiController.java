package com.foggyframework.dataviewer.controller;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据浏览器API控制器
 */
@Slf4j
@RestController
@RequestMapping("/data-viewer/api")
@RequiredArgsConstructor
public class ViewerApiController {

    private final QueryCacheService cacheService;

    /**
     * 获取查询元数据（用于初始页面加载）
     */
    @GetMapping("/query/{queryId}/meta")
    public ResponseEntity<QueryMetaResponse> getQueryMeta(@PathVariable String queryId) {
        return cacheService.getQuery(queryId)
                .map(ctx -> ResponseEntity.ok(new QueryMetaResponse(
                        ctx.getTitle(),
                        ctx.getSchema(),
                        ctx.getEstimatedRowCount(),
                        ctx.getExpiresAt().toString(),
                        ctx.getModel(),
                        ctx.getColumns()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 执行查询并返回数据
     */
    @PostMapping("/query/{queryId}/data")
    public ResponseEntity<ViewerDataResponse> queryData(
            @PathVariable String queryId,
            @RequestBody ViewerQueryRequest request) {

        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(410).body(
                    ViewerDataResponse.expired("Query link has expired")
            );
        }

        CachedQueryContext ctx = ctxOpt.get();

        try {
            // TODO: 使用现有的QueryFacade执行查询
            // 目前返回模拟数据
            List<Map<String, Object>> mockItems = generateMockData(ctx, request);
            long mockTotal = 100L;

            return ResponseEntity.ok(ViewerDataResponse.success(
                    mockItems,
                    mockTotal,
                    request.getStart(),
                    request.getLimit()
            ));
        } catch (Exception e) {
            log.error("Error executing query for queryId: {}", queryId, e);
            return ResponseEntity.ok(ViewerDataResponse.error(e.getMessage()));
        }
    }

    /**
     * 生成模拟数据（用于测试）
     */
    private List<Map<String, Object>> generateMockData(CachedQueryContext ctx, ViewerQueryRequest request) {
        List<Map<String, Object>> items = new ArrayList<>();
        int start = request.getStart() != null ? request.getStart() : 0;
        int limit = request.getLimit() != null ? request.getLimit() : 50;

        for (int i = 0; i < limit; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            int rowNum = start + i + 1;

            if (ctx.getColumns() != null) {
                for (String col : ctx.getColumns()) {
                    if (col.toLowerCase().contains("id")) {
                        item.put(col, "ID-" + rowNum);
                    } else if (col.toLowerCase().contains("date")) {
                        item.put(col, "2025-01-" + String.format("%02d", (rowNum % 28) + 1));
                    } else if (col.toLowerCase().contains("amount") || col.toLowerCase().contains("price")) {
                        item.put(col, Math.round(Math.random() * 10000 * 100.0) / 100.0);
                    } else if (col.toLowerCase().contains("name")) {
                        item.put(col, "Item " + rowNum);
                    } else {
                        item.put(col, "Value " + rowNum);
                    }
                }
            }

            items.add(item);
        }

        return items;
    }

    /**
     * 查询元数据响应
     */
    public record QueryMetaResponse(
            String title,
            List<CachedQueryContext.ColumnSchema> schema,
            Long estimatedRowCount,
            String expiresAt,
            String model,
            List<String> columns
    ) {}
}
