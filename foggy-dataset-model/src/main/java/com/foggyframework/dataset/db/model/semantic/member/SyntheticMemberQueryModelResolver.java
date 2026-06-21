package com.foggyframework.dataset.db.model.semantic.member;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.MemberPermissionDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.MemberPermissionPatchDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.MemberPermissionSliceDef;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * synthetic member-QM 解析器。
 *
 * <p>阶段1只负责：
 * <ul>
 *   <li>根据 {@code model + fieldName} 解析唯一的根维度</li>
 *   <li>生成 synthetic member-QM 命名</li>
 *   <li>按根维度 canonical 字段 + 嵌套子维度相对路径 + 父子维保留字段构建 schema</li>
 * </ul>
 */
public class SyntheticMemberQueryModelResolver {

    public static final String MODEL_SEPARATOR = "#";
    public static final String FIELD_SEPARATOR = "$";

    public SyntheticMemberQueryModelDescriptor resolve(QueryModelLoader loader,
                                                       String queryModelName,
                                                       String fieldName,
                                                       String namespace) {
        Objects.requireNonNull(loader, "queryModelLoader cannot be null");
        String normalizedNamespace = normalizeNamespace(namespace);
        QueryModel queryModel = loader.getJdbcQueryModel(queryModelName, normalizedNamespace);
        if (queryModel == null) {
            throw new IllegalArgumentException("query model not found: " + queryModelName);
        }
        return resolve(queryModel, fieldName, normalizedNamespace);
    }

    public SyntheticMemberQueryModelDescriptor resolve(QueryModel queryModel,
                                                       String fieldName,
                                                       String namespace) {
        Objects.requireNonNull(queryModel, "queryModel cannot be null");

        String normalizedNamespace = normalizeNamespace(namespace);
        String normalizedFieldName = normalizeFieldName(fieldName);
        if (StringUtils.isEmpty(normalizedFieldName)) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }

        TableModel tableModel = queryModel.getJdbcModel();
        if (tableModel == null) {
            throw new IllegalArgumentException("query model has no jdbc model: " + queryModel.getName());
        }

        DimensionMatch match = resolveDimensionMatch(tableModel, normalizedFieldName);
        if (match == null) {
            throw new IllegalArgumentException("cannot resolve synthetic member-QM from fieldName: " + normalizedFieldName);
        }

        String syntheticModelName = buildSyntheticModelName(queryModel.getName(), match.rootDimension().getEffectiveName());
        SyntheticMemberQueryModelSchema schema = buildSchema(queryModel.getName(), syntheticModelName, match.rootDimension());
        return new SyntheticMemberQueryModelDescriptor(
                normalizedNamespace,
                queryModel.getName(),
                syntheticModelName,
                match.rootDimension().getEffectiveName(),
                normalizedFieldName,
                match.matchedPath(),
                isHierarchyDimension(match.matchedDimension()),
                schema
        );
    }

    public SyntheticMemberQueryModelSchema buildSchema(String sourceModelName,
                                                       String syntheticModelName,
                                                       DbDimension rootDimension) {
        Objects.requireNonNull(rootDimension, "rootDimension cannot be null");

        String rootBase = requireDimensionBase(rootDimension);
        SchemaBuildState state = new SchemaBuildState(sourceModelName, syntheticModelName, rootBase);
        SyntheticMemberPathNode root = buildNode(rootDimension, "", state);

        return new SyntheticMemberQueryModelSchema(
                sourceModelName,
                syntheticModelName,
                rootBase,
                root,
                List.copyOf(state.fields),
                Map.copyOf(state.nodeIndex),
                Map.copyOf(state.fieldIndex),
                state.hierarchySupported
        );
    }

    public String buildSyntheticModelName(String queryModelName, String dimFieldBase) {
        return queryModelName + MODEL_SEPARATOR + dimFieldBase;
    }

    public static String normalizeNamespace(String namespace) {
        return namespace == null ? "" : namespace.trim();
    }

    public static String buildCacheKey(String namespace, String sourceModelName, String dimFieldBase) {
        return normalizeNamespace(namespace) + "|" + sourceModelName + "|" + dimFieldBase;
    }

    private SyntheticMemberPathNode buildNode(DbDimension dimension,
                                              String relativePath,
                                              SchemaBuildState state) {
        String effectiveName = requireDimensionBase(dimension);
        boolean root = StringUtils.isEmpty(relativePath);
        boolean hierarchy = isHierarchyDimension(dimension);
        state.hierarchySupported |= hierarchy;

        List<SyntheticMemberFieldSchema> fields = new ArrayList<>();
        Set<String> exposedFieldNames = new LinkedHashSet<>();

        if (root) {
            addField(fields, exposedFieldNames, "id",
                    sourceRef(state.rootBase, "id"),
                    "",
                    SyntheticMemberFieldKind.ID,
                    false,
                    hierarchy,
                    state);
            addField(fields, exposedFieldNames, "caption",
                    sourceRef(state.rootBase, "caption"),
                    "",
                    SyntheticMemberFieldKind.CAPTION,
                    false,
                    hierarchy,
                    state);

            for (DbProperty property : getDeclaredProperties(dimension)) {
                String propertyName = normalizeFieldName(property.getName());
                if (StringUtils.isEmpty(propertyName)) {
                    continue;
                }
                addField(fields, exposedFieldNames, propertyName,
                        sourceRef(state.rootBase, propertyName),
                        "",
                        SyntheticMemberFieldKind.PROPERTY,
                        false,
                        hierarchy,
                        state);
            }

            addPermissionPatchFields(dimension, fields, exposedFieldNames, "", state.rootBase, hierarchy, state);

            if (hierarchy) {
                addHierarchyFields(fields, exposedFieldNames, "", state.rootBase, state);
            }
        } else {
            String prefix = relativePath + FIELD_SEPARATOR;
            addField(fields, exposedFieldNames, prefix + "id",
                    sourceRef(relativePath, "id"),
                    relativePath,
                    SyntheticMemberFieldKind.ID,
                    false,
                    hierarchy,
                    state);
            addField(fields, exposedFieldNames, prefix + "caption",
                    sourceRef(relativePath, "caption"),
                    relativePath,
                    SyntheticMemberFieldKind.CAPTION,
                    false,
                    hierarchy,
                    state);

            for (DbProperty property : getDeclaredProperties(dimension)) {
                String propertyName = normalizeFieldName(property.getName());
                if (StringUtils.isEmpty(propertyName)) {
                    continue;
                }
                addField(fields, exposedFieldNames, prefix + propertyName,
                        sourceRef(relativePath, propertyName),
                        relativePath,
                        SyntheticMemberFieldKind.PROPERTY,
                        false,
                        hierarchy,
                        state);
            }

            addPermissionPatchFields(dimension, fields, exposedFieldNames, relativePath, relativePath, hierarchy, state);

            if (hierarchy) {
                addHierarchyFields(fields, exposedFieldNames, relativePath, relativePath, state);
            }
        }

        List<SyntheticMemberPathNode> children = new ArrayList<>();
        for (DbDimension child : safeChildren(dimension)) {
            String childRelativePath = root
                    ? requireDimensionBase(child)
                    : relativePath + FIELD_SEPARATOR + requireDimensionBase(child);
            children.add(buildNode(child, childRelativePath, state));
        }

        SyntheticMemberPathNode node = new SyntheticMemberPathNode(
                relativePath,
                effectiveName,
                root,
                hierarchy,
                List.copyOf(fields),
                List.copyOf(children)
        );

        registerNode(state, node, relativePath, root, effectiveName);
        return node;
    }

    private void addPermissionPatchFields(DbDimension dimension,
                                          List<SyntheticMemberFieldSchema> fields,
                                          Set<String> exposedFieldNames,
                                          String nodePath,
                                          String sourcePath,
                                          boolean hierarchy,
                                          SchemaBuildState state) {
        for (String fieldName : collectPermissionPatchFields(dimension)) {
            String normalized = normalizeFieldName(fieldName);
            if (StringUtils.isEmpty(normalized)) {
                continue;
            }
            String exposedName = StringUtils.isEmpty(nodePath)
                    ? normalized
                    : nodePath + FIELD_SEPARATOR + normalized;
            if (state.fieldIndex.containsKey(exposedName)) {
                continue;
            }
            addField(fields, exposedFieldNames, exposedName,
                    sourceRef(sourcePath, normalized),
                    nodePath,
                    SyntheticMemberFieldKind.PROPERTY,
                    true,
                    hierarchy,
                    state);
        }
    }

    private Set<String> collectPermissionPatchFields(DbDimension dimension) {
        if (!(dimension instanceof DbDimensionSupport support)
                || support.getMemberPermission() == null
                || support.getMemberPermission().getPatch() == null) {
            return Set.of();
        }
        MemberPermissionDef permission = support.getMemberPermission();
        MemberPermissionPatchDef patch = permission.getPatch();
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (patch.getForcedSlice() != null) {
            for (MemberPermissionSliceDef slice : patch.getForcedSlice()) {
                if (slice != null && StringUtils.isNotEmpty(slice.getField())) {
                    fields.add(slice.getField());
                }
            }
        }
        if (patch.getForcedOrderBy() != null) {
            for (OrderRequestDef order : patch.getForcedOrderBy()) {
                if (order != null && StringUtils.isNotEmpty(order.getField())) {
                    fields.add(order.getField());
                }
            }
        }
        return fields;
    }

    private void addHierarchyFields(List<SyntheticMemberFieldSchema> fields,
                                    Set<String> exposedFieldNames,
                                    String nodePath,
                                    String sourcePath,
                                    SchemaBuildState state) {
        String prefix = StringUtils.isEmpty(nodePath) ? "" : nodePath + FIELD_SEPARATOR;
        addField(fields, exposedFieldNames, prefix + "parentId",
                sourceRef(sourcePath, "parentId"),
                nodePath,
                SyntheticMemberFieldKind.PARENT_ID,
                true,
                true,
                state);
        addField(fields, exposedFieldNames, prefix + "depth",
                sourceRef(sourcePath, "depth"),
                nodePath,
                SyntheticMemberFieldKind.DEPTH,
                true,
                true,
                state);
        addField(fields, exposedFieldNames, prefix + "hasChildren",
                sourceRef(sourcePath, "hasChildren"),
                nodePath,
                SyntheticMemberFieldKind.HAS_CHILDREN,
                true,
                true,
                state);
    }

    private void addField(List<SyntheticMemberFieldSchema> fields,
                          Set<String> exposedFieldNames,
                          String exposedName,
                          String sourceRef,
                          String nodePath,
                          SyntheticMemberFieldKind kind,
                          boolean reserved,
                          boolean hierarchyScoped,
                          SchemaBuildState state) {
        SyntheticMemberFieldSchema field = new SyntheticMemberFieldSchema(
                exposedName,
                sourceRef,
                nodePath,
                kind,
                reserved,
                hierarchyScoped
        );
        SyntheticMemberFieldSchema existing = state.fieldIndex.get(exposedName);
        if (existing != null) {
            if (canPromoteReservedField(existing, field)) {
                replaceField(fields, existing, field);
                state.fieldIndex.put(exposedName, field);
                return;
            }
            throw new IllegalArgumentException("duplicate synthetic member field: " + exposedName);
        }
        if (!exposedFieldNames.add(exposedName)) {
            throw new IllegalArgumentException("duplicate synthetic member field: " + exposedName);
        }
        fields.add(field);
        state.fields.add(field);
        state.fieldIndex.put(exposedName, field);
    }

    private void registerNode(SchemaBuildState state,
                              SyntheticMemberPathNode node,
                              String relativePath,
                              boolean root,
                              String effectiveName) {
        state.nodeIndex.put(relativePath, node);
        if (root) {
            state.nodeIndex.put(effectiveName, node);
        }
    }

    private DimensionMatch resolveDimensionMatch(TableModel tableModel, String fieldName) {
        List<DimensionMatch> matches = new ArrayList<>();
        for (DbDimension root : safeRoots(tableModel)) {
            collectMatches(root, "", fieldName, root, matches);
        }

        if (matches.isEmpty()) {
            return null;
        }

        matches.sort((a, b) -> Integer.compare(b.depth(), a.depth()));
        DimensionMatch best = matches.get(0);
        if (matches.size() > 1) {
            DimensionMatch second = matches.get(1);
            if (second.depth() == best.depth() && !StringUtils.equals(second.matchedPath(), best.matchedPath())) {
                throw new IllegalArgumentException("ambiguous synthetic member field: " + fieldName);
            }
        }
        return best;
    }

    private void collectMatches(DbDimension current,
                                String relativePath,
                                String fieldName,
                                DbDimension root,
                                List<DimensionMatch> matches) {
        String currentPath = StringUtils.isEmpty(relativePath)
                ? requireDimensionBase(root)
                : relativePath;
        String effectiveName = requireDimensionBase(current);

        if (matchesNode(fieldName, currentPath, effectiveName)) {
            matches.add(DimensionMatch.node(root, current, currentPath));
        }

        if (current instanceof DbDimensionSupport support) {
            for (DbProperty property : safeProperties(support)) {
                String propertyName = normalizeFieldName(property.getName());
                if (StringUtils.isEmpty(propertyName)) {
                    continue;
                }
                if (matchesProperty(fieldName, currentPath, effectiveName, propertyName)) {
                    matches.add(DimensionMatch.property(root, current, currentPath));
                }
            }
        }

        for (DbDimension child : safeChildren(current)) {
            String childPath = StringUtils.isEmpty(relativePath)
                    ? requireDimensionBase(child)
                    : currentPath + FIELD_SEPARATOR + requireDimensionBase(child);
            collectMatches(child, childPath, fieldName, root, matches);
        }
    }

    private boolean matchesNode(String fieldName, String currentPath, String effectiveName) {
        return StringUtils.equals(fieldName, currentPath)
                || StringUtils.equals(fieldName, effectiveName)
                || fieldName.startsWith(currentPath + FIELD_SEPARATOR);
    }

    private boolean matchesProperty(String fieldName,
                                    String currentPath,
                                    String effectiveName,
                                    String propertyName) {
        return StringUtils.equals(fieldName, propertyName)
                || StringUtils.equals(fieldName, effectiveName + FIELD_SEPARATOR + propertyName)
                || StringUtils.equals(fieldName, currentPath + FIELD_SEPARATOR + propertyName);
    }

    private List<DbDimension> safeRoots(TableModel tableModel) {
        List<DbDimension> dimensions = tableModel.getDimensions();
        return dimensions == null ? List.of() : dimensions;
    }

    private List<DbDimension> safeChildren(DbDimension dimension) {
        List<DbDimension> children = dimension.getChildDimensions();
        return children == null ? List.of() : children;
    }

    private List<DbProperty> safeProperties(DbDimensionSupport support) {
        List<DbProperty> properties = support.getJdbcProperties();
        return properties == null ? List.of() : properties;
    }

    private List<DbProperty> getDeclaredProperties(DbDimension dimension) {
        if (dimension instanceof DbDimensionSupport support) {
            return safeProperties(support);
        }
        return List.of();
    }

    private boolean isHierarchyDimension(DbDimension dimension) {
        return dimension != null
                && (dimension instanceof DbModelParentChildDimensionImpl
                || dimension.getDecorate(DbModelParentChildDimensionImpl.class) != null);
    }

    private String sourceRef(String path, String field) {
        if (StringUtils.isEmpty(path)) {
            return field;
        }
        return path + FIELD_SEPARATOR + field;
    }

    private boolean canPromoteReservedField(SyntheticMemberFieldSchema existing,
                                            SyntheticMemberFieldSchema incoming) {
        return incoming.reserved()
                && !existing.reserved()
                && StringUtils.equals(existing.sourceRef(), incoming.sourceRef())
                && StringUtils.equals(existing.nodePath(), incoming.nodePath());
    }

    private void replaceField(List<SyntheticMemberFieldSchema> fields,
                              SyntheticMemberFieldSchema existing,
                              SyntheticMemberFieldSchema replacement) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equals(existing.name())) {
                fields.set(i, replacement);
                return;
            }
        }
        throw new IllegalStateException("field not found for replacement: " + existing.name());
    }

    private String requireDimensionBase(DbDimension dimension) {
        String name = dimension == null ? null : dimension.getEffectiveName();
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("dimension effective name cannot be blank");
        }
        return name;
    }

    private String normalizeFieldName(String name) {
        return name == null ? null : name.trim();
    }

    private static final class SchemaBuildState {
        private final String sourceModelName;
        private final String syntheticModelName;
        private final String rootBase;
        private final Map<String, SyntheticMemberPathNode> nodeIndex = new LinkedHashMap<>();
        private final Map<String, SyntheticMemberFieldSchema> fieldIndex = new LinkedHashMap<>();
        private final List<SyntheticMemberFieldSchema> fields = new ArrayList<>();
        private boolean hierarchySupported;

        private SchemaBuildState(String sourceModelName, String syntheticModelName, String rootBase) {
            this.sourceModelName = sourceModelName;
            this.syntheticModelName = syntheticModelName;
            this.rootBase = rootBase;
        }
    }

    private record DimensionMatch(
            DbDimension rootDimension,
            DbDimension matchedDimension,
            String matchedPath
    ) {
        private static DimensionMatch node(DbDimension root, DbDimension matchedDimension, String matchedPath) {
            return new DimensionMatch(root, matchedDimension, matchedPath);
        }

        private static DimensionMatch property(DbDimension root, DbDimension matchedDimension, String matchedPath) {
            return new DimensionMatch(root, matchedDimension, matchedPath);
        }

        private int depth() {
            if (StringUtils.isEmpty(matchedPath)) {
                return 0;
            }
            return matchedPath.split("\\Q" + FIELD_SEPARATOR + "\\E").length;
        }
    }
}
