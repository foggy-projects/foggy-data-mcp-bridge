package com.foggyframework.dataset.mcp.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.dataset.db.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.db.model.validation.DetachedModelValidationSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 语义层验证服务
 *
 * <p>提供语义层文件（TM/QM）的验证功能，支持：
 * <ul>
 *   <li>创建请求级隔离的外部 Bundle</li>
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

    private final DetachedModelValidationFactory detachedModelValidationFactory;

    @Autowired
    public SemanticLayerValidationService(
            DetachedModelValidationFactory detachedModelValidationFactory
    ) {
        this.detachedModelValidationFactory = detachedModelValidationFactory;
    }

    /** Compatibility constructor for legacy reflective callers. */
    @Deprecated(since = "9.3.5", forRemoval = false)
    public SemanticLayerValidationService() {
        this.detachedModelValidationFactory = null;
    }

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
            if (detachedModelValidationFactory == null) {
                return createErrorResult(
                        request.getNamespace(),
                        "Detached model validation factory is unavailable",
                        startTime);
            }

            // 2. 打开请求级隔离验证会话；watch/clearExisting 仅保留请求兼容，
            // 不再把临时 bundle 注册到 live context。
            String bundleName = generateBundleName(request.getNamespace());
            try (DetachedModelValidationSession validationSession =
                         detachedModelValidationFactory.open(
                                 bundleName,
                                 request.getNamespace(),
                                 request.getPath())) {
                Bundle bundle = validationSession.sourceBundle();
                if (bundle == null) {
                    return createErrorResult(
                            request.getNamespace(),
                            "无法创建隔离验证Bundle: " + bundleName,
                            startTime);
                }

                // 3. 验证TM和QM文件
                ValidationResult result = performValidation(
                        validationSession, bundle, request);

                // 4. 设置耗时
                result.setDurationMs(System.currentTimeMillis() - startTime);

                log.info("验证完成: namespace={}, totalFiles={}, validFiles={}, errors={}",
                        request.getNamespace(), result.getTotalFiles(), result.getValidFiles(), result.getErrors().size());

                return result;
            }

        } catch (Exception e) {
            log.error("验证过程发生异常: namespace={}, error={}", request.getNamespace(), e.getMessage(), e);
            return createErrorResult(request.getNamespace(), "验证异常: " + e.getMessage(), startTime);
        }
    }

    /**
     * 执行验证
     */
    private ValidationResult performValidation(
            DetachedModelValidationSession validationSession,
            Bundle bundle,
            ValidationRequest request
    ) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();
        Set<String> failedTmNames = new HashSet<>();
        int totalFiles = 0;

        // 验证TM文件
        try {
            BundleResource[] tmResources = bundle.findBundleResources("**/*.tm");
            if (tmResources != null && tmResources.length > 0) {
                totalFiles += tmResources.length;
                log.info("找到 {} 个TM文件", tmResources.length);

                for (BundleResource tmResource : tmResources) {
                    int beforeSize = errors.size();
                    validateTmFile(validationSession, tmResource, request, errors);
                    if (errors.size() > beforeSize) {
                        String modelName = extractModelName(getRelativePath(tmResource));
                        failedTmNames.add(modelName);
                    }
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
                    int beforeSize = errors.size();
                    validateQmFile(validationSession, qmResource, request, errors);
                    // 检查新增的QM错误是否由上游TM失败导致
                    if (errors.size() > beforeSize && !failedTmNames.isEmpty()) {
                        for (int i = beforeSize; i < errors.size(); i++) {
                            ValidationError err = errors.get(i);
                            if (isCascadingError(err, failedTmNames)) {
                                err.setCategory("CASCADING");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查找QM文件时出错: {}", e.getMessage());
        }

        // 统计级联错误数
        int cascadingErrors = (int) errors.stream()
                .filter(e -> "CASCADING".equals(e.getCategory()))
                .count();

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
                .cascadingErrors(cascadingErrors)
                .build();
    }

    /**
     * 判断QM错误是否由上游TM失败级联导致
     */
    private boolean isCascadingError(ValidationError error, Set<String> failedTmNames) {
        if (error.getMessage() == null) return false;
        for (String tmName : failedTmNames) {
            if (error.getMessage().contains(tmName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证单个TM文件
     */
    private void validateTmFile(
            DetachedModelValidationSession validationSession,
            BundleResource tmResource,
            ValidationRequest request,
            List<ValidationError> errors
    ) {
        String fileName = getRelativePath(tmResource);
        log.debug("验证TM文件: {}", fileName);

        try {
            validationSession.validateTableModel(tmResource, request.getNamespace());

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
    private void validateQmFile(
            DetachedModelValidationSession validationSession,
            BundleResource qmResource,
            ValidationRequest request,
            List<ValidationError> errors
    ) {
        String fileName = getRelativePath(qmResource);
        log.debug("验证QM文件: {}", fileName);

        try {
            // 尝试加载查询模型
            validationSession.validateQueryModel(qmResource);

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
            File file = resource.getResource().getFile();
            String rootPath = resource.getBundle().getRootPath();
            if (rootPath != null && file != null) {
                Path root = Paths.get(rootPath);
                Path filePath = file.toPath();
                if (filePath.startsWith(root)) {
                    return root.relativize(filePath).toString().replace('\\', '/');
                }
            }
        } catch (Exception ignored) {}
        try {
            String filename = resource.getResource().getFilename();
            if (filename != null) return filename;
        } catch (Exception ignored) {}
        return "unknown";
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
