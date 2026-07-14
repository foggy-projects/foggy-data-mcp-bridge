package com.foggyframework.dataset.db.model.spi;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当前线程的命名空间上下文。
 *
 * <p>生产代码应通过 {@link #open(String)} 或 {@link #openInherited()} 建立
 * {@link NamespaceScope}，以确保嵌套调用在正常返回、异常和 early return 时都能
 * 精确恢复上一层状态。</p>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
public class NamespaceContext {

    private static final NamespaceValue DEFAULT_NAMESPACE = new NamespaceValue("");
    private static final ThreadLocal<ContextState> CONTEXT = new ThreadLocal<>();
    private static final String WRONG_THREAD = "NAMESPACE_SCOPE_WRONG_THREAD";
    private static final String OUT_OF_ORDER = "NAMESPACE_SCOPE_OUT_OF_ORDER";
    private static final String LEGACY_MUTATION_WHILE_ACTIVE =
            "NAMESPACE_SCOPE_LEGACY_MUTATION_WHILE_ACTIVE";

    /**
     * 兼容旧调用设置当前 namespace。
     *
     * <p>{@code null} 保持旧语义，表示 unset；非 null 值保持原样。
     * 新生产代码应改用 scope API 完成 canonicalization 和嵌套恢复。</p>
     *
     * @param namespace namespace；null 表示 unset
     * @throws IllegalStateException active scope 内禁止兼容 mutation
     * @deprecated 生产代码请使用 {@link #open(String)} 或 {@link #openInherited()}
     */
    @Deprecated(since = "9.3.3")
    public static void setNamespace(String namespace) {
        rejectLegacyMutationWhileActive();
        setCurrent(namespace == null ? null : new NamespaceValue(namespace));
    }

    /**
     * 获取当前线程的 namespace。
     *
     * @return unset 时为 null；scope 内返回 canonical 值，兼容 set 写入的非 null 值保持原样
     */
    public static String getNamespace() {
        ContextState state = CONTEXT.get();
        return state == null || state.current == null ? null : state.current.namespace;
    }

    /**
     * 打开一个显式 namespace scope。
     *
     * <p>{@code null} 或 blank 都表示显式 default，并会遮蔽外层 named namespace。</p>
     *
     * @param namespace 显式 namespace
     * @return 必须由创建线程按 LIFO 关闭的 scope
     */
    public static NamespaceScope open(String namespace) {
        return push(explicitValue(namespace));
    }

    /**
     * 打开一个继承 scope。
     *
     * <p>已有上下文时继承其 canonical effective 值；root/unset 时进入显式 default。
     * 兼容 API 写入的 raw 值会在 scope 内 canonicalize，并在 close 后精确恢复。</p>
     *
     * @return 必须由创建线程按 LIFO 关闭的 scope
     */
    public static NamespaceScope openInherited() {
        ContextState state = CONTEXT.get();
        NamespaceValue inherited = state == null || state.current == null
                ? DEFAULT_NAMESPACE
                : explicitValue(state.current.namespace);
        return push(inherited);
    }

    /**
     * 兼容旧调用清除当前 namespace。
     *
     * <p>新生产代码应通过关闭 {@link NamespaceScope} 恢复上下文。</p>
     *
     * @throws IllegalStateException active scope 内禁止兼容 mutation
     * @deprecated 生产代码请使用 try-with-resources 管理 {@link NamespaceScope}
     */
    @Deprecated(since = "9.3.3")
    public static void clear() {
        rejectLegacyMutationWhileActive();
        setCurrent(null);
    }

    private static NamespaceScope push(NamespaceValue next) {
        ContextState state = CONTEXT.get();
        if (state == null) {
            state = new ContextState();
            CONTEXT.set(state);
        }

        ScopeFrame frame = new ScopeFrame(Thread.currentThread(), state.current);
        state.frames.push(frame);
        state.current = next;
        return new NamespaceScope(frame);
    }

    static void close(ScopeFrame frame) {
        if (frame.owner != Thread.currentThread()) {
            throw new IllegalStateException(WRONG_THREAD);
        }
        if (frame.closed) {
            return;
        }

        ContextState state = CONTEXT.get();
        if (state == null || state.frames.peek() != frame) {
            throw new IllegalStateException(OUT_OF_ORDER);
        }

        state.frames.pop();
        state.current = frame.previous;
        frame.closed = true;
        removeIfEmpty(state);
    }

    private static NamespaceValue explicitValue(String namespace) {
        if (namespace == null) {
            return DEFAULT_NAMESPACE;
        }
        String normalized = namespace.trim();
        return normalized.isEmpty() ? DEFAULT_NAMESPACE : new NamespaceValue(normalized);
    }

    private static void rejectLegacyMutationWhileActive() {
        ContextState state = CONTEXT.get();
        if (state != null && !state.frames.isEmpty()) {
            throw new IllegalStateException(LEGACY_MUTATION_WHILE_ACTIVE);
        }
    }

    private static void setCurrent(NamespaceValue value) {
        ContextState state = CONTEXT.get();
        if (state == null) {
            if (value == null) {
                return;
            }
            state = new ContextState();
            CONTEXT.set(state);
        }
        state.current = value;
        removeIfEmpty(state);
    }

    private static void removeIfEmpty(ContextState state) {
        if (state.current == null && state.frames.isEmpty()) {
            CONTEXT.remove();
        }
    }

    static final class ScopeFrame {
        private final Thread owner;
        private final NamespaceValue previous;
        private boolean closed;

        private ScopeFrame(Thread owner, NamespaceValue previous) {
            this.owner = owner;
            this.previous = previous;
        }
    }

    private static final class ContextState {
        private final Deque<ScopeFrame> frames = new ArrayDeque<>();
        private NamespaceValue current;
    }

    private static final class NamespaceValue {
        private final String namespace;

        private NamespaceValue(String namespace) {
            this.namespace = namespace;
        }
    }
}
