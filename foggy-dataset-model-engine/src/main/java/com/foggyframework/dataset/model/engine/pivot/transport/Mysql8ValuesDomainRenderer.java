package com.foggyframework.dataset.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;
import java.util.ArrayList;
import java.util.List;

public class Mysql8ValuesDomainRenderer implements DomainRelationRenderer {

    // Assumed version strings start with standard semantic versions e.g., "8.0.18", "8.0.19"
    private boolean isVersionSupported(String productVersion) {
        if (productVersion == null || productVersion.isEmpty()) {
            return false;
        }
        try {
            String[] parts = productVersion.split("\\.");
            if (parts.length >= 3) {
                int major = parseLeadingInt(parts[0]);
                int minor = parseLeadingInt(parts[1]);
                int patch = parseLeadingInt(parts[2]);
                if (major < 0 || minor < 0 || patch < 0) {
                    return false;
                }

                if (major > 8) return true;
                if (major == 8 && minor > 0) return true;
                if (major == 8 && minor == 0 && patch >= 19) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private int parseLeadingInt(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return -1;
        }
        return Integer.parseInt(part.substring(0, end));
    }

    @Override
    public DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan) throws DomainTransportRefusalException {
        if (!isVersionSupported(databaseVersion)) {
            throw new DomainTransportRefusalException("MySQL VALUES ROW(...) syntax requires version 8.0.19+, found: " + databaseVersion);
        }

        plan.validateForRender();

        int paramCount = plan.parameterCount();
        if (paramCount > 60000) {
            throw new DomainTransportRefusalException("MySQL bind parameter limit exceeded: " + paramCount);
        }

        StringBuilder sql = new StringBuilder();
        sql.append(plan.getRelationName()).append("(");
        List<String> fieldNames = new ArrayList<>();
        for (DomainTransportField field : plan.getFields()) {
            fieldNames.add(dialect.quoteIdentifier(field.getName()));
        }
        sql.append(String.join(", ", fieldNames));
        sql.append(") AS (\n  VALUES ");

        List<Object> params = new ArrayList<>();
        List<String> tupleStrings = new ArrayList<>();
        for (DomainTransportTuple tuple : plan.getTuples()) {
            List<String> placeholders = new ArrayList<>();
            for (Object val : tuple.getValues()) {
                placeholders.add("?");
                params.add(val);
            }
            tupleStrings.add("ROW(" + String.join(", ", placeholders) + ")");
        }
        sql.append(String.join(",\n         ", tupleStrings));
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
                .placement(DomainTransportPlacement.CTE)
                .build();
    }
}
