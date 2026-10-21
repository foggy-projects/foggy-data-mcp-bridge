package com.foggyframework.dataset.model.semantic.member;

import com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.model.impl.query.QmColumnNaming;
import com.foggyframework.dataset.model.semantic.member.permission.SyntheticMemberPermissionResolver;
import com.foggyframework.dataset.model.spi.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Resolves adapter metadata from the current source binding; never caches by model name. */
@Component
public class MemberBindingService {
    private final QueryModelLoader loader;
    public MemberBindingService(QueryModelLoader loader) { this.loader = loader; }

    public record Binding(String modelName, String selectionFieldName, String displayFieldName,
                          boolean hierarchical, List<String> hierarchyOps) { }

    public Binding resolve(String modelName, String selector, String namespace) {
        QueryModel source = loader.getJdbcQueryModel(modelName, namespace);
        DbDimension dimension = MemberFieldBinding.resolve(source, selector);
        String path = MemberFieldBinding.path(dimension);
        var permission = new SyntheticMemberPermissionResolver().resolve(
                dimension instanceof DbDimensionSupport support ? support.getMemberPermission() : null,
                MemberFieldBinding.permission(source, path));
        boolean hierarchical = dimension.getDecorate(DbModelParentChildDimensionImpl.class) != null
                && !Boolean.FALSE.equals(permission.getHierarchyEnabled());
        List<String> ops = hierarchical ? new ArrayList<>(List.of("childrenOf", "descendantsOf",
                "selfAndDescendantsOf", "ancestorsOf", "selfAndAncestorsOf")) : List.of();
        if (hierarchical && permission.getAllowedHierarchyOps() != null) ops.retainAll(permission.getAllowedHierarchyOps());
        return new Binding(modelName + "#" + path, field(source, path, "id"), field(source, path, "caption"),
                hierarchical, List.copyOf(ops));
    }

    private String field(QueryModel source, String path, String role) {
        var candidates = source.getJdbcQueryColumns().stream()
                .filter(c -> role.equals(MemberFieldBinding.mapColumn(source, path, c))).toList();
        var explicit = candidates.stream().filter(QmColumnNaming.overrides(source)::contains).toList();
        if (!explicit.isEmpty()) candidates = explicit;
        if (candidates.isEmpty()) throw new IllegalArgumentException("member role has no source query binding: " + path + "$" + role);
        // Multiple explicit projections are legal; declaration order provides a stable query name.
        return candidates.get(0).getName();
    }
}
