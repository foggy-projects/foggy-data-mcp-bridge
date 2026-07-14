package com.foggyframework.dataset.db.model.spi;

/**
 * 可嵌套、线程绑定的 namespace 作用域。
 *
 * <p>scope 必须由创建线程按 LIFO 顺序关闭。wrong-thread 或乱序关闭会快速失败且
 * 不修改当前栈；成功关闭后的重复 close 是幂等 no-op。</p>
 *
 * @since 9.3.3
 */
public final class NamespaceScope implements AutoCloseable {

    private final NamespaceContext.ScopeFrame frame;

    NamespaceScope(NamespaceContext.ScopeFrame frame) {
        this.frame = frame;
    }

    @Override
    public void close() {
        NamespaceContext.close(frame);
    }
}
