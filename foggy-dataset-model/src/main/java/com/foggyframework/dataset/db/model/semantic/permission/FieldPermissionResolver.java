package com.foggyframework.dataset.db.model.semantic.permission;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.permission.FieldPermissionRuleDef;
import com.foggyframework.dataset.db.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.FieldAccessPermissionStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbMeasure;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.DbQueryProperty;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves TM/QM declarative field permissions together with runtime fieldAccess
 * and deniedColumns into one effective allowlist.
 */
@Component
public class FieldPermissionResolver {

    public FieldPermissionResolution resolve(ModelResultContext ctx) {
        QueryModel queryModel = ctx == null ? null : ctx.getQueryModel();
        Set<String> additionalFields = collectRequestCalculatedFieldNames(ctx);
        return resolve(queryModel,
                ctx == null ? null : ctx.getNamespace(),
                ctx == null ? null : ctx.getSecurityContext(),
                ctx == null ? null : ctx.getFieldAccess(),
                ctx == null ? null : ctx.getDeniedColumns(),
                additionalFields);
    }

    public FieldPermissionResolution resolve(QueryModel queryModel,
                                             String namespace,
                                             ModelResultContext.SecurityContext securityContext,
                                             Set<String> runtimeFieldAccess,
                                             List<DeniedPhysicalColumn> deniedColumns) {
        return resolve(queryModel, namespace, securityContext, runtimeFieldAccess, deniedColumns, Set.of());
    }

    public FieldPermissionResolution resolve(QueryModel queryModel,
                                             String namespace,
                                             ModelResultContext.SecurityContext securityContext,
                                             Set<String> runtimeFieldAccess,
                                             List<DeniedPhysicalColumn> deniedColumns,
                                             Set<String> additionalFields) {
        if (queryModel == null) {
            return new FieldPermissionResolution(normalizeRuntimeFieldAccess(runtimeFieldAccess), Set.of());
        }

        Set<String> allFields = collectAllBaseFieldNames(queryModel, additionalFields);
        EvalContext evalContext = new EvalContext(namespace, securityContext);

        EffectiveLayer tmLayer = resolveTableModelLayer(queryModel, allFields, evalContext);
        EffectiveLayer qmLayer = evaluateLayer(queryModel.getFieldPermissions(), allFields, evalContext,
                "QM " + queryModel.getName());

        boolean constrained = false;
        Set<String> effective = new LinkedHashSet<>();

        if (tmLayer.constrained) {
            effective.addAll(tmLayer.allowed);
            constrained = true;
        }
        if (qmLayer.constrained) {
            if (!constrained) {
                effective.addAll(qmLayer.allowed);
                constrained = true;
            } else {
                effective.retainAll(qmLayer.allowed);
            }
        }

        Set<String> normalizedRuntimeFieldAccess = normalizeRuntimeFieldAccess(runtimeFieldAccess);
        if (normalizedRuntimeFieldAccess != null) {
            if (!constrained) {
                effective.addAll(normalizedRuntimeFieldAccess);
                constrained = true;
            } else {
                effective.retainAll(normalizedRuntimeFieldAccess);
            }
        }

        Set<String> deniedQmFields = resolveDeniedQmFields(queryModel, deniedColumns);
        if (!deniedQmFields.isEmpty()) {
            if (!constrained) {
                effective.addAll(allFields);
                constrained = true;
            }
            effective.removeAll(deniedQmFields);
        }

        Set<String> effectiveFieldAccess = constrained
                ? Collections.unmodifiableSet(new LinkedHashSet<>(effective))
                : null;
        return new FieldPermissionResolution(effectiveFieldAccess,
                Collections.unmodifiableSet(new LinkedHashSet<>(deniedQmFields)));
    }

    private EffectiveLayer resolveTableModelLayer(QueryModel queryModel, Set<String> allFields, EvalContext context) {
        List<TableModel> tableModels = collectTableModels(queryModel);
        List<TableModelLayer> constrainedLayers = new ArrayList<>();

        for (TableModel tableModel : tableModels) {
            if (tableModel == null) {
                continue;
            }
            Set<String> tableModelFields = collectTableModelBaseFieldNames(tableModel);
            if (tableModelFields.isEmpty()) {
                tableModelFields = allFields;
            }
            EffectiveLayer layer = evaluateLayer(tableModel.getFieldPermissions(), tableModelFields, context,
                    "TM " + tableModel.getName());
            if (!layer.constrained) {
                continue;
            }
            constrainedLayers.add(new TableModelLayer(tableModelFields, layer.allowed));
        }

        if (constrainedLayers.isEmpty()) {
            return EffectiveLayer.unconstrained();
        }

        Set<String> allowed = new LinkedHashSet<>();
        for (String field : allFields) {
            // TM constraints only apply to fields owned by that TM; unresolved ownership falls back above.
            boolean ownedByConstrainedTableModel = false;
            boolean fieldAllowed = true;
            for (TableModelLayer layer : constrainedLayers) {
                if (!layer.fields.contains(field)) {
                    continue;
                }
                ownedByConstrainedTableModel = true;
                if (!layer.allowed.contains(field)) {
                    fieldAllowed = false;
                    break;
                }
            }
            if (!ownedByConstrainedTableModel || fieldAllowed) {
                allowed.add(field);
            }
        }

        return new EffectiveLayer(true, allowed);
    }

    private List<TableModel> collectTableModels(QueryModel queryModel) {
        List<TableModel> tableModels = new ArrayList<>();
        Set<TableModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        addTableModel(tableModels, seen, queryModel.getJdbcModel());
        List<TableModel> list = queryModel.getJdbcModelList();
        if (list != null) {
            for (TableModel tableModel : list) {
                addTableModel(tableModels, seen, tableModel);
            }
        }
        return tableModels;
    }

    private void addTableModel(List<TableModel> tableModels, Set<TableModel> seen, TableModel tableModel) {
        if (tableModel != null && seen.add(tableModel)) {
            tableModels.add(tableModel);
        }
    }

    private EffectiveLayer evaluateLayer(FieldPermissionsDef def, Set<String> allFields, EvalContext context,
                                         String owner) {
        if (def == null || def.isEmpty()) {
            return EffectiveLayer.unconstrained();
        }

        Set<String> allowed = Boolean.FALSE.equals(def.getDefaultVisible())
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allFields);

        applyVisibleRules(allowed, def.getVisibleFields(), allFields, context, owner);

        Set<String> hidden = new LinkedHashSet<>();
        collectMatchedRuleFields(hidden, def.getHiddenFields(), allFields, context, owner, "hiddenFields");
        allowed.removeAll(hidden);

        return new EffectiveLayer(true, allowed);
    }

    private void applyVisibleRules(Set<String> allowed, List<FieldPermissionRuleDef> rules,
                                   Set<String> allFields, EvalContext context, String owner) {
        Set<String> visible = new LinkedHashSet<>();
        collectMatchedRuleFields(visible, rules, allFields, context, owner, "visibleFields");
        allowed.addAll(visible);
    }

    private void collectMatchedRuleFields(Set<String> target, List<FieldPermissionRuleDef> rules,
                                          Set<String> allFields, EvalContext context, String owner,
                                          String ruleSetName) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (FieldPermissionRuleDef rule : rules) {
            if (rule == null) {
                continue;
            }
            Set<String> ruleFields = validateAndNormalizeRuleFields(rule.getFields(), allFields, owner, ruleSetName);
            if (!matches(rule.getWhen(), context)) {
                continue;
            }
            target.addAll(ruleFields);
        }
    }

    private Set<String> validateAndNormalizeRuleFields(List<String> fields, Set<String> allFields,
                                                       String owner, String ruleSetName) {
        if (fields == null || fields.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedFields = new LinkedHashSet<>();
        for (String field : fields) {
            String normalized = normalizeFieldName(field);
            if (StringUtils.isEmpty(normalized)) {
                throw RX.throwB(owner + " fieldPermissions." + ruleSetName + " contains blank field name");
            }
            if (!allFields.contains(normalized)) {
                throw RX.throwB(owner + " fieldPermissions." + ruleSetName
                        + " references unknown field '" + field + "'");
            }
            normalizedFields.add(normalized);
        }
        return normalizedFields;
    }

    private boolean matches(Map<String, Object> when, EvalContext context) {
        if (when == null || when.isEmpty()) {
            return true;
        }
        return evalPredicate(when, context);
    }

    private boolean evalPredicate(Object predicate, EvalContext context) {
        if (predicate == null) {
            return true;
        }
        if (predicate instanceof Boolean bool) {
            return bool;
        }
        if (!(predicate instanceof Map<?, ?> map)) {
            throw RX.throwB("fieldPermissions.when must be an object predicate");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            boolean matched = switch (key) {
                case "and" -> evalAnd(value, context);
                case "or" -> evalOr(value, context);
                case "not" -> !evalPredicate(value, context);
                case "hasAnyGroup" -> intersects(context.groups(), toStringSet(value));
                case "hasAllGroups" -> context.groups().containsAll(toStringSet(value));
                case "hasAnyRole" -> intersects(context.roles(), toStringSet(value));
                case "hasAllRoles" -> context.roles().containsAll(toStringSet(value));
                case "hasAnyPermission" -> intersects(context.permissions(), toStringSet(value));
                case "hasAllPermissions" -> context.permissions().containsAll(toStringSet(value));
                case "namespaceIn" -> containsString(toStringSet(value), context.namespace);
                case "profileIn" -> intersects(context.profiles(), toStringSet(value));
                case "contextEq" -> evalContextEq(value, context);
                case "contextIn" -> evalContextIn(value, context);
                default -> throw RX.throwB("Unsupported fieldPermissions.when predicate: " + key);
            };
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean evalAnd(Object value, EvalContext context) {
        List<Object> predicates = toObjectList(value);
        if (predicates.isEmpty()) {
            return true;
        }
        for (Object item : predicates) {
            if (!evalPredicate(item, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean evalOr(Object value, EvalContext context) {
        List<Object> predicates = toObjectList(value);
        if (predicates.isEmpty()) {
            return false;
        }
        for (Object item : predicates) {
            if (evalPredicate(item, context)) {
                return true;
            }
        }
        return false;
    }

    private boolean evalContextEq(Object value, EvalContext context) {
        if (!(value instanceof Map<?, ?> map)) {
            throw RX.throwB("fieldPermissions.when.contextEq must be an object");
        }
        if (map.containsKey("key") && map.containsKey("value")) {
            return valueEquals(context.value(String.valueOf(map.get("key"))), map.get("value"));
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!valueEquals(context.value(String.valueOf(entry.getKey())), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean evalContextIn(Object value, EvalContext context) {
        if (!(value instanceof Map<?, ?> map)) {
            throw RX.throwB("fieldPermissions.when.contextIn must be an object");
        }
        if (map.containsKey("key")) {
            Object expected = map.containsKey("anyOf") ? map.get("anyOf") : map.get("values");
            return valueIntersects(context.value(String.valueOf(map.get("key"))), expected);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!valueIntersects(context.value(String.valueOf(entry.getKey())), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean valueEquals(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (valueEquals(item, expected)) {
                    return true;
                }
            }
            return false;
        }
        return Objects.equals(actual, expected)
                || Objects.equals(String.valueOf(actual), String.valueOf(expected));
    }

    private boolean valueIntersects(Object actual, Object expected) {
        Set<String> actualValues = toStringSet(actual);
        Set<String> expectedValues = toStringSet(expected);
        return intersects(actualValues, expectedValues);
    }

    private Set<String> collectAllBaseFieldNames(QueryModel queryModel, Set<String> additionalFields) {
        Set<String> fields = new LinkedHashSet<>();

        PhysicalColumnMapping mapping = queryModel.getPhysicalColumnMapping();
        if (mapping != null && mapping.getAllQmFieldNames() != null) {
            addNormalized(fields, mapping.getAllQmFieldNames());
        }

        List<DbQueryColumn> queryColumns = queryModel.getJdbcQueryColumns();
        if (queryColumns != null) {
            for (DbQueryColumn column : queryColumns) {
                if (column == null) {
                    continue;
                }
                addNormalized(fields, column.getName());
                addNormalized(fields, column.getAlias());
                addNormalized(fields, column.getField());
            }
        }

        List<DbQueryProperty> queryProperties = queryModel.getQueryProperties();
        if (queryProperties != null) {
            for (DbQueryProperty queryProperty : queryProperties) {
                if (queryProperty != null) {
                    addNormalized(fields, queryProperty.getName());
                }
            }
        }

        TableModel tableModel = queryModel.getJdbcModel();
        if (tableModel != null) {
            addTableModelFields(fields, tableModel);
        }
        List<TableModel> tableModels = queryModel.getJdbcModelList();
        if (tableModels != null) {
            for (TableModel model : tableModels) {
                addTableModelFields(fields, model);
            }
        }

        addCalculatedFields(fields, queryModel.getPredefinedCalculatedFields());
        addNormalized(fields, additionalFields);

        return fields;
    }

    private void addTableModelFields(Set<String> fields, TableModel tableModel) {
        if (tableModel == null) {
            return;
        }
        List<DbDimension> dimensions = tableModel.getDimensions();
        if (dimensions != null) {
            for (DbDimension dimension : dimensions) {
                if (dimension != null) {
                    addNormalized(fields, dimension.getEffectiveName());
                    addNormalized(fields, dimension.getName());
                }
            }
        }
        List<DbProperty> properties = tableModel.getProperties();
        if (properties != null) {
            for (DbProperty property : properties) {
                if (property != null) {
                    addNormalized(fields, property.getName());
                }
            }
        }
        List<DbMeasure> measures = tableModel.getMeasures();
        if (measures != null) {
            for (DbMeasure measure : measures) {
                if (measure != null) {
                    addNormalized(fields, measure.getName());
                }
            }
        }
    }

    private Set<String> collectTableModelBaseFieldNames(TableModel tableModel) {
        Set<String> fields = new LinkedHashSet<>();
        addTableModelFields(fields, tableModel);
        return fields;
    }

    private void addCalculatedFields(Set<String> fields, List<CalculatedFieldDef> calculatedFields) {
        if (calculatedFields == null) {
            return;
        }
        for (CalculatedFieldDef calculatedField : calculatedFields) {
            if (calculatedField != null) {
                addNormalized(fields, calculatedField.getName());
            }
        }
    }

    private Set<String> collectRequestCalculatedFieldNames(ModelResultContext ctx) {
        if (ctx == null || ctx.getRequest() == null || ctx.getRequest().getParam() == null) {
            return Set.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        addCalculatedFields(fields, ctx.getRequest().getParam().getCalculatedFields());
        return fields;
    }

    private Set<String> resolveDeniedQmFields(QueryModel queryModel, List<DeniedPhysicalColumn> deniedColumns) {
        if (deniedColumns == null || deniedColumns.isEmpty()) {
            return Set.of();
        }
        PhysicalColumnMapping mapping = queryModel.getPhysicalColumnMapping();
        if (mapping == null) {
            return Set.of();
        }
        Set<String> denied = mapping.toDeniedQmFields(deniedColumns);
        Set<String> normalized = new LinkedHashSet<>();
        addNormalized(normalized, denied);
        return normalized;
    }

    private Set<String> normalizeRuntimeFieldAccess(Set<String> runtimeFieldAccess) {
        if (runtimeFieldAccess == null) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        addNormalized(normalized, runtimeFieldAccess);
        return Collections.unmodifiableSet(normalized);
    }

    private void addNormalized(Set<String> target, Collection<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addNormalized(target, value);
        }
    }

    private void addNormalized(Set<String> target, String value) {
        String normalized = normalizeFieldName(value);
        if (StringUtils.isNotEmpty(normalized)) {
            target.add(normalized);
        }
    }

    private String normalizeFieldName(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        return FieldAccessPermissionStep.stripDimensionSuffix(fieldName.trim());
    }

    private boolean intersects(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String item : right) {
            if (left.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsString(Set<String> values, String value) {
        return value != null && values != null && values.contains(value);
    }

    private Set<String> toStringSet(Object value) {
        Set<String> values = new LinkedHashSet<>();
        if (value == null) {
            return values;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addStringValue(values, item);
            }
            return values;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addStringValue(values, Array.get(value, i));
            }
            return values;
        }
        addStringValue(values, value);
        return values;
    }

    private void addStringValue(Set<String> values, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addStringValue(values, item);
            }
            return;
        }
        values.add(String.valueOf(value));
    }

    private List<Object> toObjectList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        return List.of(value);
    }

    private record EffectiveLayer(boolean constrained, Set<String> allowed) {
        static EffectiveLayer unconstrained() {
            return new EffectiveLayer(false, Set.of());
        }
    }

    private record TableModelLayer(Set<String> fields, Set<String> allowed) {
    }

    private static class EvalContext {
        private final String namespace;
        private final ModelResultContext.SecurityContext securityContext;

        private EvalContext(String namespace, ModelResultContext.SecurityContext securityContext) {
            this.namespace = namespace;
            this.securityContext = securityContext;
        }

        Set<String> roles() {
            Set<String> values = new LinkedHashSet<>();
            if (securityContext != null && securityContext.getRoles() != null) {
                values.addAll(securityContext.getRoles());
            }
            values.addAll(attributeValues("roles"));
            values.addAll(attributeValues("role"));
            return values;
        }

        Set<String> groups() {
            Set<String> values = new LinkedHashSet<>();
            values.addAll(attributeValues("groups"));
            values.addAll(attributeValues("group"));
            values.addAll(roles());
            return values;
        }

        Set<String> permissions() {
            Set<String> values = new LinkedHashSet<>();
            values.addAll(attributeValues("permissions"));
            values.addAll(attributeValues("permission"));
            return values;
        }

        Set<String> profiles() {
            Set<String> values = new LinkedHashSet<>();
            values.addAll(attributeValues("profile"));
            values.addAll(attributeValues("profiles"));
            return values;
        }

        Object value(String key) {
            if ("namespace".equals(key)) {
                return namespace;
            }
            if (securityContext == null) {
                return null;
            }
            return switch (key) {
                case "authorization" -> securityContext.getAuthorization();
                case "userId" -> securityContext.getUserId();
                case "tenantId" -> securityContext.getTenantId();
                case "deptId" -> securityContext.getDeptId();
                case "roles" -> securityContext.getRoles();
                case "groups" -> groups();
                case "permissions" -> permissions();
                case "profile", "profiles" -> profiles();
                default -> securityContext.getAttribute(key);
            };
        }

        private Set<String> attributeValues(String key) {
            if (securityContext == null) {
                return Set.of();
            }
            Object value = securityContext.getAttribute(key);
            Set<String> values = new LinkedHashSet<>();
            if (value == null) {
                return values;
            }
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null) {
                        values.add(String.valueOf(item));
                    }
                }
                return values;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object item = Array.get(value, i);
                    if (item != null) {
                        values.add(String.valueOf(item));
                    }
                }
                return values;
            }
            values.add(String.valueOf(value));
            return values;
        }
    }
}
