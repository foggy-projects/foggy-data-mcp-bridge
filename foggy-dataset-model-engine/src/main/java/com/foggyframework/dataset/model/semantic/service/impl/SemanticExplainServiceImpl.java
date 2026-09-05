package com.foggyframework.dataset.model.semantic.service.impl;

import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.dataset.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.join.JoinEdge;
import com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.model.impl.utils.ViewSqlQueryObject;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.explain.ExplainTraceCollector;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainCompilation;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainRequest;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainService;
import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolution;
import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolver;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionService;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.permission.PermissionDecision;
import com.foggyframework.dataset.model.semantic.permission.PermissionPredicate;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbDimensionColumn;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.DbMeasureColumn;
import com.foggyframework.dataset.model.spi.DbPropertyColumn;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse.Basis;
import static com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse.Confidence;
import static com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse.ConditionOrigin;
import static com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse.StageStatus;

/** Default Java-engine implementation of definition and recompilation explain. */
@Service
public class SemanticExplainServiceImpl implements SemanticExplainService {

    private static final Pattern REFERENCE_TOKEN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private final SemanticQueryServiceV3 semanticQueryService;
    private final QueryModelLoader queryModelLoader;
    private final CatalogSnapshotStore catalogSnapshotStore;
    private final ModelPermissionService modelPermissionService;
    private final FieldPermissionResolver fieldPermissionResolver;

    public SemanticExplainServiceImpl(
            SemanticQueryServiceV3 semanticQueryService,
            QueryModelLoader queryModelLoader,
            CatalogSnapshotStore catalogSnapshotStore,
            ModelPermissionService modelPermissionService,
            FieldPermissionResolver fieldPermissionResolver
    ) {
        this.semanticQueryService = semanticQueryService;
        this.queryModelLoader = queryModelLoader;
        this.catalogSnapshotStore = catalogSnapshotStore;
        this.modelPermissionService = modelPermissionService;
        this.fieldPermissionResolver = fieldPermissionResolver;
    }

    @Override
    public SemanticExplainResponse explain(
            String queryModelName,
            SemanticExplainRequest request,
            SemanticRequestContext context
    ) {
        String requestedModel = requireText(queryModelName, "queryModel");
        SemanticExplainRequest safeRequest = request == null
                ? new SemanticExplainRequest()
                : request;
        SemanticRequestContext baseContext = context == null
                ? SemanticRequestContext.empty()
                : context;
        ExplainTraceCollector collector = new ExplainTraceCollector();

        CatalogResolution<QueryModel> resolution =
                queryModelLoader.resolveJdbcQueryModel(requestedModel, baseContext.getNamespace());
        QueryModel queryModel = resolution != null
                ? resolution.model()
                : queryModelLoader.getJdbcQueryModel(requestedModel, baseContext.getNamespace());
        if (queryModel == null) {
            throw new IllegalArgumentException("Model not found: " + requestedModel);
        }

        String canonicalName = resolution != null ? resolution.canonicalName() : queryModel.getName();
        SemanticRequestContext pinnedContext = baseContext
                .withExplainTraceCollector(collector);
        if (resolution != null) {
            pinnedContext = pinnedContext.withCatalogResolution(resolution);
        }

        List<SemanticExplainResponse.Limitation> limitations = new ArrayList<>();
        CatalogSnapshot snapshot = catalogSnapshotStore
                .readCurrent(baseContext.getNamespace())
                .orElse(null);
        ModelProvenance provenance = snapshot == null
                ? null
                : snapshot.queryModelProvenance(canonicalName).orElse(null);
        if (resolution == null || provenance == null) {
            limitations.add(limitation(
                    "CATALOG_PROVENANCE_UNAVAILABLE",
                    "The active loader did not provide complete immutable catalog provenance.",
                    Confidence.OPAQUE));
        }

        PermissionDecision describeDecision = modelPermissionService.evaluate(
                queryModel,
                baseContext.getNamespace(),
                PermissionAction.DESCRIBE,
                baseContext.getRequestIdentity(),
                baseContext.getPermissionSession());
        if (!describeDecision.isAllow()) {
            throw ModelPermissionException.denied();
        }
        SemanticRequestContext describeContext = baseContext.withPermissionAttributes(
                describeDecision.getAttributes());
        FieldPermissionResolution fieldPermissions = fieldPermissionResolver.resolve(
                queryModel,
                baseContext.getNamespace(),
                describeContext.getSecurityContext(),
                baseContext.getFieldAccess(),
                baseContext.getDeniedColumns());

        SemanticExplainResponse.DefinitionTrace definitionTrace = buildDefinitionTrace(
                canonicalName,
                queryModel,
                resolution,
                provenance,
                safeRequest,
                fieldPermissions,
                limitations);

        SemanticQueryRequest payload = safeRequest.getPayload();
        if (payload == null) {
            collector.record(
                    "DEFINITION",
                    "STATIC_SEMANTIC_LINEAGE",
                    StageStatus.EVALUATED,
                    "DEFINITION_ONLY",
                    "DEFINITION_TRACE_COMPLETE",
                    Confidence.EXACT,
                    Map.of("fieldCount", definitionTrace.fields().size()));
            return new SemanticExplainResponse(
                    SemanticExplainResponse.SCHEMA_VERSION,
                    Basis.DEFINITION,
                    definitionTrace,
                    new SemanticExplainResponse.CompilationTrace(
                            null, null, List.of(), List.of(), null,
                            detailedEvents(safeRequest, collector)),
                    securityTrace(queryModel, describeDecision, fieldPermissions,
                            null, baseContext, null, limitations),
                    materializationTrace(queryModel, safeRequest, null),
                    new SemanticExplainResponse.ExecutionTrace(
                            StageStatus.NOT_EVALUATED,
                            StageStatus.NOT_EVALUATED,
                            StageStatus.NOT_EVALUATED,
                            "NOT_EVALUATED"),
                    new SemanticExplainResponse.SqlTrace(
                            false, null, null, null, List.of(),
                            Confidence.OPAQUE, "NOT_EVALUATED"),
                    deduplicatedLimitations(limitations),
                    List.of());
        }

        SemanticQueryRequest originalDsl = deepCopy(payload, SemanticQueryRequest.class);
        SemanticExplainCompilation compilation = semanticQueryService.compileForExplain(
                canonicalName,
                payload,
                pinnedContext.withPermissionAction(PermissionAction.EXECUTE));
        ModelResultContext resultContext = compilation.modelResultContext();
        PermissionDecision executeDecision = resultContext.getPermissionDecision();
        FieldPermissionResolution executedFieldPermissions = fieldPermissionResolver.resolve(resultContext);

        List<SemanticExplainResponse.ConditionTrace> normalizedConditions =
                normalizedConditionTrace(
                        compilation.normalizedRequest(),
                        collector,
                        executedFieldPermissions);
        DbQueryRequestDef normalizedForOutput = redactNormalizedDsl(
                compilation.normalizedRequest(), normalizedConditions);

        SemanticExplainResponse.CompilationTrace compilationTrace =
                new SemanticExplainResponse.CompilationTrace(
                        redactSemanticDsl(originalDsl),
                        normalizedForOutput,
                        normalizedConditions,
                        fieldResolution(compilation.normalizedRequest(), definitionTrace),
                        safeRequest.getDepth() == SemanticExplainRequest.Depth.SUMMARY
                                ? null
                                : stagePlan(resultContext),
                        detailedEvents(safeRequest, collector));

        limitations.add(limitation(
                "RECOMPILED_NOT_EXECUTED_TRACE",
                "This evidence was produced by a fresh governed compilation. It is not the historical trace of an earlier execution.",
                Confidence.EXACT));

        SemanticExplainResponse.SecurityTrace securityTrace = securityTrace(
                queryModel,
                executeDecision,
                executedFieldPermissions,
                normalizedConditions,
                baseContext,
                resultContext,
                limitations);
        SemanticExplainResponse.MaterializationTrace materializationTrace =
                materializationTrace(queryModel, safeRequest, compilation);
        SemanticExplainResponse.SqlTrace sqlTrace = sqlTrace(
                safeRequest, compilation, normalizedConditions, limitations);

        return new SemanticExplainResponse(
                SemanticExplainResponse.SCHEMA_VERSION,
                Basis.RECOMPILED,
                definitionTrace,
                compilationTrace,
                securityTrace,
                materializationTrace,
                new SemanticExplainResponse.ExecutionTrace(
                        StageStatus.NOT_EVALUATED,
                        StageStatus.NOT_EVALUATED,
                        StageStatus.NOT_EVALUATED,
                        "NOT_EVALUATED"),
                sqlTrace,
                deduplicatedLimitations(limitations),
                payload.getQueryInputWarnings() == null
                        ? List.of()
                        : List.copyOf(payload.getQueryInputWarnings()));
    }

    private SemanticExplainResponse.DefinitionTrace buildDefinitionTrace(
            String canonicalName,
            QueryModel queryModel,
            CatalogResolution<QueryModel> resolution,
            ModelProvenance provenance,
            SemanticExplainRequest request,
            FieldPermissionResolution permissions,
            List<SemanticExplainResponse.Limitation> limitations
    ) {
        Set<String> effectiveAccess = permissions == null
                ? null
                : permissions.getEffectiveFieldAccess();
        Map<String, DbQueryColumn> queryColumns = new LinkedHashMap<>();
        if (queryModel.getJdbcQueryColumns() != null) {
            for (DbQueryColumn column : queryModel.getJdbcQueryColumns()) {
                if (column != null && column.getName() != null) {
                    queryColumns.put(column.getName(), column);
                }
            }
        }
        Map<String, CalculatedFieldDef> calculatedFields = new LinkedHashMap<>();
        for (CalculatedFieldDef calculated : safeList(queryModel.getPredefinedCalculatedFields())) {
            if (calculated != null && calculated.getName() != null) {
                calculatedFields.put(calculated.getName(), calculated);
            }
        }

        List<String> selected = requestedFields(request.getFields(), canonicalName,
                queryColumns.keySet(), calculatedFields.keySet());
        List<SemanticExplainResponse.FieldTrace> fields = new ArrayList<>();
        for (String field : selected) {
            if (effectiveAccess != null && !effectiveAccess.contains(field)) {
                limitations.add(limitation(
                        "FIELD_NOT_VISIBLE_OR_UNKNOWN",
                        "A requested field was omitted because it is unavailable to this caller.",
                        Confidence.EXACT));
                continue;
            }
            DbQueryColumn queryColumn = queryColumns.get(field);
            if (queryColumn != null) {
                fields.add(fieldTrace(canonicalName, queryModel, queryColumn,
                        request.isIncludePhysicalNames(), limitations));
                continue;
            }
            CalculatedFieldDef calculated = calculatedFields.get(field);
            if (calculated != null) {
                fields.add(calculatedFieldTrace(canonicalName, queryModel,
                        calculated, queryColumns.keySet()));
                continue;
            }
            limitations.add(limitation(
                    "FIELD_NOT_VISIBLE_OR_UNKNOWN",
                    "A requested field was omitted because it is unavailable to this caller.",
                    Confidence.EXACT));
        }

        SemanticExplainResponse.ModelTrace modelTrace = new SemanticExplainResponse.ModelTrace(
                canonicalName,
                provenance == null ? "QUERY" : provenance.kind().name(),
                resolution == null ? null : resolution.catalogIdentity().namespace(),
                resolution == null
                        ? null
                        : resolution.catalogIdentity().sourceRevision().value(),
                modelSource(provenance),
                modelDependencies(provenance));
        return new SemanticExplainResponse.DefinitionTrace(
                canonicalName,
                modelTrace,
                List.copyOf(fields),
                joinTrace(queryModel, request.isIncludePhysicalNames()));
    }

    private SemanticExplainResponse.FieldTrace fieldTrace(
            String queryModelName,
            QueryModel queryModel,
            DbQueryColumn queryColumn,
            boolean includePhysicalNames,
            List<SemanticExplainResponse.Limitation> limitations
    ) {
        DbColumn selected = queryColumn.getSelectColumn() != null
                ? queryColumn.getSelectColumn()
                : queryColumn;
        TableModel owner = owner(queryModel, selected.getQueryObject());
        String tableField = tableField(selected);
        String qmRef = queryModelName + "." + queryColumn.getName();
        String tmRef = (owner == null ? "unresolved-tm" : owner.getName()) + "." + tableField;
        List<SemanticExplainResponse.LineageEdge> lineage = new ArrayList<>();
        lineage.add(new SemanticExplainResponse.LineageEdge(
                qmRef, tmRef, null, null, null,
                Confidence.EXACT, "QM_TO_TM_MODEL_STRUCTURE"));

        String aggregationFormula = aggregationFormula(selected);
        List<String> references = formulaReferences(selected, queryModel);
        boolean opaqueFormulaBuilder = hasOpaqueFormulaBuilder(selected);
        if (aggregationFormula != null) {
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    tmRef,
                    aggregationFormula,
                    null,
                    null,
                    aggregationFormula,
                    formulaConfidence(selected),
                    formulaReasonCode(selected)));
        } else if (opaqueFormulaBuilder) {
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    tmRef,
                    "<opaque-formula-builder>",
                    null,
                    null,
                    null,
                    Confidence.OPAQUE,
                    "FORMULA_BUILDER_OPAQUE"));
            limitations.add(limitation(
                    "FORMULA_BUILDER_OPAQUE",
                    "An arbitrary formulaDef.builder participated; its source code and undeclared physical lineage were not inspected or exposed.",
                    Confidence.OPAQUE));
        }

        QueryObject queryObject = selected.getQueryObject();
        String physicalColumn = safeColumnName(selected);
        TableQueryObject table = queryObject == null
                ? null
                : queryObject.getDecorate(TableQueryObject.class);
        ViewSqlQueryObject view = queryObject == null
                ? null
                : queryObject.getDecorate(ViewSqlQueryObject.class);
        if (opaqueFormulaBuilder) {
            addOpaqueOrDeclaredFormulaSources(
                    lineage, selected, includePhysicalNames);
        } else if (table != null) {
            String relation = includePhysicalNames ? qualifiedTable(table) : null;
            String column = includePhysicalNames ? physicalColumn : null;
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    tmRef,
                    includePhysicalNames ? qualifiedPhysical(relation, column) : "<physical-source-redacted>",
                    relation,
                    column,
                    null,
                    Confidence.EXACT,
                    includePhysicalNames ? "TABLE_COLUMN_MODEL_STRUCTURE" : "PHYSICAL_NAME_REDACTED"));
        } else if (view != null) {
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    tmRef,
                    "viewSql.output." + (includePhysicalNames ? physicalColumn : "<redacted>"),
                    null,
                    includePhysicalNames ? physicalColumn : null,
                    null,
                    Confidence.EXACT,
                    "VIEW_OUTPUT_COLUMN_MODEL_STRUCTURE"));
            List<SourceRef> declared = sourceRefs(selected);
            if (declared.isEmpty()) {
                lineage.add(new SemanticExplainResponse.LineageEdge(
                        "viewSql.output." + (includePhysicalNames ? physicalColumn : "<redacted>"),
                        "<opaque-view-source>",
                        null,
                        null,
                        null,
                        Confidence.OPAQUE,
                        "VIEW_SQL_SOURCE_OPAQUE"));
                limitations.add(limitation(
                        "VIEW_SQL_SOURCE_OPAQUE",
                        "An arbitrary viewSql has no declared sourceRefs; its underlying physical lineage was not inferred from SQL text.",
                        Confidence.OPAQUE));
            } else {
                for (SourceRef sourceRef : declared) {
                    String relation = includePhysicalNames ? sourceRef.relation() : null;
                    String column = includePhysicalNames ? sourceRef.column() : null;
                    lineage.add(new SemanticExplainResponse.LineageEdge(
                            "viewSql.output." + (includePhysicalNames ? physicalColumn : "<redacted>"),
                            includePhysicalNames
                                    ? qualifiedPhysical(relation, column)
                                    : "<declared-physical-source-redacted>",
                            relation,
                            column,
                            sourceRef.expression(),
                            Confidence.DECLARED,
                            "SOURCE_REFS_DECLARATION"));
                }
            }
        } else {
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    tmRef, "<opaque-query-object>", null, null, null,
                    Confidence.OPAQUE, "QUERY_OBJECT_SOURCE_OPAQUE"));
            limitations.add(limitation(
                    "QUERY_OBJECT_SOURCE_OPAQUE",
                    "The query object does not expose a tableName or structured view lineage.",
                    Confidence.OPAQUE));
        }

        return new SemanticExplainResponse.FieldTrace(
                qmRef,
                owner == null ? null : owner.getName(),
                tableField,
                fieldType(selected),
                selected.getType() == null ? null : selected.getType().name(),
                aggregationFormula,
                references,
                List.copyOf(lineage));
    }

    private void addOpaqueOrDeclaredFormulaSources(
            List<SemanticExplainResponse.LineageEdge> lineage,
            DbColumn column,
            boolean includePhysicalNames
    ) {
        List<SourceRef> declared = sourceRefs(column);
        if (declared.isEmpty()) {
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    "<opaque-formula-builder>",
                    "<opaque-formula-source>",
                    null,
                    null,
                    null,
                    Confidence.OPAQUE,
                    "FORMULA_BUILDER_SOURCE_OPAQUE"));
            return;
        }
        for (SourceRef sourceRef : declared) {
            String relation = includePhysicalNames ? sourceRef.relation() : null;
            String physicalColumn = includePhysicalNames ? sourceRef.column() : null;
            lineage.add(new SemanticExplainResponse.LineageEdge(
                    "<opaque-formula-builder>",
                    includePhysicalNames
                            ? qualifiedPhysical(relation, physicalColumn)
                            : "<declared-physical-source-redacted>",
                    relation,
                    physicalColumn,
                    sourceRef.expression(),
                    Confidence.DECLARED,
                    "SOURCE_REFS_DECLARATION"));
        }
    }

    private SemanticExplainResponse.FieldTrace calculatedFieldTrace(
            String queryModelName,
            QueryModel queryModel,
            CalculatedFieldDef calculated,
            Set<String> knownFields
    ) {
        String qmRef = queryModelName + "." + calculated.getName();
        List<String> references = expressionReferences(calculated.getExpression(), knownFields);
        List<SemanticExplainResponse.LineageEdge> edges = List.of(
                new SemanticExplainResponse.LineageEdge(
                        qmRef,
                        "calculated:" + calculated.getName(),
                        null,
                        null,
                        calculated.getExpression(),
                        Confidence.DECLARED,
                        "CALCULATED_FIELD_DECLARATION"));
        return new SemanticExplainResponse.FieldTrace(
                qmRef,
                queryModel.getJdbcModel() == null ? null : queryModel.getJdbcModel().getName(),
                calculated.getName(),
                "calculated",
                calculated.getType(),
                calculated.getExpression(),
                references,
                edges);
    }

    private SemanticExplainResponse.SecurityTrace securityTrace(
            QueryModel queryModel,
            PermissionDecision decision,
            FieldPermissionResolution fieldPermissions,
            List<SemanticExplainResponse.ConditionTrace> normalizedConditions,
            SemanticRequestContext baseContext,
            ModelResultContext resultContext,
            List<SemanticExplainResponse.Limitation> limitations
    ) {
        PermissionDecision safeDecision = decision == null
                ? PermissionDecision.publicAllow()
                : decision;
        List<SemanticExplainResponse.ConditionTrace> conditions = normalizedConditions == null
                ? permissionConditions(safeDecision, fieldPermissions)
                : new ArrayList<>(normalizedConditions);
        boolean hasAccessBuilder = queryModel.getAccessBuilders() != null
                && !queryModel.getAccessBuilders().isEmpty();
        if (hasAccessBuilder) {
            conditions.add(new SemanticExplainResponse.ConditionTrace(
                    "accessBuilder",
                    null,
                    "CUSTOM",
                    "OPAQUE",
                    "***",
                    ConditionOrigin.ACCESS_BUILDER,
                    Confidence.OPAQUE));
            limitations.add(limitation(
                    "ACCESS_BUILDER_OPAQUE",
                    "An access queryBuilder participated, but its source and exact predicate structure are intentionally not exposed.",
                    Confidence.OPAQUE));
        }

        Set<String> visible = fieldPermissions == null
                ? null
                : fieldPermissions.getEffectiveFieldAccess();
        LinkedHashSet<String> affectedFields = new LinkedHashSet<>();
        for (PermissionPredicate predicate : safeDecision.getRowPredicates()) {
            for (String field : predicate.getReferencedFields()) {
                if (visible == null || visible.contains(field)) {
                    affectedFields.add(field);
                }
            }
        }
        boolean fieldVisibility = visible != null;
        boolean rowConstraints = !safeDecision.getRowPredicates().isEmpty()
                || (baseContext.getSystemSlice() != null && !baseContext.getSystemSlice().isEmpty())
                || hasAccessBuilder;
        return new SemanticExplainResponse.SecurityTrace(
                StageStatus.EVALUATED,
                safeDecision.getDecisionId() != null
                        ? safeDecision.getDecisionId()
                        : queryModel.getName() + ":modelPermissions",
                safeDecision.getPolicyVersion() != null
                        ? safeDecision.getPolicyVersion()
                        : "UNVERSIONED",
                safeDecision.isPublicDecision(),
                rowConstraints || fieldVisibility ? "CONSTRAINED" : "ALLOW",
                List.copyOf(conditions),
                List.copyOf(affectedFields),
                fieldVisibility,
                hasAccessBuilder,
                rowConstraints);
    }

    private List<SemanticExplainResponse.ConditionTrace> normalizedConditionTrace(
            DbQueryRequestDef normalized,
            ExplainTraceCollector collector,
            FieldPermissionResolution fieldPermissions
    ) {
        List<CondRequestDef> flattened = flattenJdbcConditions(
                normalized == null ? null : normalized.getSlice());
        Set<String> visible = fieldPermissions == null
                ? null
                : fieldPermissions.getEffectiveFieldAccess();
        List<SemanticExplainResponse.ConditionTrace> traces = new ArrayList<>();
        for (int index = 0; index < flattened.size(); index++) {
            CondRequestDef condition = flattened.get(index);
            ConditionOrigin origin = collector == null
                    ? null
                    : collector.conditionOrigin(condition);
            if (origin == null) {
                origin = ConditionOrigin.UNKNOWN;
            }
            boolean fieldVisible = condition.getField() == null
                    || visible == null
                    || visible.contains(condition.getField());
            traces.add(new SemanticExplainResponse.ConditionTrace(
                    "slice[" + index + "]",
                    fieldVisible ? condition.getField() : null,
                    condition.getOp() != null ? condition.getOp() : expressionOperator(condition),
                    valueType(condition.getValue()),
                    "***",
                    origin,
                    origin == ConditionOrigin.UNKNOWN ? Confidence.OBSERVED : Confidence.EXACT));
        }
        return List.copyOf(traces);
    }

    private List<SemanticExplainResponse.ConditionTrace> permissionConditions(
            PermissionDecision decision,
            FieldPermissionResolution fieldPermissions
    ) {
        List<SemanticExplainResponse.ConditionTrace> conditions = new ArrayList<>();
        Set<String> visible = fieldPermissions == null
                ? null
                : fieldPermissions.getEffectiveFieldAccess();
        int index = 0;
        for (PermissionPredicate predicate : decision.getRowPredicates()) {
            ConditionOrigin origin = predicate.getOrigin() == PermissionPredicate.Origin.LEGACY_ACCESS
                    ? ConditionOrigin.ACCESS_BUILDER
                    : ConditionOrigin.MODEL_PERMISSION;
            boolean fieldVisible = predicate.getField() == null
                    || visible == null
                    || visible.contains(predicate.getField());
            conditions.add(new SemanticExplainResponse.ConditionTrace(
                    "permission[" + index++ + "]",
                    fieldVisible ? predicate.getField() : null,
                    predicate.getOperator(),
                    predicate.getValueType(),
                    "***",
                    origin,
                    predicate.isProvable() ? Confidence.EXACT : Confidence.OPAQUE));
        }
        return conditions;
    }

    private SemanticExplainResponse.MaterializationTrace materializationTrace(
            QueryModel queryModel,
            SemanticExplainRequest request,
            SemanticExplainCompilation compilation
    ) {
        List<SemanticExplainResponse.MaterializationDefinition> definitions =
                materializationDefinitions(queryModel, request.isIncludePhysicalNames());
        if (compilation == null) {
            return new SemanticExplainResponse.MaterializationTrace(
                    definitions,
                    StageStatus.NOT_EVALUATED,
                    "NOT_EVALUATED",
                    null,
                    "NOT_EVALUATED",
                    "NOT_EVALUATED");
        }
        SemanticExplainCompilation.RoutingEvidence routing = compilation.routingEvidence();
        if (routing == null) {
            return new SemanticExplainResponse.MaterializationTrace(
                    definitions,
                    StageStatus.NOT_EVALUATED,
                    "NOT_EVALUATED",
                    null,
                    "NOT_EVALUATED",
                    "ROUTING_EVIDENCE_UNAVAILABLE");
        }
        return new SemanticExplainResponse.MaterializationTrace(
                definitions,
                routing.status(),
                routing.route(),
                routing.preAggregation(),
                routing.decision(),
                routing.reasonCode());
    }

    private SemanticExplainResponse.SqlTrace sqlTrace(
            SemanticExplainRequest request,
            SemanticExplainCompilation compilation,
            List<SemanticExplainResponse.ConditionTrace> conditions,
            List<SemanticExplainResponse.Limitation> limitations
    ) {
        SemanticExplainCompilation.SqlEvidence evidence = compilation.sqlEvidence();
        boolean exposed = request.isIncludeSql() && request.isIncludePhysicalNames();
        String reasonCode = null;
        if (!request.isIncludeSql()) {
            reasonCode = "SQL_NOT_REQUESTED";
        } else if (!request.isIncludePhysicalNames()) {
            reasonCode = "PHYSICAL_NAMES_NOT_EXPOSED";
            limitations.add(limitation(
                    "PHYSICAL_SQL_REDACTED",
                    "Physical SQL was withheld because includePhysicalNames is false; Foggy does not guess at safe SQL token redaction.",
                    Confidence.EXACT));
        } else if (evidence == null) {
            reasonCode = "SQL_EVIDENCE_UNAVAILABLE";
        } else if ("QueryExecutionContext.sql".equals(evidence.sourceOfTruth())) {
            reasonCode = "SQL_CAPTURED_FROM_QUERY_EXECUTION_CONTEXT";
        } else if ("ComposeSqlCompiler.output".equals(evidence.sourceOfTruth())) {
            reasonCode = "SQL_CAPTURED_FROM_COMPOSE_COMPILER";
        } else {
            reasonCode = "SQL_CAPTURED_FROM_COMPILER";
        }
        List<SemanticExplainResponse.ParameterTrace> parameters = new ArrayList<>();
        List<Object> values = evidence == null ? List.of() : evidence.parameters();
        List<ConditionOrigin> parameterOrigins = parameterOrigins(
                compilation.normalizedRequest(), conditions, values.size(), limitations);
        for (int index = 0; index < values.size(); index++) {
            ConditionOrigin origin = parameterOrigins.get(index);
            parameters.add(new SemanticExplainResponse.ParameterTrace(
                    index + 1,
                    valueType(values.get(index)),
                    "***",
                    origin,
                    Confidence.OBSERVED));
        }
        return new SemanticExplainResponse.SqlTrace(
                exposed,
                exposed && evidence != null ? evidence.logicalSql() : null,
                exposed && evidence != null ? evidence.finalPhysicalSql() : null,
                evidence == null ? null : evidence.sourceOfTruth(),
                List.copyOf(parameters),
                evidence == null ? Confidence.OPAQUE : evidence.confidence(),
                reasonCode);
    }

    private List<ConditionOrigin> parameterOrigins(
            DbQueryRequestDef normalized,
            List<SemanticExplainResponse.ConditionTrace> conditions,
            int parameterCount,
            List<SemanticExplainResponse.Limitation> limitations
    ) {
        if (parameterCount == 0) {
            return List.of();
        }
        List<CondRequestDef> normalizedConditions = flattenJdbcConditions(
                normalized == null ? null : normalized.getSlice());
        List<ConditionOrigin> expanded = new ArrayList<>();
        for (int index = 0; index < normalizedConditions.size(); index++) {
            ConditionOrigin origin = index < conditions.size()
                    ? conditions.get(index).origin()
                    : ConditionOrigin.UNKNOWN;
            append(expanded, origin, bindParameterCount(normalizedConditions.get(index).getValue()));
        }
        if (expanded.size() == parameterCount) {
            return List.copyOf(expanded);
        }
        limitations.add(limitation(
                "PARAMETER_ORIGIN_PARTIALLY_OPAQUE",
                "Generated parameter cardinality could not be correlated one-to-one with normalized conditions; parameter origins were not guessed.",
                Confidence.OPAQUE));
        return Collections.nCopies(parameterCount, ConditionOrigin.UNKNOWN);
    }

    private int bindParameterCount(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }

    private List<SemanticExplainResponse.MaterializationDefinition> materializationDefinitions(
            QueryModel queryModel,
            boolean includePhysicalNames
    ) {
        List<SemanticExplainResponse.MaterializationDefinition> definitions = new ArrayList<>();
        for (TableModel tableModel : tableModels(queryModel)) {
            for (PreAggregation preAgg : safeList(tableModel.getPreAggregations())) {
                Map<String, String> granularities = new LinkedHashMap<>();
                if (preAgg.getGranularities() != null) {
                    preAgg.getGranularities().forEach((key, value) ->
                            granularities.put(key, value == null ? null : value.name()));
                }
                Map<String, String> measures = new LinkedHashMap<>();
                if (preAgg.getMeasureAggregations() != null) {
                    preAgg.getMeasureAggregations().forEach((key, value) ->
                            measures.put(key, value == null ? null : value.name()));
                }
                definitions.add(new SemanticExplainResponse.MaterializationDefinition(
                        tableModel.getName(),
                        preAgg.getName(),
                        includePhysicalNames ? preAgg.getQualifiedTableName() : null,
                        preAgg.getBuildMode() == null ? null : preAgg.getBuildMode().name(),
                        sorted(preAgg.getDimensionNames()),
                        Collections.unmodifiableMap(granularities),
                        Collections.unmodifiableMap(measures),
                        Confidence.EXACT));
            }
        }
        return List.copyOf(definitions);
    }

    private SemanticExplainResponse.QueryStageTrace stagePlan(ModelResultContext context) {
        if (context == null || context.getExtData() == null) {
            return null;
        }
        Object raw = context.getExtData().get("queryStagePlan");
        if (!(raw instanceof Map<?, ?> plan)) {
            return null;
        }
        List<SemanticExplainResponse.StageTrace> stages = new ArrayList<>();
        Object rawStages = plan.get("stages");
        if (rawStages instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (!(item instanceof Map<?, ?> stage)) {
                    continue;
                }
                stages.add(new SemanticExplainResponse.StageTrace(
                        text(stage.get("id")),
                        text(stage.get("type")),
                        text(stage.get("sqlAlias")),
                        strings(stage.get("inputAliases")),
                        strings(stage.get("outputAliases")),
                        strings(stage.get("filterAliases")),
                        strings(stage.get("orderAliases")),
                        Boolean.TRUE.equals(stage.get("requiresSqlBoundary")),
                        integer(stage.get("parameterCount"))));
            }
        }
        return new SemanticExplainResponse.QueryStageTrace(
                text(plan.get("version")),
                text(plan.get("dialect")),
                text(plan.get("renderStrategy")),
                text(plan.get("returnTotalStrategy")),
                text(plan.get("preAggOptimizationPolicy")),
                List.copyOf(stages),
                strings(plan.get("fallbacks")),
                strings(plan.get("unsupported")));
    }

    private List<SemanticExplainResponse.FieldResolution> fieldResolution(
            DbQueryRequestDef normalized,
            SemanticExplainResponse.DefinitionTrace definition
    ) {
        if (normalized == null || normalized.getColumns() == null) {
            return List.of();
        }
        Map<String, SemanticExplainResponse.FieldTrace> fields = new LinkedHashMap<>();
        for (SemanticExplainResponse.FieldTrace field : definition.fields()) {
            String unqualified = field.queryField().substring(field.queryField().lastIndexOf('.') + 1);
            fields.put(unqualified, field);
        }
        List<SemanticExplainResponse.FieldResolution> result = new ArrayList<>();
        for (String requested : normalized.getColumns()) {
            String lookup = outputFieldName(requested);
            SemanticExplainResponse.FieldTrace field = fields.get(lookup);
            result.add(new SemanticExplainResponse.FieldResolution(
                    requested,
                    lookup,
                    field == null ? null : field.tableModel(),
                    field == null ? null : field.tableField(),
                    field == null ? Confidence.OBSERVED : Confidence.EXACT));
        }
        return List.copyOf(result);
    }

    private List<SemanticExplainResponse.JoinTrace> joinTrace(
            QueryModel queryModel,
            boolean includePhysicalNames
    ) {
        if (queryModel.getMergedJoinGraph() == null) {
            return List.of();
        }
        List<SemanticExplainResponse.JoinTrace> joins = new ArrayList<>();
        for (JoinEdge edge : queryModel.getMergedJoinGraph().getAllEdges()) {
            TableModel from = owner(queryModel, edge.getFrom());
            TableModel to = owner(queryModel, edge.getTo());
            boolean opaque = edge.hasOnBuilder();
            joins.add(new SemanticExplainResponse.JoinTrace(
                    from == null ? null : from.getName(),
                    to == null ? null : to.getName(),
                    edge.getJoinType() == null ? "LEFT" : edge.getJoinType().name(),
                    includePhysicalNames && !opaque ? edge.getForeignKey() : null,
                    opaque ? Confidence.OPAQUE : Confidence.EXACT,
                    opaque ? "JOIN_ON_BUILDER_OPAQUE" :
                            (includePhysicalNames ? "JOIN_FOREIGN_KEY_MODEL_STRUCTURE" : "PHYSICAL_NAME_REDACTED")));
        }
        return List.copyOf(joins);
    }

    private SemanticQueryRequest redactSemanticDsl(SemanticQueryRequest request) {
        if (request == null) {
            return null;
        }
        SemanticQueryRequest copy = deepCopy(request, SemanticQueryRequest.class);
        redactSemanticConditions(copy.getSlice());
        redactSemanticConditions(copy.getHaving());
        redactSemanticConditions(copy.getPostSlice());
        if (copy.getCursor() != null) {
            copy.setCursor("***");
        }
        if (copy.getSemanticSql() != null) {
            copy.setSemanticSql("<semantic-sql-redacted>");
        }
        if (copy.getGridSql() != null) {
            copy.setGridSql("<grid-sql-redacted>");
        }
        copy.setHints(redactMapScalars(copy.getHints()));
        copy.setExtData(redactMapScalars(copy.getExtData()));
        copy.setTimeWindow(redactMapScalars(copy.getTimeWindow()));
        copy.setMemoryGridPlan(redactMapScalars(copy.getMemoryGridPlan()));
        copy.setExecutablePlan(redactScalars(copy.getExecutablePlan()));
        return copy;
    }

    private void redactSemanticConditions(List<SemanticQueryRequest.SliceItem> conditions) {
        if (conditions == null) {
            return;
        }
        for (SemanticQueryRequest.SliceItem condition : conditions) {
            if (condition == null) {
                continue;
            }
            if (condition.getValue() != null) {
                condition.setValue("***");
            }
            redactSemanticConditions(condition.getOr());
            redactSemanticConditions(condition.getAnd());
        }
    }

    private DbQueryRequestDef redactNormalizedDsl(
            DbQueryRequestDef request,
            List<SemanticExplainResponse.ConditionTrace> normalizedConditions
    ) {
        if (request == null) {
            return null;
        }
        DbQueryRequestDef copy = deepCopy(request, DbQueryRequestDef.class);
        redactConditions(copy.getSlice());
        redactConditions(copy.getHaving());
        redactConditions(copy.getPostSlice());
        copy.setQueryId(copy.getQueryId() == null ? null : "***");
        copy.setExtData(redactScalars(copy.getExtData()));

        List<CondRequestDef> flattened = flattenJdbcConditions(copy.getSlice());
        for (int index = 0;
             index < flattened.size() && index < normalizedConditions.size();
             index++) {
            CondRequestDef condition = flattened.get(index);
            SemanticExplainResponse.ConditionTrace trace = normalizedConditions.get(index);
            if (condition.getField() != null && trace.field() == null) {
                condition.setField("<restricted-field>");
            }
        }
        return copy;
    }

    private void redactConditions(List<? extends CondRequestDef> conditions) {
        if (conditions == null) {
            return;
        }
        for (CondRequestDef condition : conditions) {
            if (condition == null) {
                continue;
            }
            if (condition.getValue() != null) {
                condition.setValue("***");
            }
            if (condition.getExpr() != null) {
                condition.setExpr("<expression-redacted>");
            }
            redactConditions(condition.getOr());
            redactConditions(condition.getAnd());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redactMapScalars(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        return (Map<String, Object>) redactScalars(value);
    }

    private Object redactScalars(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((key, item) -> redacted.put(
                    String.valueOf(key),
                    item instanceof Map<?, ?> || item instanceof Collection<?>
                            ? redactScalars(item)
                            : "***"));
            return Collections.unmodifiableMap(redacted);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::redactScalars).toList();
        }
        return "***";
    }

    private List<SourceRef> sourceRefs(DbColumn column) {
        List<SourceRef> refs = new ArrayList<>();
        collectSourceRefs(column == null ? null : column.getExtData(), refs);
        DbMeasureColumn measureColumn = column == null
                ? null
                : column.getDecorate(DbMeasureColumn.class);
        if (measureColumn != null && measureColumn.getJdbcMeasure() != null) {
            collectSourceRefs(measureColumn.getJdbcMeasure().getExtData(), refs);
        }
        return refs.stream().distinct().toList();
    }

    private void collectSourceRefs(Object extData, List<SourceRef> refs) {
        if (!(extData instanceof Map<?, ?> map)) {
            return;
        }
        Object raw = map.get("sourceRefs");
        if (raw == null && map.get("lineage") instanceof Map<?, ?> lineage) {
            raw = lineage.get("sourceRefs");
        }
        Collection<?> values = raw instanceof Collection<?> collection
                ? collection
                : raw instanceof Map<?, ?> ? List.of(raw) : List.of();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> source)) {
                continue;
            }
            String relation = firstText(source.get("relation"), source.get("table"));
            String column = text(source.get("column"));
            if (relation != null || column != null) {
                refs.add(new SourceRef(relation, column, text(source.get("expression"))));
            }
        }
    }

    private String aggregationFormula(DbColumn column) {
        DbMeasureColumn measureColumn = column.getDecorate(DbMeasureColumn.class);
        DbMeasure measure = measureColumn == null ? null : measureColumn.getJdbcMeasure();
        DbFormulaDef formula = measureColumn != null
                ? measureColumn.getFormulaDef()
                : measure == null ? null : measure.getFormulaDef();
        if (formula != null && formula.getValue() != null && !formula.getValue().isBlank()) {
            return formula.getValue();
        }
        if (formula != null && formula.getBuilder() != null) {
            return null;
        }
        DbAggregation aggregation = measure != null ? measure.getAggregation() : column.getAggregation();
        if (aggregation == null || aggregation == DbAggregation.NONE || aggregation == DbAggregation.PK) {
            return null;
        }
        return aggregation.name() + "(" + safeColumnName(column) + ")";
    }

    private boolean hasOpaqueFormulaBuilder(DbColumn column) {
        DbMeasureColumn measureColumn = column.getDecorate(DbMeasureColumn.class);
        if (measureColumn != null && measureColumn.getJdbcMeasure() != null) {
            DbMeasureSupport measure = measureColumn.getJdbcMeasure()
                    .getDecorate(DbMeasureSupport.class);
            if (measure != null && measure.getFormulaBuilder() != null) {
                return true;
            }
        }

        DbPropertyColumn propertyColumn = column.getDecorate(DbPropertyColumn.class);
        if (propertyColumn != null && propertyColumn.getProperty() != null) {
            DbPropertyImpl property = propertyColumn.getProperty()
                    .getDecorate(DbPropertyImpl.class);
            if (property != null && property.getFormulaBuilder() != null) {
                return true;
            }
        }

        DbDimensionColumn dimensionColumn = column.getDecorate(DbDimensionColumn.class);
        if (dimensionColumn != null
                && dimensionColumn.isCaptionColumn()
                && dimensionColumn.getDimension() != null) {
            DbDimensionSupport dimension = dimensionColumn.getDimension()
                    .getDecorate(DbDimensionSupport.class);
            return dimension != null && dimension.getCaptionFormulaBuilder() != null;
        }
        return false;
    }

    private Confidence formulaConfidence(DbColumn column) {
        DbMeasureColumn measureColumn = column.getDecorate(DbMeasureColumn.class);
        DbFormulaDef formula = measureColumn == null ? null : measureColumn.getFormulaDef();
        return formula != null && formula.getValue() != null
                ? Confidence.DECLARED
                : Confidence.EXACT;
    }

    private String formulaReasonCode(DbColumn column) {
        return formulaConfidence(column) == Confidence.DECLARED
                ? "FORMULA_DECLARATION"
                : "AGGREGATION_MODEL_STRUCTURE";
    }

    private List<String> formulaReferences(DbColumn column, QueryModel model) {
        DbMeasureColumn measureColumn = column.getDecorate(DbMeasureColumn.class);
        DbFormulaDef formula = measureColumn == null ? null : measureColumn.getFormulaDef();
        return formula == null
                ? List.of()
                : expressionReferences(formula.getValue(), modelFieldNames(model));
    }

    private Set<String> modelFieldNames(QueryModel model) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (DbQueryColumn field : safeList(model.getJdbcQueryColumns())) {
            if (field != null && field.getName() != null) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private List<String> expressionReferences(String expression, Set<String> knownFields) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        Matcher matcher = REFERENCE_TOKEN.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group();
            if (knownFields.contains(token)) {
                refs.add(token);
            }
        }
        return List.copyOf(refs);
    }

    private String fieldType(DbColumn column) {
        if (column.isCalculatedField()) {
            return "calculated";
        }
        if (column.isMeasure()) {
            return "measure";
        }
        if (column.isDimension()) {
            return "dimension";
        }
        if (column.isProperty()) {
            return "property";
        }
        return "property";
    }

    private String tableField(DbColumn column) {
        DbMeasureColumn measureColumn = column.getDecorate(DbMeasureColumn.class);
        if (measureColumn != null && measureColumn.getJdbcMeasure() != null) {
            return measureColumn.getJdbcMeasure().getName();
        }
        DbDimensionColumn dimensionColumn = column.getDecorate(DbDimensionColumn.class);
        if (dimensionColumn != null && dimensionColumn.getDimension() != null) {
            String suffix = dimensionColumn.isCaptionColumn() ? "$caption" : "$id";
            return dimensionColumn.getDimension().getName() + suffix;
        }
        DbPropertyColumn propertyColumn = column.getDecorate(DbPropertyColumn.class);
        if (propertyColumn != null && propertyColumn.getProperty() != null) {
            return propertyColumn.getProperty().getName();
        }
        return column.getName();
    }

    private TableModel owner(QueryModel model, QueryObject queryObject) {
        if (model == null || queryObject == null) {
            return null;
        }
        try {
            TableModel direct = model.getJdbcModelByQueryObject(queryObject);
            if (direct != null) {
                return direct;
            }
        } catch (RuntimeException ignored) {
            // Continue with identity/root matching for legacy model implementations.
        }
        for (TableModel tableModel : tableModels(model)) {
            QueryObject candidate = tableModel.getQueryObject();
            if (candidate == queryObject
                    || (candidate != null && candidate.isRootEqual(queryObject))) {
                return tableModel;
            }
        }
        return null;
    }

    private List<TableModel> tableModels(QueryModel queryModel) {
        List<TableModel> result = new ArrayList<>();
        Set<TableModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (queryModel.getJdbcModel() != null && seen.add(queryModel.getJdbcModel())) {
            result.add(queryModel.getJdbcModel());
        }
        for (TableModel tableModel : safeList(queryModel.getJdbcModelList())) {
            if (tableModel != null && seen.add(tableModel)) {
                result.add(tableModel);
            }
        }
        return result;
    }

    private List<SemanticExplainResponse.ModelDependency> modelDependencies(
            ModelProvenance provenance
    ) {
        if (provenance == null) {
            return List.of();
        }
        return provenance.modelDependencies().stream()
                .sorted()
                .map(key -> new SemanticExplainResponse.ModelDependency(
                        key.kind().name(), key.canonicalName()))
                .toList();
    }

    private String modelSource(ModelProvenance provenance) {
        if (provenance == null || provenance.source() == null) {
            return null;
        }
        return provenance.source().bundleName() + ":" + provenance.source().resourceIdentity();
    }

    private List<String> requestedFields(
            List<String> requested,
            String modelName,
            Set<String> queryFields,
            Set<String> calculatedFields
    ) {
        LinkedHashSet<String> available = new LinkedHashSet<>();
        available.addAll(queryFields);
        available.addAll(calculatedFields);
        if (requested == null || requested.isEmpty()) {
            return List.copyOf(available);
        }
        List<String> result = new ArrayList<>();
        for (String field : requested) {
            if (field == null) {
                continue;
            }
            String normalized = field.trim();
            String prefix = modelName + ".";
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
            }
            result.add(normalized);
        }
        return result;
    }

    private List<CondRequestDef> flattenJdbcConditions(List<? extends CondRequestDef> conditions) {
        List<CondRequestDef> result = new ArrayList<>();
        flattenJdbcConditions(conditions, result);
        return result;
    }

    private void flattenJdbcConditions(
            List<? extends CondRequestDef> conditions,
            List<CondRequestDef> result
    ) {
        if (conditions == null) {
            return;
        }
        for (CondRequestDef condition : conditions) {
            if (condition == null) {
                continue;
            }
            if (condition._isLogicalGroup()) {
                flattenJdbcConditions(condition._getGroupChildren(), result);
            } else {
                result.add(condition);
            }
        }
    }

    private void append(List<ConditionOrigin> origins, ConditionOrigin origin, int count) {
        for (int index = 0; index < count; index++) {
            origins.add(origin);
        }
    }

    private String expressionOperator(CondRequestDef condition) {
        return condition.getExpr() == null ? null : "$expr";
    }

    private String outputFieldName(String expression) {
        if (expression == null) {
            return null;
        }
        Matcher alias = Pattern.compile("(?i)\\s+as\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*$")
                .matcher(expression);
        return alias.find() ? alias.group(1) : expression.trim();
    }

    private String safeColumnName(DbColumn column) {
        try {
            return column.getSqlColumnName();
        } catch (RuntimeException ex) {
            return column.getName();
        }
    }

    private String qualifiedTable(TableQueryObject table) {
        return table.getSchema() == null || table.getSchema().isBlank()
                ? table.getTableName()
                : table.getSchema() + "." + table.getTableName();
    }

    private String qualifiedPhysical(String relation, String column) {
        if (relation == null) {
            return column;
        }
        return column == null ? relation : relation + "." + column;
    }

    private String valueType(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Collection<?> collection) {
            Object first = collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
            return "LIST<" + (first == null ? "UNKNOWN" : first.getClass().getSimpleName()) + ">";
        }
        return value.getClass().getSimpleName();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value != null ? value : text(second);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String requireText(String value, String label) {
        String normalized = text(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private List<String> sorted(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).sorted().toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> T deepCopy(Object source, Class<T> type) {
        return JsonUtils.fromJson(JsonUtils.toJson(source), type);
    }

    private SemanticExplainResponse.Limitation limitation(
            String code,
            String message,
            Confidence confidence
    ) {
        return new SemanticExplainResponse.Limitation(code, message, confidence);
    }

    private List<ExplainTraceCollector.Event> detailedEvents(
            SemanticExplainRequest request,
            ExplainTraceCollector collector
    ) {
        return request != null
                && request.getDepth() == SemanticExplainRequest.Depth.DETAILED
                ? collector.snapshot()
                : List.of();
    }

    private List<SemanticExplainResponse.Limitation> deduplicatedLimitations(
            List<SemanticExplainResponse.Limitation> limitations
    ) {
        LinkedHashMap<String, SemanticExplainResponse.Limitation> unique = new LinkedHashMap<>();
        for (SemanticExplainResponse.Limitation limitation : limitations) {
            if (limitation == null) {
                continue;
            }
            String key = limitation.code() + "\u0000"
                    + limitation.message() + "\u0000"
                    + limitation.confidence();
            unique.putIfAbsent(key, limitation);
        }
        return List.copyOf(unique.values());
    }

    private record SourceRef(String relation, String column, String expression) {
    }
}
