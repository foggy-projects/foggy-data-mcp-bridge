package com.foggyframework.dataset.db.model.impl;

import com.foggyframework.core.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormulaSqlSupport {

    private static final Pattern ALIAS_DOT = Pattern.compile("(?<![A-Za-z0-9_])alias\\.");

    private FormulaSqlSupport() {
    }

    public static boolean hasSql(String formulaSql) {
        return StringUtils.isNotEmpty(formulaSql);
    }

    public static String applyAlias(String formulaSql, String alias) {
        if (StringUtils.isEmpty(formulaSql)) {
            return formulaSql;
        }
        if (StringUtils.isEmpty(alias)) {
            return formulaSql;
        }
        String result = formulaSql
                .replace("${alias}", alias)
                .replace("{alias}", alias);
        return ALIAS_DOT.matcher(result).replaceAll(Matcher.quoteReplacement(alias + "."));
    }
}
