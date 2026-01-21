package com.foggyframework.dataset.db.model.spi;

/**
 * 命名空间上下文（ThreadLocal）
 * <p>
 * 用于在查询执行过程中传递namespace，避免修改大量方法签名。
 * </p>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
public class NamespaceContext {

    private static final ThreadLocal<String> namespaceHolder = new ThreadLocal<>();

    /**
     * 设置当前线程的namespace
     *
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     */
    public static void setNamespace(String namespace) {
        namespaceHolder.set(namespace);
    }

    /**
     * 获取当前线程的namespace
     *
     * @return 命名空间（可能为null）
     */
    public static String getNamespace() {
        return namespaceHolder.get();
    }

    /**
     * 清除当前线程的namespace
     * <p>
     * 必须在请求结束时调用，避免线程池复用导致的数据泄漏
     * </p>
     */
    public static void clear() {
        namespaceHolder.remove();
    }
}
