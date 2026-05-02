package com.foggyframework.dataset.db.model.engine.pivot.rollup;

/**
 * Stage 4: domain 过大时，non-additive rollup 辅助查询拒绝执行。
 *
 * <p>这是一个 fail-closed 异常：当 surviving domain 超过 {@code MAX_IN_LIST_SIZE} 时，
 * 不再静默跳过（跳过会导致 subtotal/grandTotal 把被 TopN 过滤的成员算回去），
 * 而是显式抛出此异常，由调用方决定是否回退到内存路径。</p>
 */
public class NonAdditiveRollupDomainTooLargeException extends RuntimeException {

    private final int domainSize;
    private final int maxAllowed;

    public NonAdditiveRollupDomainTooLargeException(int domainSize, int maxAllowed) {
        super("Non-additive rollup surviving domain too large: size=" + domainSize +
                ", max=" + maxAllowed +
                ". Cannot generate safe tuple constraint. Use memory rollup path instead.");
        this.domainSize = domainSize;
        this.maxAllowed = maxAllowed;
    }

    public int getDomainSize() {
        return domainSize;
    }

    public int getMaxAllowed() {
        return maxAllowed;
    }
}
