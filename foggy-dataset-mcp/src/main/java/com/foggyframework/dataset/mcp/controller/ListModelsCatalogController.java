package com.foggyframework.dataset.mcp.controller;

import com.foggyframework.dataset.mcp.service.ModelCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * Host-facing catalog endpoint for bridge integrations.
 */
@Slf4j
@RestController
@RequestMapping("/semantic/v3")
@RequiredArgsConstructor
public class ListModelsCatalogController {

    private final ModelCatalogService modelCatalogService;

    @PostMapping(value = "/list-models", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> listModels(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        Map<String, Object> options = request != null ? request : Collections.emptyMap();
        log.info("Semantic v3 list-models catalog request: namespace={}, format={}", namespace, options.get("format"));
        return modelCatalogService.buildCatalogResponse(options, namespace, authorization);
    }
}
