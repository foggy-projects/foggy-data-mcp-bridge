package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.SavedQueryDef;
import com.foggyframework.dataviewer.service.SavedQueryService;
import com.foggyframework.dataviewer.service.SavedQueryService.SaveQueryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 保存查询 API 控制器
 */
@RestController
@RequestMapping("/data-viewer/api/saved-query")
@RequiredArgsConstructor
public class SavedQueryController {

    private final SavedQueryService savedQueryService;

    /**
     * 保存查询
     */
    @PostMapping
    public RX<SavedQueryDef> save(
            @RequestBody SaveQueryRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!savedQueryService.isAvailable()) {
            return RX.<SavedQueryDef>status(HttpStatusCode.valueOf(503))
                    .msg("SecurityIdentityResolver 未配置，保存查询功能不可用").build();
        }
        if (authorization == null || authorization.isBlank()) {
            return RX.failB("缺少 Authorization 请求头", null);
        }
        return RX.ok(savedQueryService.save(request, authorization));
    }

    /**
     * 列出可见查询
     */
    @GetMapping("/list/{model}")
    public RX<List<SavedQueryDef>> list(
            @PathVariable String model,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!savedQueryService.isAvailable()) {
            return RX.<List<SavedQueryDef>>status(HttpStatusCode.valueOf(503))
                    .msg("SecurityIdentityResolver 未配置，保存查询功能不可用").build();
        }
        if (authorization == null || authorization.isBlank()) {
            return RX.failB("缺少 Authorization 请求头", null);
        }
        return RX.ok(savedQueryService.list(model, authorization));
    }

    /**
     * 获取单个查询详情
     */
    @GetMapping("/{id}")
    public RX<SavedQueryDef> get(@PathVariable String id) {
        return savedQueryService.get(id)
                .map(RX::ok)
                .orElse(RX.notFound().build());
    }

    /**
     * 更新查询（仅 owner）
     */
    @PutMapping("/{id}")
    public RX<SavedQueryDef> update(
            @PathVariable String id,
            @RequestBody SaveQueryRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!savedQueryService.isAvailable()) {
            return RX.<SavedQueryDef>status(HttpStatusCode.valueOf(503))
                    .msg("SecurityIdentityResolver 未配置，保存查询功能不可用").build();
        }
        if (authorization == null || authorization.isBlank()) {
            return RX.failB("缺少 Authorization 请求头", null);
        }
        return savedQueryService.update(id, request, authorization)
                .map(RX::ok)
                .orElse(RX.notFound().build());
    }

    /**
     * 删除查询（仅 owner）
     */
    @DeleteMapping("/{id}")
    public RX<Void> delete(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!savedQueryService.isAvailable()) {
            return RX.<Void>status(HttpStatusCode.valueOf(503))
                    .msg("SecurityIdentityResolver 未配置，保存查询功能不可用").build();
        }
        if (authorization == null || authorization.isBlank()) {
            return RX.failB("缺少 Authorization 请求头", null);
        }
        if (savedQueryService.delete(id, authorization)) {
            return RX.ok(null);
        }
        return RX.notFound().build();
    }

    /**
     * 应用保存的查询（创建临时 cached_query 并返回 queryId）
     */
    @PostMapping("/{id}/apply")
    public RX<Map<String, String>> apply(@PathVariable String id) {
        return savedQueryService.apply(id)
                .map(queryId -> RX.ok(Map.of("queryId", queryId)))
                .orElse(RX.notFound().build());
    }
}
