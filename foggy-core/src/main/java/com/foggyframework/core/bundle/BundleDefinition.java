package com.foggyframework.core.bundle;

public interface BundleDefinition {
    String getPackageName();

    String getName();

    /**
     * 获取命名空间
     * <p>默认返回空字符串表示默认命名空间。
     * <p>不同命名空间下可以存在同名模型（用于dev/test环境隔离）。
     *
     * @return 命名空间（空字符串表示默认命名空间）
     */
    default String getNamespace() {
        return "";
    }

    default Class<?> getDefinitionClass() {
        return getClass();
    }
}
