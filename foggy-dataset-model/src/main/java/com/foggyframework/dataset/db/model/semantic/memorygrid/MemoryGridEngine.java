package com.foggyframework.dataset.db.model.semantic.memorygrid;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

/**
 * SPI for governed small-result secondary analysis engines.
 */
public interface MemoryGridEngine {

    MemoryGridDialectDescriptor dialect();

    MemoryGridValidation validate(MemoryGridRequest request, SemanticRequestContext context);

    MemoryGridExecutionResult execute(MemoryGridRequest request, SemanticRequestContext context);
}
