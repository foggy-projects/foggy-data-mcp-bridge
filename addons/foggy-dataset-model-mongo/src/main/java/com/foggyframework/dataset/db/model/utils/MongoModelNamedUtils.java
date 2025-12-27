package com.foggyframework.dataset.db.model.utils;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.data.mongodb.core.aggregation.Field;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Collections;

/**
 * MongoDB 相关的命名工具类
 * 从 JdbcModelNamedUtils 中分离出来
 */
public class MongoModelNamedUtils {

    public static String criteriaToString(Criteria criteria) {
        StringBuilder sb = new StringBuilder();
        recursiveCriteriaToString(criteria, sb, 0);
        return sb.toString();
    }

    private static void recursiveCriteriaToString(Criteria criteria, StringBuilder sb, int depth) {
        String indentation = String.join("", Collections.nCopies(depth * 2, " "));
        sb.append(indentation).append("{\n");

        sb.append(indentation).append("  Criteria: {\n");
        for (String key : criteria.getCriteriaObject().keySet()) {
            Object value = criteria.getCriteriaObject().get(key);
            if (value instanceof Criteria) {
                sb.append(indentation).append("    ").append(key).append(": ");
                recursiveCriteriaToString((Criteria) value, sb, depth + 1);
            } else {
                sb.append(indentation).append("    ").append(key).append(": ").append(value).append("\n");
            }
        }
        sb.append(indentation).append("  }\n");

        if (!criteria.getCriteriaObject().isEmpty()) {
            // This part is for handling additional conditions not covered by the simple key-value pair.
            // Depending on your use case, you might want to handle these differently or ignore them.
            sb.append(indentation).append("  Additional Conditions: ").append(criteria.getCriteriaObject()).append("\n");
        }

        sb.append(indentation).append("}");
        if (depth > 0) {
            sb.append("\n");
        }
    }

    public static String projectionOperationToString(ProjectionOperation operation) {
        StringBuilder sb = new StringBuilder();
        sb.append("ProjectionOperation {\n");

        AggregationOperationContext context = null; // 实际使用时，你需要提供一个AggregationOperationContext实例，通常在执行聚合操作的上下文中可用

        try {
            for (Field field : operation.getFields()) {
                sb.append("\"").append(field.getTarget()).append("\":\"").append(field.getName()).append("\",\n");
            }
            sb.delete(sb.length() - 2, sb.length());
        } catch (Exception e) {
            sb.append("Error converting to string: ").append(e.getMessage());
        }

        sb.append("}\n");
        return sb.toString();
    }

    public static String formatSort(Sort sort) {
        StringBuilder sb = new StringBuilder();
        sort.stream().forEach(order -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(order.getProperty())
                    .append(" ")
                    .append(order.getDirection().name().toLowerCase());
        });
        return sb.toString();
    }

}
