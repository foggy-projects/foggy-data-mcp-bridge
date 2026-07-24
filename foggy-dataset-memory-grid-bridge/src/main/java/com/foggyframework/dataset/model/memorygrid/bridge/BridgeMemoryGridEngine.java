package com.foggyframework.dataset.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridDialectDescriptor;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridEngine;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridExecutionResult;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridRequest;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridValidation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Current scoped Memory Grid implementation over governed result handles.
 */
public class BridgeMemoryGridEngine implements MemoryGridEngine {

    private static final MemoryGridDialectDescriptor DIALECT = new MemoryGridDialectDescriptor(
            "memory-grid-bridge",
            "foggy-memory-grid-plan-v1",
            true,
            false,
            List.of("two governed result_handle inputs", "single-key inner join", "binary numeric derived formula"),
            List.of("free grid_sql", "physical tables", "DML/DDL", "window functions", "unbounded rows"),
            MemoryGridGuardrailValidator.productionGuardDescriptor());

    private MemoryGridResultResolver resultResolver;

    public BridgeMemoryGridEngine() {
    }

    public BridgeMemoryGridEngine(MemoryGridResultResolver resultResolver) {
        this.resultResolver = resultResolver;
    }

    public void setResultResolver(MemoryGridResultResolver resultResolver) {
        this.resultResolver = resultResolver;
    }

    @Override
    public MemoryGridDialectDescriptor dialect() {
        return DIALECT;
    }

    @Override
    public MemoryGridValidation validate(MemoryGridRequest request, SemanticRequestContext context) {
        return new MemoryGridValidation(validationEvidence(request, context).validation());
    }

    @Override
    public MemoryGridExecutionResult execute(MemoryGridRequest request, SemanticRequestContext context) {
        ValidationEvidence evidence = validationEvidence(request, context);
        if (!evidence.bridgePlan().ready()) {
            throw RX.throwB("MEMORY_GRID_BRIDGE_NOT_SUPPORTED: " + evidence.bridgePlan().unsupported());
        }
        MemoryGridExecutor.ExecutionResult result = MemoryGridExecutor.execute(
                request.plan(), evidence.bridgePlan(), resultResolver, context);
        Map<String, Object> validation = new LinkedHashMap<>(evidence.validation());
        validation.put("memory_grid_execution_summary", result.summary());
        return new MemoryGridExecutionResult(result.rows(), validation, result.summary());
    }

    private ValidationEvidence validationEvidence(MemoryGridRequest request, SemanticRequestContext context) {
        if (request != null && request.gridSql() != null) {
            throw RX.throwB("MEMORY_GRID_GRID_SQL_NOT_SUPPORTED: memory-grid-bridge only supports governed memory_grid_plan requests.");
        }
        if (request == null || request.plan() == null || request.plan().isEmpty()) {
            throw RX.throwB("MEMORY_GRID_UNBOUNDED_INPUT: memory_grid_plan must be provided for MEMORY_GRID route.");
        }
        Map<String, Object> validation = new LinkedHashMap<>(
                MemoryGridGuardrailValidator.validate(request.plan(), context));
        MemoryGridExecutablePlanner.BridgePlan bridgePlan = MemoryGridExecutablePlanner.plan(request.plan());
        appendMemoryGridBridgeEvidence(validation, bridgePlan);
        validation.put("memory_grid_engine", dialect().engineId());
        validation.put("memory_grid_dialect", dialect().dialectId());
        return new ValidationEvidence(validation, bridgePlan);
    }

    private void appendMemoryGridBridgeEvidence(Map<String, Object> validation,
                                                MemoryGridExecutablePlanner.BridgePlan bridgePlan) {
        validation.put("memory_grid_bridge_status", bridgePlan.status());
        if (bridgePlan.ready()) {
            validation.put("memory_grid_bridge_output", bridgePlan.outputColumns());
            validation.put("memory_grid_bridge_derived", bridgePlan.derived().stream()
                    .map(MemoryGridExecutablePlanner.DerivedFormula::name)
                    .toList());
        } else {
            validation.put("memory_grid_bridge_unsupported", bridgePlan.unsupported());
        }
    }

    private record ValidationEvidence(Map<String, Object> validation,
                                      MemoryGridExecutablePlanner.BridgePlan bridgePlan) {
    }
}
