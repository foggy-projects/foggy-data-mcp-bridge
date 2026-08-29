package com.foggyframework.dataset.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;
import java.util.ArrayList;
import java.util.List;

public class SqlServerCteDomainRenderer implements DomainRelationRenderer {

    private static final int MAX_PARAMS = 2000;

    @Override
    public DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan)
            throws DomainTransportRefusalException {
        plan.validateForRender();

        int paramCount = plan.parameterCount();
        if (paramCount > MAX_PARAMS) {
            throw new DomainTransportRefusalException(
                    "SQL Server bind parameter limit exceeded. Max: " + MAX_PARAMS + ", Actual: " + paramCount);
        }

        List<String> fieldNames = new ArrayList<>();
        List<String> columnAliases = new ArrayList<>();
        for (DomainTransportField field : plan.getFields()) {
            columnAliases.add(field.getName());
            fieldNames.add(dialect.quoteIdentifier(field.getName()));
        }

        StringBuilder sql = new StringBuilder();
        StringBuilder body = new StringBuilder();
        List<Object> params = new ArrayList<>();
        sql.append(plan.getRelationName()).append("(").append(String.join(", ", fieldNames)).append(") AS (\n");
        for (int i = 0; i < plan.getTuples().size(); i++) {
            if (i > 0) {
                body.append("\nUNION ALL\n");
            }
            body.append("SELECT ");
            DomainTransportTuple tuple = plan.getTuples().get(i);
            List<String> columns = new ArrayList<>();
            for (int j = 0; j < tuple.getValues().size(); j++) {
                params.add(tuple.getValues().get(j));
                String expression = "CAST(? AS NVARCHAR(4000))";
                if (i == 0) {
                    expression += " AS " + fieldNames.get(j);
                }
                columns.add(expression);
            }
            body.append(String.join(", ", columns));
        }
        String cteBody = body.toString();
        sql.append("  ").append(cteBody.replace("\n", "\n  "));
        sql.append("\n)");

        List<String> joinParts = new ArrayList<>();
        for (String col : fieldNames) {
            joinParts.add("(_base." + col + " = _d." + col + " OR (_base." + col + " IS NULL AND _d." + col + " IS NULL))");
        }

        return DomainRelationRenderResult.builder()
                .sqlFragment(sql.toString())
                .cteAlias(plan.getRelationName())
                .cteColumnAliases(columnAliases)
                .cteBody(cteBody)
                .params(params)
                .joinPredicate(String.join(" AND ", joinParts))
                .placement(DomainTransportPlacement.CTE)
                .build();
    }
}
