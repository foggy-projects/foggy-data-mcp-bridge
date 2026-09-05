package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;

import java.util.List;
import java.util.stream.Collectors;

/** Raised before execution when Query DSL input-property handling must fail closed. */
public class QueryInputValidationException extends IllegalArgumentException {

    private final String code;
    private final List<QueryInputWarning> violations;

    public QueryInputValidationException(String code, List<QueryInputWarning> violations) {
        super(buildMessage(code, violations));
        this.code = code;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public String getCode() {
        return code;
    }

    public List<QueryInputWarning> getViolations() {
        return violations;
    }

    private static String buildMessage(String code, List<QueryInputWarning> violations) {
        String paths = violations == null ? "" : violations.stream()
                .map(QueryInputWarning::path)
                .limit(20)
                .collect(Collectors.joining(", "));
        return code + ": invalid Query DSL input properties"
                + (paths.isEmpty() ? "" : " at " + paths);
    }
}
