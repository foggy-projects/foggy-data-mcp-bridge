package com.foggyframework.bundle.event;

import com.foggyframework.bundle.Bundle;
import org.springframework.context.ApplicationEvent;

/**
 * Bundle添加事件
 *
 * <p>当新的Bundle注册到SystemBundlesContext时触发此事件。
 * 监听器可以响应此事件来执行初始化操作。
 *
 * @author foggy-framework
 * @since 1.0.0
 */
public class BundleAddedEvent extends ApplicationEvent {

    private final String bundleName;
    private final String namespace;
    private final Bundle addedBundle;

    /**
     * 构造Bundle添加事件
     *
     * @param source      事件源
     * @param bundleName  Bundle名称
     * @param namespace   命名空间
     * @param addedBundle 新添加的Bundle实例
     */
    public BundleAddedEvent(Object source, String bundleName, String namespace, Bundle addedBundle) {
        super(source);
        this.bundleName = bundleName;
        this.namespace = namespace;
        this.addedBundle = addedBundle;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getNamespace() {
        return namespace;
    }

    public Bundle getAddedBundle() {
        return addedBundle;
    }

    @Override
    public String toString() {
        return "BundleAddedEvent{" +
                "bundleName='" + bundleName + '\'' +
                ", namespace='" + namespace + '\'' +
                ", addedBundle=" + addedBundle +
                '}';
    }
}
