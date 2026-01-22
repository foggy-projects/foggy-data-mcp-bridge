package com.foggyframework.dataset.mcp.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.mcp.validation.SemanticLayerValidationService;
import com.foggyframework.dataset.mcp.validation.ValidationRequest;
import com.foggyframework.dataset.mcp.validation.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 语义层验证控制器
 *
 * <p>提供REST API用于验证外部语义层文件（TM/QM）
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/semantic-layer")
@Tag(name = "语义层验证", description = "语义层文件验证相关接口")
public class SemanticLayerValidationController {

    @Resource
    private SemanticLayerValidationService validationService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    /**
     * 验证语义层文件夹
     *
     * <p>验证指定目录下的所有TM和QM文件
     *
     * @param request 验证请求
     * @return 验证结果
     */
    @PostMapping("/validate")
    @Operation(summary = "验证语义层文件夹", description = "验证指定目录下的所有TM和QM文件，并返回详细的验证结果")
    public ResponseEntity<ValidationResult> validate(
            @RequestBody @Parameter(description = "验证请求参数") ValidationRequest request
    ) {
        log.info("收到语义层验证请求: path={}, namespace={}", request.getPath(), request.getNamespace());

        try {
            ValidationResult result = validationService.validate(request);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("验证请求处理失败: path={}, error={}", request.getPath(), e.getMessage(), e);

            // 返回错误结果
            ValidationResult errorResult = ValidationResult.builder()
                    .success(false)
                    .namespace(request.getNamespace())
                    .totalFiles(0)
                    .validFiles(0)
                    .invalidFiles(0)
                    .build();

            errorResult.getErrors().add(
                    com.foggyframework.dataset.mcp.validation.ValidationError.simple(
                            "system", "SYSTEM", "请求处理失败: " + e.getMessage()
                    )
            );

            return ResponseEntity.status(500).body(errorResult);
        }
    }

    /**
     * 快速验证（简化参数）
     *
     * @param path      文件夹路径
     * @param namespace 命名空间（可选）
     * @param watch     是否监听文件变化（可选）
     * @return 验证结果
     */
    @GetMapping("/validate")
    @Operation(summary = "快速验证", description = "使用URL参数快速验证语义层文件夹")
    public ResponseEntity<ValidationResult> validateSimple(
            @RequestParam @Parameter(description = "文件夹路径", required = true) String path,
            @RequestParam(required = false, defaultValue = "openhands")
            @Parameter(description = "命名空间，默认为openhands") String namespace,
            @RequestParam(required = false, defaultValue = "false")
            @Parameter(description = "是否监听文件变化，默认为false") boolean watch
    ) {
        ValidationRequest request = ValidationRequest.builder()
                .path(path)
                .namespace(namespace)
                .watch(watch)
                .build();

        return validate(request);
    }

    /**
     * 列出所有已注册的外部Bundle
     *
     * @return Bundle列表
     */
    @GetMapping("/bundles")
    @Operation(summary = "列出外部Bundle", description = "列出所有已注册的外部Bundle及其详细信息")
    public ResponseEntity<List<Map<String, Object>>> listBundles() {
        try {
            // 获取所有外部Bundle
            List<Map<String, Object>> result = systemBundlesContext.listExternalBundles().stream()
                    .map(bundleDef -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", bundleDef.getName());
                        info.put("packageName", bundleDef.getPackageName());
                        info.put("namespace", bundleDef.getNamespace());

                        // 如果是ExternalBundleDefinition，添加额外信息
                        if (bundleDef instanceof com.foggyframework.bundle.external.ExternalBundleDefinition) {
                            com.foggyframework.bundle.external.ExternalBundleDefinition extDef =
                                    (com.foggyframework.bundle.external.ExternalBundleDefinition) bundleDef;
                            info.put("path", extDef.getPath());
                            info.put("watch", extDef.isWatch());
                        }

                        return info;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取Bundle列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    /**
     * 移除指定的Bundle
     *
     * @param bundleName Bundle名称
     * @return 操作结果
     */
    @DeleteMapping("/bundles/{bundleName}")
    @Operation(summary = "移除Bundle", description = "移除指定名称的Bundle")
    public ResponseEntity<Map<String, Object>> removeBundle(
            @PathVariable @Parameter(description = "Bundle名称", required = true) String bundleName
    ) {
        log.info("收到移除Bundle请求: bundleName={}", bundleName);

        try {
            boolean removed = systemBundlesContext.removeBundle(bundleName);

            Map<String, Object> result = new HashMap<>();
            result.put("success", removed);
            result.put("bundleName", bundleName);

            if (removed) {
                result.put("message", "Bundle移除成功");
                return ResponseEntity.ok(result);
            } else {
                result.put("message", "Bundle不存在或移除失败");
                return ResponseEntity.status(404).body(result);
            }

        } catch (Exception e) {
            log.error("移除Bundle失败: bundleName={}, error={}", bundleName, e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("bundleName", bundleName);
            result.put("message", "移除失败: " + e.getMessage());

            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查语义层验证服务的健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "semantic-layer-validation");
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(health);
    }
}
