package com.foggyframework.dataset.db.model.engine.compose.normalization;

/**
 * Options for bounded compose plan normalization.
 */
public final class PlanNormalizeOptions {

    public static final int DEFAULT_MAX_LOOP_COUNT = 4;

    private final int maxLoopCount;

    private PlanNormalizeOptions(Builder b) {
        if (b.maxLoopCount < 0) {
            throw new IllegalArgumentException("PlanNormalizeOptions.maxLoopCount must be >= 0");
        }
        this.maxLoopCount = b.maxLoopCount;
    }

    public int maxLoopCount() {
        return maxLoopCount;
    }

    public static PlanNormalizeOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxLoopCount = DEFAULT_MAX_LOOP_COUNT;

        public Builder maxLoopCount(int v) {
            this.maxLoopCount = v;
            return this;
        }

        public PlanNormalizeOptions build() {
            return new PlanNormalizeOptions(this);
        }
    }
}
