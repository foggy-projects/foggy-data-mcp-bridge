package com.foggyframework.bundle.external;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.loader.BundleLoader;
import com.foggyframework.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 外部Bundle加载器
 *
 * <p>负责将配置的外部目录注册为Bundle，使其可以被系统的模型加载机制识别。
 *
 * <h3>工作流程：</h3>
 * <ol>
 *   <li>读取 {@link ExternalBundleProperties} 配置</li>
 *   <li>验证每个外部目录是否存在且可访问</li>
 *   <li>为每个有效目录创建 {@link ExternalFileBundle} 实例</li>
 *   <li>将Bundle注册到 {@link SystemBundlesContext}</li>
 * </ol>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Slf4j
public class ExternalBundleLoader extends BundleLoader<ExternalBundleDefinition> {

    public ExternalBundleLoader(List<ExternalBundleDefinition> bundleDefList) {
        super(bundleDefList);
    }

    /**
     * 从配置属性创建ExternalBundleLoader
     *
     * @param properties 外部Bundle配置
     * @return ExternalBundleLoader实例，如果未启用或无配置则返回null
     */
    public static ExternalBundleLoader fromProperties(ExternalBundleProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            log.debug("外部Bundle加载未启用");
            return null;
        }

        List<ExternalBundleProperties.ExternalBundleItem> items = properties.getBundles();
        if (items == null || items.isEmpty()) {
            log.debug("没有配置任何外部Bundle");
            return null;
        }

        List<ExternalBundleDefinition> definitions = items.stream()
                .filter(item -> validateItem(item))
                .map(ExternalBundleDefinition::new)
                .collect(Collectors.toList());

        if (definitions.isEmpty()) {
            log.warn("所有配置的外部Bundle都无效");
            return null;
        }

        return new ExternalBundleLoader(definitions);
    }

    /**
     * 验证配置项是否有效
     */
    private static boolean validateItem(ExternalBundleProperties.ExternalBundleItem item) {
        if (item == null) {
            return false;
        }

        if (StringUtils.isEmpty(item.getName())) {
            log.warn("外部Bundle配置缺少name属性，已跳过");
            return false;
        }

        if (StringUtils.isEmpty(item.getPath())) {
            log.warn("外部Bundle[{}]配置缺少path属性，已跳过", item.getName());
            return false;
        }

        File dir = new File(item.getPath());
        if (!dir.exists()) {
            log.warn("外部Bundle[{}]路径不存在: {}，已跳过", item.getName(), item.getPath());
            return false;
        }

        if (!dir.isDirectory()) {
            log.warn("外部Bundle[{}]路径不是目录: {}，已跳过", item.getName(), item.getPath());
            return false;
        }

        if (!dir.canRead()) {
            log.warn("外部Bundle[{}]路径无读取权限: {}，已跳过", item.getName(), item.getPath());
            return false;
        }

        log.info("外部Bundle[{}]配置有效: {}", item.getName(), item.getPath());
        return true;
    }

    @Override
    public void load(SystemBundlesContext systemBundlesContext) {
        if (bundleDefList == null || bundleDefList.isEmpty()) {
            log.debug("没有外部Bundle需要加载");
            return;
        }

        log.info("开始加载 {} 个外部Bundle", bundleDefList.size());

        for (ExternalBundleDefinition definition : bundleDefList) {
            try {
                ExternalFileBundle bundle = new ExternalFileBundle(systemBundlesContext);
                bundle.setName(definition.getName());
                bundle.setBundleDefinition(definition);
                bundle.setBasePath(definition.getPath());
                bundle.setRootPath(definition.getPath());

                systemBundlesContext.regBundle(bundle);

                log.info("已加载外部Bundle: {} -> {}", definition.getName(), definition.getPath());

            } catch (Exception e) {
                log.error("加载外部Bundle[{}]失败", definition.getName(), e);
            }
        }
    }
}
