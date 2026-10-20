package com.foggyframework.dataset.model.engine.compose.compilation;

import java.util.Map;
import java.util.regex.Pattern;

/** Scope generated relation identifiers while preserving SQL literals and parameter order. */
final class CteAliasScope {
    private static final Pattern TOKEN = Pattern.compile(
            "'(?:(?:'')|[^'])*'|--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|\"(?:\"\"|[^\"])*\"|`(?:``|[^`])*`|\\[(?:\\]\\]|[^\\]])*\\]|[A-Za-z_][A-Za-z_0-9$]*");
    private CteAliasScope() {}
    static String rewrite(String sql, Map<String, String> names) {
        var matcher = TOKEN.matcher(sql);
        StringBuilder result = new StringBuilder();
        int end = 0;
        String previous = "";
        while (matcher.find()) {
            String token = matcher.group();
            result.append(sql, end, matcher.start());
            String replacement = token;
            if (!token.startsWith("'") && !token.startsWith("--") && !token.startsWith("/*")) {
                boolean quoted = token.startsWith("\"") || token.startsWith("`") || token.startsWith("[");
                String name = quoted ? token.substring(1, token.length() - 1) : token;
                int following = matcher.end();
                while (following < sql.length() && Character.isWhitespace(sql.charAt(following))) following++;
                if (names.containsKey(name) && ("FROM".equalsIgnoreCase(previous) || "JOIN".equalsIgnoreCase(previous)
                        || following < sql.length() && sql.charAt(following) == '.')) {
                    replacement = quoted ? token.charAt(0) + names.get(name) + token.charAt(token.length() - 1) : names.get(name);
                }
                previous = name;
            }
            result.append(replacement);
            end = matcher.end();
        }
        return result.append(sql, end, sql.length()).toString();
    }
}
