package com.foggyframework.dataset.model.semantic.member;

import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.impl.query.QmColumnNaming;
import com.foggyframework.dataset.model.semantic.member.permission.QmMemberPermissionDef;
import com.foggyframework.dataset.model.spi.*;

import java.util.*;

/** Source identity shared by member selection, synthetic names and permission translation. */
public final class MemberFieldBinding {
    private MemberFieldBinding() { }

    public static QueryModel sourceModel(QueryModel synthetic, QueryModelLoader loader, String namespace) {
        if (synthetic instanceof SyntheticMemberQueryModel bound) return bound.source();
        String name = synthetic.getName();
        return loader.getJdbcQueryModel(name.substring(0, name.indexOf('#')), namespace);
    }

    public static String path(DbDimension dimension) {
        Deque<String> parts = new ArrayDeque<>();
        Set<DbDimension> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DbDimension current = dimension; current != null; current = current.getParentDimension()) {
            if (!seen.add(current)) throw new IllegalArgumentException("cyclic member dimension path");
            parts.addFirst(current.getEffectiveName());
        }
        return String.join("$", parts);
    }

    public static List<DbDimension> dimensions(TableModel model) {
        List<DbDimension> result = new ArrayList<>();
        Set<DbDimension> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (model != null && model.getDimensions() != null) {
            for (DbDimension dimension : model.getDimensions()) collect(dimension, result, seen);
        }
        return result;
    }

    private static void collect(DbDimension dimension, List<DbDimension> result, Set<DbDimension> seen) {
        if (dimension == null || !seen.add(dimension)) return;
        result.add(dimension);
        if (dimension.getChildDimensions() != null)
            for (DbDimension child : dimension.getChildDimensions()) collect(child, result, seen);
    }

    public static DbDimension resolve(QueryModel model, String field) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("member field cannot be blank");
        String selector = field.trim();
        // Exact query names win. Output aliases are deliberately not searched.
        if (model.getJdbcQueryColumns() != null) {
            for (DbQueryColumn column : model.getJdbcQueryColumns()) {
                if (selector.equals(column.getName())) {
                    DbDimension owner = owner(model, column.getSelectColumn());
                    if (owner != null) return owner;
                    throw new IllegalArgumentException("member field is not a dimension: " + selector);
                }
            }
        }
        List<DbDimension> matches = new ArrayList<>();
        for (DbDimension dimension : dimensions(model.getJdbcModel())) {
            Set<String> bases = Set.copyOf(Arrays.asList(path(dimension), path(dimension).replace('$', '.'),
                    path(dimension).replace('$', '_'), dimension.getEffectiveName()));
            boolean match = bases.stream().anyMatch(base -> selector.equals(base)
                    || selector.equals(base + "$id") || selector.equals(base + "$caption"));
            if (!match && dimension instanceof com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport support
                    && support.getJdbcProperties() != null) {
                match = support.getJdbcProperties().stream().anyMatch(p -> selector.equals(p.getName())
                        || bases.stream().anyMatch(base -> selector.equals(base + "$" + leaf(p.getName()))));
            }
            if (match) matches.add(dimension);
        }
        if (matches.size() != 1) throw new IllegalArgumentException(
                (matches.isEmpty() ? "cannot resolve" : "ambiguous") + " member field: " + selector);
        return matches.get(0);
    }

    public static DbDimension canonicalDimension(TableModel model, String canonicalPath) {
        return dimensions(model).stream().filter(d -> path(d).equals(canonicalPath)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown canonical member dimension: " + canonicalPath));
    }

    private static DbDimension owner(QueryModel model, DbColumn column) {
        if (column == null) return null;
        DbDimensionColumn dimension = column.getDecorate(DbDimensionColumn.class);
        if (dimension != null) return dimension.getDimension();
        DbPropertyColumn property = column.getDecorate(DbPropertyColumn.class);
        if (property != null) for (DbDimension candidate : dimensions(model.getJdbcModel())) {
            if (candidate instanceof com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport support
                    && support.getJdbcProperties() != null && support.getJdbcProperties().stream()
                    .anyMatch(p -> p == property.getProperty())) return candidate;
        }
        return null;
    }

    public static QmMemberPermissionDef permission(QueryModel model, String canonicalPath) {
        QueryModelSupport support = model.getDecorate(QueryModelSupport.class);
        QmMemberPermissionDef result = null;
        if (support != null && support.getMemberPermissions() != null) {
            for (QmMemberPermissionDef permission : support.getMemberPermissions()) {
                if (permission != null && path(resolve(model, permission.getDimension())).equals(canonicalPath)) {
                    if (result != null) throw new IllegalArgumentException("duplicate member permission: " + canonicalPath);
                    result = permission;
                }
            }
        }
        return result;
    }

    /** Translate a declared source column using owner and role, never output alias text. */
    public static String mapColumn(QueryModel source, String canonicalPath, DbQueryColumn column) {
        DbColumn selected = column.getSelectColumn();
        DbDimension dimension = owner(source, selected);
        if (dimension == null) return null;
        String ownerPath = path(dimension);
        if (!ownerPath.equals(canonicalPath) && !ownerPath.startsWith(canonicalPath + "$")) return null;
        String prefix = ownerPath.equals(canonicalPath) ? "" : ownerPath.substring(canonicalPath.length() + 1) + "$";
        DbPropertyColumn property = selected.getDecorate(DbPropertyColumn.class);
        if (property != null) return prefix + leaf(property.getProperty().getName());
        DbDimensionColumn dc = selected.getDecorate(DbDimensionColumn.class);
        return dc == null ? null : prefix + (dc.isCaptionColumn() ? "caption" : "id");
    }

    public static String mapField(QueryModel source, String canonicalPath, String field, Set<String> syntheticFields) {
        if (field == null) throw new IllegalArgumentException("member permission field cannot be null");
        if (source.getJdbcQueryColumns() != null) for (DbQueryColumn column : source.getJdbcQueryColumns()) {
            if (field.equals(column.getName())) return requireMapped(mapColumn(source, canonicalPath, column), syntheticFields);
        }
        DbDimension target = canonicalDimension(source.getJdbcModel(), canonicalPath);
        String mapped = null;
        for (String prefix : List.of(canonicalPath, canonicalPath.replace('$', '.'), canonicalPath.replace('$', '_'), target.getEffectiveName())) {
            if (field.equals(prefix)) mapped = "id";
            else if (field.startsWith(prefix + "$")) mapped = field.substring(prefix.length() + 1);
            else if (field.startsWith(prefix + ".")) mapped = field.substring(prefix.length() + 1).replace('.', '$');
        }
        // Legacy member predicates may already use canonical synthetic fields.
        if (mapped == null && syntheticFields.contains(field) && source.findJdbcQueryColumnByName(field, false) == null)
            mapped = field;
        return requireMapped(mapped, syntheticFields);
    }

    private static String requireMapped(String mapped, Set<String> fields) {
        if (mapped == null || !fields.contains(mapped))
            throw new IllegalArgumentException("row permission cannot be represented by the member source query");
        return mapped;
    }

    public static Set<String> allowedFields(QueryModel source, String canonicalPath, Set<String> allowed, Set<String> fields) {
        if (allowed == null) return null;
        Set<String> result = new LinkedHashSet<>();
        for (String field : fields) {
            List<DbQueryColumn> declarations = source.getJdbcQueryColumns().stream()
                    .filter(c -> field.equals(mapColumn(source, canonicalPath, c))).toList();
            List<DbQueryColumn> named = declarations.stream().filter(QmColumnNaming.overrides(source)::contains).toList();
            if (!named.isEmpty()) declarations = named;
            if (!declarations.isEmpty()) {
                // Auto-generated legacy columns must not reopen a renamed, hidden projection.
                if (declarations.stream().allMatch(c -> allowed.contains(QmColumnNaming.permissionName(c.getName())))) result.add(field);
            } else {
                DbDimension dimension = canonicalDimension(source.getJdbcModel(), canonicalPath);
                if (allowed.contains(QmColumnNaming.permissionName(canonicalPath + "$" + field))
                        || allowed.contains(QmColumnNaming.permissionName(dimension.getEffectiveName() + "$" + field))) result.add(field);
            }
        }
        return result;
    }

    private static String leaf(String field) { return field.substring(field.lastIndexOf('$') + 1); }
}
