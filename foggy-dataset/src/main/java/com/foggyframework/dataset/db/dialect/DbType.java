/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.db.dialect;

/**
 * 数据库类型枚举
 */
public enum DbType {
    MYSQL(1),
    POSTGRESQL(2),
    SQLSERVER(3),
    SQLITE(4),
    ORACLE(5);  // 预留

    private final int code;

    DbType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 根据 code 获取枚举值
     * @param code 数据库类型代码
     * @return 对应的枚举值，未找到返回 null
     */
    public static DbType fromCode(int code) {
        for (DbType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
