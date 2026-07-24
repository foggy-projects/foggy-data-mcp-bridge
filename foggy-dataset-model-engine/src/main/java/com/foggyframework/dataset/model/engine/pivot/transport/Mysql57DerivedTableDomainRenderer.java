package com.foggyframework.dataset.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;
import java.util.ArrayList;
import java.util.List;

public class Mysql57DerivedTableDomainRenderer implements DomainRelationRenderer {

    private static final int MAX_TUPLES = 2000;
    private static final int MAX_PARAMS = 10000;
    private static final int MAX_SQL_LENGTH = 1024 * 1024; // 1MB

    @Override
    public DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan) throws DomainTransportRefusalException {
        plan.validateForRender();

        int tupleCount = plan.getTuples().size();
        if (tupleCount > MAX_TUPLES) {
            throw new DomainTransportRefusalException("MySQL 5.7 derived table tuple limit exceeded. Max: " + MAX_TUPLES + ", Actual: " + tupleCount);
        }

        int paramCount = plan.parameterCount();
        if (paramCount > MAX_PARAMS) {
            throw new DomainTransportRefusalException("MySQL 5.7 bind parameter limit exceeded. Max: " + MAX_PARAMS + ", Actual: " + paramCount);
        }

        StringBuilder sql = new StringBuilder();
        sql.append("(\n");
        List<Object> params = new ArrayList<>();

        List<String> fieldNames = new ArrayList<>();
        for (DomainTransportField field : plan.getFields()) {
            fieldNames.add(dialect.quoteIdentifier(field.getName()));
        }

        for (int i = 0; i < tupleCount; i++) {
            if (i > 0) {
                sql.append("\n  UNION ALL\n");
            }
            sql.append("  SELECT ");
            DomainTransportTuple tuple = plan.getTuples().get(i);
            List<String> cols = new ArrayList<>();
            for (int j = 0; j < tuple.getValues().size(); j++) {
                Object val = tuple.getValues().get(j);
                params.add(val);
                String placeholder = "?";
                if (i == 0) {
                    placeholder += " AS " + fieldNames.get(j);
                }
                cols.add(placeholder);
            }
            sql.append(String.join(", ", cols));

            if (sql.length() > MAX_SQL_LENGTH) {
                throw new DomainTransportRefusalException("MySQL 5.7 derived table SQL length limit exceeded. Max: " + MAX_SQL_LENGTH);
            }
        }
        sql.append("\n)");

        StringBuilder joinPredicate = new StringBuilder();
        List<String> joinParts = new ArrayList<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String col = fieldNames.get(i);
            joinParts.add("_base." + col + " <=> _d." + col);
        }
        joinPredicate.append(String.join(" AND ", joinParts));

        return DomainRelationRenderResult.builder()
                .sqlFragment(sql.toString())
                .params(params)
                .joinPredicate(joinPredicate.toString())
                .placement(DomainTransportPlacement.DERIVED_TABLE)
                .build();
    }
}
