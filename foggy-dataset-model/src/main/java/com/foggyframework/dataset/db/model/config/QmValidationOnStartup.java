package com.foggyframework.dataset.db.model.config;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * QM 启动时校验组件
 *
 * <p>在应用启动时加载并校验所有 QM 文件，提前发现配置错误。
 *
 * <p>配置方式：
 * <pre>
 * foggy:
 *   dataset:
 *     validate-on-startup: true
 * </pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "foggy.dataset.validate-on-startup", havingValue = "true")
public class QmValidationOnStartup implements ApplicationRunner {

    private final QueryModelLoader queryModelLoader;
    private final SystemBundlesContext systemBundlesContext;

    public QmValidationOnStartup(QueryModelLoader queryModelLoader,
                                  SystemBundlesContext systemBundlesContext) {
        this.queryModelLoader = queryModelLoader;
        this.systemBundlesContext = systemBundlesContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== QM 启动校验开始 ==========");

        List<BundleResource> qmFiles = findAllQmFiles();
        if (qmFiles.isEmpty()) {
            log.info("未找到 QM 文件");
            log.info("========== QM 启动校验完成 ==========");
            return;
        }

        log.info("找到 {} 个 QM 文件", qmFiles.size());

        List<String> errors = new ArrayList<>();
        int successCount = 0;

        for (BundleResource qmFile : qmFiles) {
            String path = qmFile.getResource().getDescription();
            try {
                queryModelLoader.loadJdbcQueryModel(qmFile);
                successCount++;
                log.debug("QM 校验通过: {}", path);
            } catch (Exception e) {
                String errorMsg = String.format("QM [%s]: %s", path, e.getMessage());
                errors.add(errorMsg);
                log.error("QM 校验失败: {}", path, e);
            }
        }

        log.info("校验结果: 成功 {}, 失败 {}", successCount, errors.size());

        if (!errors.isEmpty()) {
            log.error("========== QM 校验失败详情 ==========");
            for (String error : errors) {
                log.error("  {}", error);
            }
            log.error("=======================================");

            // 抛出异常阻止应用启动
            throw new RuntimeException(String.format(
                    "QM 启动校验失败: %d 个文件有错误。详情请查看日志。", errors.size()));
        }

        log.info("========== QM 启动校验完成 ==========");
    }

    /**
     * 查找所有 QM 文件
     */
    private List<BundleResource> findAllQmFiles() {
        List<BundleResource> result = new ArrayList<>();

        try {
            // 从所有 bundle 中查找 .qm 文件
            systemBundlesContext.getBundleList().forEach(bundle -> {
                try {
                    BundleResource[] resources = bundle.findBundleResources("**/*.qm");
                    if (resources != null) {
                        result.addAll(java.util.Arrays.asList(resources));
                    }
                } catch (Exception e) {
                    log.warn("从 bundle {} 查找 QM 文件时出错: {}", bundle.getName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("查找 QM 文件时出错: {}", e.getMessage());
        }

        return result;
    }
}
