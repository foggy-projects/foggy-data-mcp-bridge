package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.proxy.ColumnRef;
import com.foggyframework.dataset.model.proxy.DimensionProxy;
import com.foggyframework.dataset.model.spi.DbObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Stable typed predicate builder exposed to model permission resolvers.
 */
public final class PermissionPredicateBuilder {

    public PermissionPredicate eq(Object field, Object value) {
        return build(field, "=", value);
    }

    public PermissionPredicate ne(Object field, Object value) {
        return build(field, "!=", value);
    }

    public PermissionPredicate gt(Object field, Object value) {
        return build(field, ">", value);
    }

    public PermissionPredicate gte(Object field, Object value) {
        return build(field, ">=", value);
    }

    public PermissionPredicate lt(Object field, Object value) {
        return build(field, "<", value);
    }

    public PermissionPredicate lte(Object field, Object value) {
        return build(field, "<=", value);
    }

    public PermissionPredicate in(Object field, Object values) {
        return build(field, "in", requireCollection(values, "in"));
    }

    public PermissionPredicate notIn(Object field, Object values) {
        return build(field, "not in", requireCollection(values, "notIn"));
    }

    public PermissionPredicate between(Object field, Object lower, Object upper) {
        List<Object> bounds = new ArrayList<>(2);
        bounds.add(lower);
        bounds.add(upper);
        return build(field, "between", Collections.unmodifiableList(bounds));
    }

    public PermissionPredicate isNull(Object field) {
        return build(field, "is null", null);
    }

    public PermissionPredicate isNotNull(Object field) {
        return build(field, "is not null", null);
    }

    private PermissionPredicate build(Object fieldRef, String operator, Object value) {
        FieldBinding binding = resolveField(fieldRef);
        return PermissionPredicate.provable(
                PermissionPredicate.Origin.QM_MODEL_PERMISSION,
                binding.binding(),
                binding.field(),
                operator,
                value
        );
    }

    private Collection<?> requireCollection(Object value, String operation) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(Array.get(value, index));
            }
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException(operation + " requires an array or collection value");
    }

    private FieldBinding resolveField(Object fieldRef) {
        if (fieldRef instanceof ColumnRef columnRef) {
            return new FieldBinding(
                    columnRef.getTableModelProxy().getModelName(),
                    columnRef.getQualifiedAliasRef()
            );
        }
        if (fieldRef instanceof DimensionProxy dimensionProxy) {
            return new FieldBinding(
                    dimensionProxy.getRootProxy().getModelName(),
                    dimensionProxy.getQualifiedAliasPath()
            );
        }
        if (fieldRef instanceof DbObject dbObject) {
            return new FieldBinding(null, dbObject.getName());
        }
        if (fieldRef instanceof String field && !field.isBlank()) {
            return new FieldBinding(null, field.trim());
        }
        throw new IllegalArgumentException("permission predicate field must be a model field reference or string");
    }

    private record FieldBinding(String binding, String field) {
    }
}
