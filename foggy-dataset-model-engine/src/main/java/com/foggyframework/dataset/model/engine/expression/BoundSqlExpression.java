package com.foggyframework.dataset.model.engine.expression;

import java.util.List;

/** SQL expression and the bind values owned by that expression. */
public record BoundSqlExpression(String sql, List<Object> values) {

    public BoundSqlExpression {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static BoundSqlExpression of(String sql) {
        return new BoundSqlExpression(sql, List.of());
    }

    /**
     * Returns whether every JDBC placeholder owned by this expression has a value.
     * Question marks inside quoted SQL literals/identifiers and comments are ignored.
     */
    public boolean hasCompleteBindings() {
        return countJdbcPlaceholders(sql) == values.size();
    }

    static int countJdbcPlaceholders(String sql) {
        if (sql == null || sql.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean backtickQuoted = false;
        boolean bracketQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (singleQuoted) {
                if (current == '\\' && next != '\0') {
                    i++;
                } else if (current == '\'' && next == '\'') {
                    i++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                if (current == '"' && next == '"') {
                    i++;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (backtickQuoted) {
                if (current == '`' && next == '`') {
                    i++;
                } else if (current == '`') {
                    backtickQuoted = false;
                }
                continue;
            }
            if (bracketQuoted) {
                if (current == ']' && next == ']') {
                    i++;
                } else if (current == ']') {
                    bracketQuoted = false;
                }
                continue;
            }

            if (current == '-' && next == '-') {
                lineComment = true;
                i++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (current == '\'') {
                singleQuoted = true;
            } else if (current == '"') {
                doubleQuoted = true;
            } else if (current == '`') {
                backtickQuoted = true;
            } else if (current == '[') {
                bracketQuoted = true;
            } else if (current == '?') {
                count++;
            }
        }
        return count;
    }
}
