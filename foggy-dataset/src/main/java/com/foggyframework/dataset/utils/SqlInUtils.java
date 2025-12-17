package com.foggyframework.dataset.utils;

public final class SqlInUtils {
    private static final String[] results = new String[100];

    static {
        // 初始化数组
        for (int i = 0; i < results.length; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j == 0) {
                    sb.append("?");
                } else {
                    sb.append(",?");
                }
            }
            results[i] = "(" + sb + ")";
        }
    }

    public static String getSqlInExpress(int i) {
        if (i >= 0 && i < results.length) {
            return results[i];
        } else {
            StringBuilder sb = new StringBuilder("(");
            for (int j = 0; j < i; j++) {
                if (j == 0) {
                    sb.append("?");
                } else {
                    sb.append(",?");
                }
            }
            return sb.append(")").toString();
        }
    }
}
