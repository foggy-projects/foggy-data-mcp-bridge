package com.foggyframework.bundle.external;

import com.foggyframework.core.bundle.BundleDefinition;
import lombok.Getter;

/**
 * 外部Bundle定义
 *
 * <p>用于定义从外部文件系统加载的Bundle。
 * 与classpath中的Bundle不同，外部Bundle直接指向文件系统路径。
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Getter
public class ExternalBundleDefinition implements BundleDefinition {

    /**
     * Bundle名称
     */
    private final String name;

    /**
     * 命名空间（用于模型隔离）
     */
    private final String namespace;

    /**
     * 外部目录路径
     */
    private final String path;

    /**
     * 是否监听文件变化
     */
    private final boolean watch;

    /**
     * 虚拟包名（用于与现有Bundle机制兼容）
     */
    private final String packageName;

    public ExternalBundleDefinition(String name, String path, boolean watch) {
        this(name, "", path, watch);
    }

    public ExternalBundleDefinition(String name, String namespace, String path, boolean watch) {
        this.name = name;
        this.namespace = namespace != null ? namespace : "";
        this.path = path;
        this.watch = watch;
        // 使用 "external." 前缀作为虚拟包名，避免与实际Java包冲突
        this.packageName = "external." + name;
    }

    public ExternalBundleDefinition(ExternalBundleProperties.ExternalBundleItem item) {
        this(item.getName(), item.getNamespace(), item.getPath(), item.isWatch());
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Class<?> getDefinitionClass() {
        // 外部Bundle没有对应的Java类，返回此定义类本身
        return ExternalBundleDefinition.class;
    }

    @Override
    public String toString() {
        return String.format("ExternalBundleDefinition{name='%s', namespace='%s', path='%s', watch=%s}",
                name, namespace, path, watch);
    }
}
