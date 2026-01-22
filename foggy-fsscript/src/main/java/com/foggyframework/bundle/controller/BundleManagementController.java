package com.foggyframework.bundle.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bundle动态管理Controller
 * <p>
 * 提供REST API来动态添加、移除、查询外部Bundle。
 * </p>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/bundles")
@Api(tags = "Bundle动态管理")
public class BundleManagementController {

    @Resource
    private SystemBundlesContext systemBundlesContext;

    /**
     * 列出所有外部Bundle
     */
    @GetMapping("/list")
    @ApiOperation("列出所有外部Bundle")
    public RX<List<BundleInfo>> listBundles() {
        List<BundleDefinition> bundles = systemBundlesContext.listExternalBundles();
        List<BundleInfo> result = bundles.stream()
                .map(bd -> new BundleInfo(
                        bd.getName(),
                        bd.getNamespace(),
                        bd instanceof com.foggyframework.bundle.external.ExternalBundleDefinition
                                ? ((com.foggyframework.bundle.external.ExternalBundleDefinition) bd).getPath()
                                : null,
                        bd instanceof com.foggyframework.bundle.external.ExternalBundleDefinition
                                ? ((com.foggyframework.bundle.external.ExternalBundleDefinition) bd).isWatch()
                                : false
                ))
                .collect(Collectors.toList());
        return RX.success(result);
    }

    /**
     * 添加外部Bundle
     */
    @PostMapping("/add")
    @ApiOperation("动态添加外部Bundle")
    public RX<String> addBundle(@RequestBody AddBundleRequest request) {
        log.info("收到添加Bundle请求: name={}, namespace={}, path={}",
                request.getName(), request.getNamespace(), request.getPath());

        boolean success = systemBundlesContext.addExternalBundle(
                request.getName(),
                request.getNamespace(),
                request.getPath(),
                request.isWatch()
        );

        if (success) {
            return RX.success("Bundle添加成功");
        } else {
            return RX.error("Bundle添加失败，请检查日志");
        }
    }

    /**
     * 移除Bundle
     */
    @DeleteMapping("/remove/{bundleName}")
    @ApiOperation("移除外部Bundle")
    public RX<String> removeBundle(
            @ApiParam(value = "Bundle名称", required = true) @PathVariable String bundleName) {
        log.info("收到移除Bundle请求: {}", bundleName);

        boolean success = systemBundlesContext.removeBundle(bundleName);

        if (success) {
            return RX.success("Bundle移除成功");
        } else {
            return RX.error("Bundle移除失败，请检查日志");
        }
    }

    /**
     * 检查Bundle是否存在
     */
    @GetMapping("/exists/{bundleName}")
    @ApiOperation("检查Bundle是否存在")
    public RX<Boolean> existsBundle(
            @ApiParam(value = "Bundle名称", required = true) @PathVariable String bundleName) {
        boolean exists = systemBundlesContext.containBundle(bundleName);
        return RX.success(exists);
    }

    // ==================== DTO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BundleInfo {
        private String name;
        private String namespace;
        private String path;
        private boolean watch;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddBundleRequest {
        @ApiParam(value = "Bundle名称（唯一标识）", required = true)
        private String name;

        @ApiParam(value = "命名空间（空字符串表示默认命名空间）")
        private String namespace = "";

        @ApiParam(value = "外部目录路径", required = true)
        private String path;

        @ApiParam(value = "是否监听文件变化")
        private boolean watch = false;
    }
}
