package com.foggyframework.bundle.external;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部Bundle配置属性
 *
 * <p>用于配置从外部文件系统加载数据模型的Bundle。
 * 支持配置多个外部目录，每个目录对应一个独立的Bundle。
 *
 * <h3>配置示例：</h3>
 * <pre>
 * foggy:
 *   bundle:
 *     external:
 *       enabled: true
 *       bundles:
 *         - name: external-models
 *           path: /data/foggy-models
 *           watch: true
 *         - name: custom-models
 *           path: ${CUSTOM_MODELS_PATH:/app/custom-models}
 *           watch: false
 * </pre>
 *
 * <h3>Docker部署示例：</h3>
 * <pre>
 * docker run -v /host/models:/data/foggy-models \
 *            -e FOGGY_BUNDLE_EXTERNAL_BUNDLES_0_NAME=external-models \
 *            -e FOGGY_BUNDLE_EXTERNAL_BUNDLES_0_PATH=/data/foggy-models \
 *            foggy-mcp
 * </pre>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "foggy.bundle.external")
public class ExternalBundleProperties {

    /**
     * 是否启用外部Bundle加载
     */
    private boolean enabled = false;

    /**
     * 外部Bundle配置列表
     */
    private List<ExternalBundleItem> bundles = new ArrayList<>();

    /**
     * 外部Bundle配置项
     */
    @Data
    public static class ExternalBundleItem {
        /**
         * Bundle名称（唯一标识）
         *
         * <p>用于在系统中标识此外部Bundle，不能与其他Bundle重名。
         */
        private String name;

        /**
         * 外部目录路径
         *
         * <p>指向包含数据模型文件的目录。
         * 目录结构应为：
         * <pre>
         * {path}/
         *   ├── model/
         *   │   ├── XxxModel.tm
         *   │   └── YyyModel.tm
         *   ├── query/
         *   │   ├── XxxQueryModel.qm
         *   │   └── YyyQueryModel.qm
         *   └── dicts.fsscript
         * </pre>
         *
         * <p>支持环境变量：${ENV_VAR:/default/path}
         */
        private String path;

        /**
         * 是否监听文件变化
         *
         * <p>启用后，当目录中的文件发生变化时，会自动重新加载。
         * 适用于开发环境，生产环境建议关闭以提高性能。
         */
        private boolean watch = false;
    }
}
