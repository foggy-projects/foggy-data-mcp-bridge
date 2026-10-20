package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.model.impl.DbColumnDelegate;
import com.foggyframework.dataset.model.impl.query.DbQueryColumnImpl;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.support.SimpleQueryObject;
import com.foggyframework.dataset.model.spi.support.SimpleSqlJdbcColumn;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderByFieldBindingsTest {
    private DbColumn find(List<DbColumn> columns, DbColumn source) {
        return new OrderByFieldBindings(columns, columns).projection(source);
    }
    private DbColumn physical(QueryObject table, String name) {
        return new SimpleSqlJdbcColumn(table, new SqlColumn(name, name, Types.INTEGER), name, name, name);
    }

    private DbColumn alias(DbColumn source, String alias) {
        return new DbQueryColumnImpl(source, alias, alias, alias);
    }

    @Test void differentPublicAliasesResolveToTheActualSelectedProjection() {
        var source = physical(SimpleQueryObject.of("artist", "a", null), "id");
        var selected = alias(source, "artist$id");
        assertSame(selected, find(List.of(selected), alias(source, "track.album.artist$id")));
        assertSame(selected, find(List.of(selected), source));
    }

    @Test void nestedProjectionAliasesAreTransparent() {
        var source = physical(SimpleQueryObject.of("artist", "a", null), "id");
        var selected = alias(alias(source, "inner"), "outer");
        assertSame(selected, find(List.of(selected), source));
    }

    @Test void sameAliasOnDifferentPhysicalColumnsDoesNotMatch() {
        var table = SimpleQueryObject.of("artist", "a", null);
        assertNull(find(List.of(alias(physical(table, "id"), "shared")),
                alias(physical(table, "other_id"), "shared")));
    }

    @Test void sameRootBoundToTwoTableInstancesDoesNotMatch() {
        var left = SimpleQueryObject.of("artist", "left_artist", null);
        var right = SimpleQueryObject.of("artist", "right_artist", null);
        var source = physical(left, "id");
        DbColumn boundRight = new DbColumnDelegate(source) {
            @Override public QueryObject getQueryObject() { return right; }
            @Override public Object getExtData() { return null; }
            @Override public com.foggyframework.dataset.model.impl.AiObject getAi() { return null; }
        };
        assertSame(source.getRoot(), boundRight.getRoot());
        assertNull(find(List.of(alias(source, "id")), alias(boundRight, "id")));
    }

    @Test void expressionDecoratorsAreNotReducedToTheirUnderlyingPhysicalColumn() {
        var source = physical(SimpleQueryObject.of("artist", "a", null), "id");
        DbColumn expression = new DbColumnDelegate(source) {
            @Override public String getDeclare() { return "a.id * -1"; }
            @Override public Object getExtData() { return null; }
            @Override public com.foggyframework.dataset.model.impl.AiObject getAi() { return null; }
        };
        var selected = alias(expression, "reverseId");
        assertNull(find(List.of(selected), source));
        assertSame(selected, find(List.of(selected), expression));
    }

    @Test void missingSourceDoesNotMatch() {
        assertNull(find(List.of(), null));
    }

    @Test void aggregateLineageUsesStageOutputWithoutUnwrappingItsExpression() {
        var source = physical(SimpleQueryObject.of("artist", "a", null), "id");
        var selected = alias(source, "publicId");
        var aggregate = new com.foggyframework.dataset.model.spi.support.AggregationDbColumn(
                source.getQueryObject(), "publicId", "SUM(a.id)",
                com.foggyframework.dataset.model.spi.DbColumnType.NUMBER,
                com.foggyframework.dataset.model.spi.DbAggregation.SUM);
        var bindings = new OrderByFieldBindings(List.of(selected), List.of(aggregate));
        assertSame(aggregate, bindings.projection("publicId", source));
        assertSame(aggregate, bindings.projection(alias(source, "other")));
        assertNotSame(OrderByFieldBindings.expressionIdentity(source),
                OrderByFieldBindings.expressionIdentity(aggregate));
    }
}
