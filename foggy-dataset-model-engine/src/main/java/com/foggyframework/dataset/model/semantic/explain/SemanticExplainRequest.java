package com.foggyframework.dataset.model.semantic.explain;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Input contract for an on-demand semantic explanation. */
@Data
@NoArgsConstructor
public class SemanticExplainRequest {

    private List<String> fields;
    private SemanticQueryRequest payload;
    private Depth depth = Depth.STANDARD;
    private boolean includeSql;
    private boolean includePhysicalNames;

    public enum Depth {
        SUMMARY,
        STANDARD,
        DETAILED
    }
}
