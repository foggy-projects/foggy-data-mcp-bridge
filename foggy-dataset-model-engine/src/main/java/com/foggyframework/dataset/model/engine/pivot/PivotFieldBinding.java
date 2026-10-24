package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.impl.query.QmColumnNaming;
import com.foggyframework.dataset.model.semantic.member.MemberFieldBinding;
import com.foggyframework.dataset.model.spi.*;

import java.util.*;

/** Use the same source-owner/role binding as member queries, including explicit QM naming overlays. */
public final class PivotFieldBinding {
    private PivotFieldBinding() {}

    public static String role(QueryModel model, String dimension, String role) {
        if (model == null) return dimension + "$" + role;
        var candidates =
                model.getJdbcQueryColumns().stream()
                        .filter(c -> role.equals(MemberFieldBinding.mapColumn(model, dimension, c)))
                        .toList();
        var named = candidates.stream().filter(QmColumnNaming.overrides(model)::contains).toList();
        if (!named.isEmpty()) candidates = named;
        if (candidates.isEmpty())
            throw new IllegalArgumentException(
                    "PIVOT_FIELD_BINDING_MISSING: " + dimension + "$" + role);
        return candidates.get(0).getName();
    }

    public static String dimension(QueryModel model, String field) {
        return MemberFieldBinding.path(MemberFieldBinding.resolve(model, field));
    }

    /** Prefer an actual declared input on the axis over an arbitrary same-source projection. */
    public static Optional<String> roleOnAxis(
            QueryModel model, String dimension, String role, Collection<String> axis) {
        if (model == null) return Optional.empty();
        return axis.stream()
                .filter(
                        field ->
                                model.getJdbcQueryColumns().stream()
                                        .anyMatch(
                                                column ->
                                                        field.equals(column.getName())
                                                                && role.equals(
                                                                        MemberFieldBinding
                                                                                .mapColumn(
                                                                                        model,
                                                                                        dimension,
                                                                                        column))))
                .findFirst();
    }
}
