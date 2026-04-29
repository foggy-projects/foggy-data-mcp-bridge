package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.authority.ModelInfoProvider;
import com.foggyframework.dataset.db.model.engine.compose.plan.TimeWindowDef;
import com.foggyframework.dataset.db.model.engine.compose.relation.RelationPermissionState;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable options bag for
 * {@link ComposeRelationCompiler#compileToRelation}.
 *
 * <p>Mirrors {@link ComposeSqlCompiler.CompileOptions} and adds
 * relation-specific fields (alias, permissionState, timeWindow
 * enrichment hints).</p>
 *
 * @since 8.5.0.beta (S7c)
 */
public final class RelationCompileOptions {

    private final SemanticQueryServiceV3 semanticService;
    private final Map<String, ModelBinding> bindings;
    private final ModelInfoProvider modelInfoProvider;
    private final Map<String, Optional<String>> datasourceIds;
    private final String dialect;
    private final String relationAlias;
    private final String permissionState;

    // TimeWindow enrichment hints — when provided, OutputSchema is
    // enriched with semantic metadata from TimeWindowExpander.
    private final TimeWindowDef timeWindowDef;
    private final List<String> dimensionFields;
    private final Set<String> measureFields;

    private RelationCompileOptions(Builder b) {
        this.semanticService = b.semanticService;
        this.bindings = b.bindings;
        this.modelInfoProvider = b.modelInfoProvider;
        this.datasourceIds = b.datasourceIds;
        this.dialect = b.dialect == null ? "mysql" : b.dialect;
        this.relationAlias = b.relationAlias == null ? "rel_0" : b.relationAlias;
        this.permissionState = b.permissionState == null
                ? RelationPermissionState.UNKNOWN
                : b.permissionState;
        this.timeWindowDef = b.timeWindowDef;
        this.dimensionFields = b.dimensionFields;
        this.measureFields = b.measureFields;
    }

    public SemanticQueryServiceV3 semanticService() { return semanticService; }
    public Map<String, ModelBinding> bindings() { return bindings; }
    public ModelInfoProvider modelInfoProvider() { return modelInfoProvider; }
    public Map<String, Optional<String>> datasourceIds() { return datasourceIds; }
    public String dialect() { return dialect; }
    public String relationAlias() { return relationAlias; }
    public String permissionState() { return permissionState; }
    public TimeWindowDef timeWindowDef() { return timeWindowDef; }
    public List<String> dimensionFields() { return dimensionFields; }
    public Set<String> measureFields() { return measureFields; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private SemanticQueryServiceV3 semanticService;
        private Map<String, ModelBinding> bindings;
        private ModelInfoProvider modelInfoProvider;
        private Map<String, Optional<String>> datasourceIds;
        private String dialect;
        private String relationAlias;
        private String permissionState;
        private TimeWindowDef timeWindowDef;
        private List<String> dimensionFields;
        private Set<String> measureFields;

        public Builder semanticService(SemanticQueryServiceV3 v) { this.semanticService = v; return this; }
        public Builder bindings(Map<String, ModelBinding> v) { this.bindings = v; return this; }
        public Builder modelInfoProvider(ModelInfoProvider v) { this.modelInfoProvider = v; return this; }
        public Builder datasourceIds(Map<String, Optional<String>> v) { this.datasourceIds = v; return this; }
        public Builder dialect(String v) { this.dialect = v; return this; }
        public Builder relationAlias(String v) { this.relationAlias = v; return this; }
        public Builder permissionState(String v) { this.permissionState = v; return this; }
        public Builder timeWindowDef(TimeWindowDef v) { this.timeWindowDef = v; return this; }
        public Builder dimensionFields(List<String> v) { this.dimensionFields = v; return this; }
        public Builder measureFields(Set<String> v) { this.measureFields = v; return this; }

        public RelationCompileOptions build() { return new RelationCompileOptions(this); }
    }
}
