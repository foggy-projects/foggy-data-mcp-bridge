package com.foggyframework.dataset.db.model.engine.pivot.transport;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/**
 * Stage 5A Large-Domain Transport Plan
 * Encapsulates the surviving domain axes and tuples to be injected as a relation.
 */
@Getter
@Builder
public class DomainTransportPlan {
    /**
     * The fields involved in the domain (e.g., ["category", "product"])
     */
    private final List<DomainTransportField> fields;

    /**
     * The surviving tuples
     */
    private final List<DomainTransportTuple> tuples;

    void validateForRender() {
        if (fields == null || fields.isEmpty() || tuples == null || tuples.isEmpty()) {
            throw new DomainTransportRefusalException("Empty domain plan");
        }
        for (DomainTransportField field : fields) {
            if (field == null || field.getName() == null || field.getName().isBlank()) {
                throw new DomainTransportRefusalException("Domain transport field name is required");
            }
        }
        int expectedSize = fields.size();
        for (DomainTransportTuple tuple : tuples) {
            if (tuple == null || tuple.getValues() == null || tuple.getValues().size() != expectedSize) {
                throw new DomainTransportRefusalException(
                        "Domain transport tuple arity mismatch. Expected: " + expectedSize);
            }
        }
    }

    int parameterCount() {
        return fields.size() * tuples.size();
    }
}
