package com.foggyframework.bundle.event;

import com.foggyframework.bundle.Bundle;
import org.springframework.context.ApplicationEvent;

/**
 * Bundle移除事件
 *
 * <p>当Bundle从SystemBundlesContext中移除时触发此事件。
 * 监听器可以响应此事件来清理相关资源（如缓存）。
 *
 * @author foggy-framework
 * @since 1.0.0
 */
public class BundleRemovedEvent extends ApplicationEvent {

    private final String bundleName;
    private final String namespace;
    private final Bundle removedBundle;
    private final String committedSourceRevision;
    private final boolean scopeKnown;

    /**
     * 构造Bundle移除事件
     *
     * @param source        事件源
     * @param bundleName    Bundle名称
     * @param namespace     命名空间
     * @param removedBundle 被移除的Bundle实例
     */
    public BundleRemovedEvent(Object source, String bundleName, String namespace, Bundle removedBundle) {
        this(source, bundleName, namespace, removedBundle, null, true);
    }

    public BundleRemovedEvent(
            Object source,
            String bundleName,
            String namespace,
            Bundle removedBundle,
            String committedSourceRevision,
            boolean scopeKnown
    ) {
        super(source);
        this.bundleName = bundleName;
        this.namespace = namespace;
        this.removedBundle = removedBundle;
        this.committedSourceRevision = committedSourceRevision;
        this.scopeKnown = scopeKnown;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getNamespace() {
        return namespace;
    }

    public Bundle getRemovedBundle() {
        return removedBundle;
    }

    public String getCommittedSourceRevision() {
        return committedSourceRevision;
    }

    /** Additive shorthand retained for source-event consumers. */
    public String getSourceRevision() {
        return committedSourceRevision;
    }

    public boolean isScopeKnown() {
        return scopeKnown;
    }

    @Override
    public String toString() {
        return "BundleRemovedEvent{" +
                "bundleName='" + bundleName + '\'' +
                ", namespace='" + namespace + '\'' +
                ", removedBundle=" + removedBundle +
                '}';
    }
}
