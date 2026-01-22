package com.foggyframework.dataset.mcp.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 语义层验证服务
 *
 * <p>提供语义层文件（TM/QM）的验证功能，支持：
 * <ul>
 *   <li>动态注册外部Bundle</li>
 *   <li>验证TM模型文件</li>
 *   <li>验证QM查询模型文件</li>
 *   <li>收集错误和警告信息</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Slf4j
@Service
public class SemanticLayerValidationService {

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private QueryModelLoader queryModelLoader;

    /**
     * 验证外部语义层文件夹
     *
     * @param request 验证请求
     * @return 验证结果
     */
    public ValidationResult validate(ValidationRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("开始验证语义层: path={}, namespace={}, watch={}",
                request.getPath(), request.getNamespace(), request.isWatch());

        // 1. 参数验证
        if (request.getPath() == null || request.getPath().isBlank()) {
            return createErrorResult(request.getNamespace(), "路径参数不能为空", startTime);
        }

        File pathFile = new File(request.getPath());
        if (!pathFile.exists()) {
            return createErrorResult(request.getNamespace(), "路径不存在: " + request.getPath(), startTime);
        }

        if (!pathFile.isDirectory()) {
            return createErrorResult(request.getNamespace(), "路径必须是目录: " + request.getPath(), startTime);
        }

        try {
            // 2. 注册外部Bundle
            String bundleName = generateBundleName(request.getNamespace());

            // 如果已存在同名Bundle，先移除
            if (request.isClearExisting() && systemBundlesContext.containBundle(bundleName)) {
                log.info("检测到已存在的Bundle: {}，将被移除", bundleName);
                // 移除Bundle（会自动触发BundleRemovedEvent，由BundleLifecycleListener清理缓存）
                systemBundlesContext.removeBundle(bundleName);
            }

            // 注册新Bundle
            boolean registered = systemBundlesContext.addExternalBundle(
                    bundleName,
                    request.getNamespace(),
                    request.getPath(),
                    request.isWatch()
            );

            if (!registered) {
                return createErrorResult(request.getNamespace(), "Bundle注册失败", startTime);
            }

            log.info("Bundle注册成功: name={}, namespace={}", bundleName, request.getNamespace());

            // 3. 获取注册的Bundle
            Bundle bundle = systemBundlesContext.getBundleByName(bundleName);
            if (bundle == null) {
                return createErrorResult(request.getNamespace(), "无法获取已注册的Bundle: " + bundleName, startTime);
            }

            // 4. 验证TM和QM文件
            ValidationResult result = performValidation(bundle, request);

            // 5. 设置耗时
            result.setDurationMs(System.currentTimeMillis() - startTime);

            log.info("验证完成: namespace={}, totalFiles={}, validFiles={}, errors={}",
                    request.getNamespace(), result.getTotalFiles(), result.getValidFiles(), result.getErrors().size());

            return result;

        } catch (Exception e) {
            log.error("验证过程发生异常: namespace={}, error={}", request.getNamespace(), e.getMessage(), e);
            return createErrorResult(request.getNamespace(), "验证异常: " + e.getMessage(), startTime);
        }
    }

    /**
     * 执行验证
     */
    private ValidationResult performValidation(Bundle bundle, ValidationRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();
        int totalFiles = 0;

        // 验证TM文件
        try {
            BundleResource[] tmResources = bundle.findBundleResources("**/*.tm");
            if (tmResources != null && tmResources.length > 0) {
                totalFiles += tmResources.length;
                log.info("找到 {} 个TM文件", tmResources.length);

                for (BundleResource tmResource : tmResources) {
                    validateTmFile(tmResource, request, errors);
                }
            }
        } catch (Exception e) {
            log.warn("查找TM文件时出错: {}", e.getMessage());
        }

        // 验证QM文件
        try {
            BundleResource[] qmResources = bundle.findBundleResources("**/*.qm");
            if (qmResources != null && qmResources.length > 0) {
                totalFiles += qmResources.length;
                log.info("找到 {} 个QM文件", qmResources.length);

                for (BundleResource qmResource : qmResources) {
                    validateQmFile(qmResource, request, errors);
                }
            }
        } catch (Exception e) {
            log.warn("查找QM文件时出错: {}", e.getMessage());
        }

        // 构建结果
        boolean success = errors.isEmpty();
        int validFiles = totalFiles - errors.size();

        return ValidationResult.builder()
                .success(success)
                .namespace(request.getNamespace())
                .totalFiles(totalFiles)
                .validFiles(validFiles)
                .invalidFiles(errors.size())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * 验证单个TM文件
     */
    private void validateTmFile(BundleResource tmResource, ValidationRequest request, List<ValidationError> errors) {
        String fileName = getRelativePath(tmResource);
        log.debug("验证TM文件: {}", fileName);

        try {
            // 提取模型名称（去掉 .tm 后缀）
            String modelName = extractModelName(fileName);

            // 尝试加载模型（注意：当前版本不支持namespace参数）
            // 假设模型已经通过配置加载到对应的namespace
            tableModelLoaderManager.load(modelName);

            log.debug("TM文件验证通过: {}", fileName);

        } catch (Exception e) {
            log.warn("TM文件验证失败: file={}, error={}", fileName, e.getMessage());

            ValidationError error = ValidationError.builder()
                    .file(fileName)
                    .type("TM")
                    .message(e.getMessage())
                    .code(e.getClass().getSimpleName())
                    .build();

            if (request.isIncludeStackTrace()) {
                error.setStackTrace(getStackTraceString(e));
            }

            errors.add(error);
        }
    }

    /**
     * 验证单个QM文件
     */
    private void validateQmFile(BundleResource qmResource, ValidationRequest request, List<ValidationError> errors) {
        String fileName = getRelativePath(qmResource);
        log.debug("验证QM文件: {}", fileName);

        try {
            // 尝试加载查询模型
            queryModelLoader.loadJdbcQueryModel(qmResource);

            log.debug("QM文件验证通过: {}", fileName);

        } catch (Exception e) {
            log.warn("QM文件验证失败: file={}, error={}", fileName, e.getMessage());

            ValidationError error = ValidationError.builder()
                    .file(fileName)
                    .type("QM")
                    .message(e.getMessage())
                    .code(e.getClass().getSimpleName())
                    .build();

            if (request.isIncludeStackTrace()) {
                error.setStackTrace(getStackTraceString(e));
            }

            errors.add(error);
        }
    }

    /**
     * 生成Bundle名称
     */
    private String generateBundleName(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "external-validation";
        }
        return "external-validation-" + namespace;
    }

    /**
     * 获取文件相对路径
     */
    private String getRelativePath(BundleResource resource) {
        try {
            String description = resource.getResource().getDescription();
            // 提取文件名部分
            int lastSlash = description.lastIndexOf('/');
            int lastBackslash = description.lastIndexOf('\\');
            int lastSeparator = Math.max(lastSlash, lastBackslash);

            if (lastSeparator >= 0) {
                return description.substring(lastSeparator + 1);
            }
            return description;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 从文件名提取模型名称
     */
    private String extractModelName(String fileName) {
        // 移除路径分隔符
        int lastSlash = fileName.lastIndexOf('/');
        int lastBackslash = fileName.lastIndexOf('\\');
        int lastSeparator = Math.max(lastSlash, lastBackslash);

        if (lastSeparator >= 0) {
            fileName = fileName.substring(lastSeparator + 1);
        }

        // 移除 .tm 或 .qm 后缀
        if (fileName.endsWith(".tm")) {
            return fileName.substring(0, fileName.length() - 3);
        } else if (fileName.endsWith(".qm")) {
            return fileName.substring(0, fileName.length() - 3);
        }

        return fileName;
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private String getStackTraceString(Exception e) {
        if (e == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");

        for (StackTraceElement element : e.getStackTrace()) {
            if (sb.length() > 1000) break; // 限制长度
            sb.append("  at ").append(element.toString()).append("\n");
        }

        // 添加cause
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            sb.append("Caused by: ").append(cause.getClass().getName())
                    .append(": ").append(cause.getMessage()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 创建错误结果
     */
    private ValidationResult createErrorResult(String namespace, String errorMessage, long startTime) {
        ValidationError error = ValidationError.simple("system", "SYSTEM", errorMessage);

        return ValidationResult.builder()
                .success(false)
                .namespace(namespace)
                .totalFiles(0)
                .validFiles(0)
                .invalidFiles(1)
                .errors(List.of(error))
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
    }
}
