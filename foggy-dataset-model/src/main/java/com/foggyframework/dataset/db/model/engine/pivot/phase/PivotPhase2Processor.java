package com.foggyframework.dataset.db.model.engine.pivot.phase;

/**
 * Phase 2 处理器接口。
 *
 * <p>每个处理器负责 Pivot 内存加工管线的一个子步骤。
 * 处理器按注册顺序串行执行，通过共享 {@link PivotPhase2Context} 传递中间状态。</p>
 */
@FunctionalInterface
public interface PivotPhase2Processor {

    /**
     * 执行处理步骤，可修改 ctx 中的可变状态（resultSet、domains、rollup 等）。
     *
     * @param ctx 共享上下文
     */
    void process(PivotPhase2Context ctx);
}
