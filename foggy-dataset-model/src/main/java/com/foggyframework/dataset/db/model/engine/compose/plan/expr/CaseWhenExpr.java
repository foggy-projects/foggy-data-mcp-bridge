package com.foggyframework.dataset.db.model.engine.compose.plan.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A CASE WHEN expression.
 *
 * @since 8.2.0.beta
 */
public final class CaseWhenExpr implements PlanExpression {
    private final List<WhenThen> whens;
    private final PlanExpression elseExpr;

    public CaseWhenExpr(List<WhenThen> whens, PlanExpression elseExpr) {
        if (whens == null || whens.isEmpty()) {
            throw new IllegalArgumentException("CaseWhenExpr requires at least one WHEN clause");
        }
        this.whens = List.copyOf(whens);
        this.elseExpr = elseExpr; // nullable
    }

    public List<WhenThen> whens() { return whens; }
    public PlanExpression elseExpr() { return elseExpr; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaseWhenExpr that)) return false;
        return Objects.equals(whens, that.whens) &&
               Objects.equals(elseExpr, that.elseExpr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(whens, elseExpr);
    }

    @Override
    public String toString() {
        return "CaseWhenExpr(whens=" + whens + ", else=" + elseExpr + ")";
    }

    public static final class WhenThen {
        private final PlanExpression condition;
        private final PlanExpression result;

        public WhenThen(PlanExpression condition, PlanExpression result) {
            if (condition == null || result == null) {
                throw new IllegalArgumentException("WhenThen requires non-null condition and result");
            }
            this.condition = condition;
            this.result = result;
        }

        public PlanExpression condition() { return condition; }
        public PlanExpression result() { return result; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WhenThen that)) return false;
            return Objects.equals(condition, that.condition) &&
                   Objects.equals(result, that.result);
        }

        @Override
        public int hashCode() {
            return Objects.hash(condition, result);
        }

        @Override
        public String toString() {
            return "WHEN " + condition + " THEN " + result;
        }
    }
}
