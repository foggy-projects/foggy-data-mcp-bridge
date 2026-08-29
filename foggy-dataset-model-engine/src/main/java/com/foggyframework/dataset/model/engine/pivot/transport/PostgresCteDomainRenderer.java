package com.foggyframework.dataset.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;
import java.util.ArrayList;
import java.util.List;

public class PostgresCteDomainRenderer implements DomainRelationRenderer {

    @Override
    public DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan) throws DomainTransportRefusalException {
        plan.validateForRender();

        int paramCount = plan.parameterCount();
        if (paramCount > 65000) {
            throw new DomainTransportRefusalException("PostgreSQL bind parameter limit exceeded: " + paramCount);
        }

        StringBuilder sql = new StringBuilder();
        sql.append(plan.getRelationName()).append("(");
        List<String> fieldNames = new ArrayList<>();
        List<String> columnAliases = new ArrayList<>();
        for (DomainTransportField field : plan.getFields()) {
            columnAliases.add(field.getName());
            fieldNames.add(dialect.quoteIdentifier(field.getName()));
        }
        sql.append(String.join(", ", fieldNames));
        sql.append(") AS (\n  ");

        List<Object> params = new ArrayList<>();
        List<String> tupleStrings = new ArrayList<>();
        for (DomainTransportTuple tuple : plan.getTuples()) {
            List<String> placeholders = new ArrayList<>();
            for (Object val : tuple.getValues()) {
                placeholders.add("?");
                params.add(val);
            }
            tupleStrings.add("(" + String.join(", ", placeholders) + ")");
        }
        String cteBody = "VALUES " + String.join(",\n       ", tupleStrings);
        sql.append(cteBody.replace("\n", "\n  "));
        sql.append("\n)");

        StringBuilder joinPredicate = new StringBuilder();
        List<String> joinParts = new ArrayList<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String col = fieldNames.get(i);
            joinParts.add("_base." + col + " IS NOT DISTINCT FROM _d." + col);
        }
        joinPredicate.append(String.join(" AND ", joinParts));

        return DomainRelationRenderResult.builder()
                .sqlFragment(sql.toString())
                .cteAlias(plan.getRelationName())
                .cteColumnAliases(columnAliases)
                .cteBody(cteBody)
                .params(params)
                .joinPredicate(joinPredicate.toString())
                .placement(DomainTransportPlacement.CTE)
                .build();
    }
}
