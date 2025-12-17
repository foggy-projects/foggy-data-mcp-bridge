/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset.support;


import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.dataset.db.table.SqlColumn;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;


public final class SqlUtils {

    public static final String toInByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("(");

        for (String obj : ids) {
            sb.append("'").append(obj).append("'").append(",");
        }

        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(")");
        return sb.toString();
    }

//	public String toInFromFromEE(ExpEvaluator ee, String name) {
//
//		StringBuilder sb = new StringBuilder("(");
//
//		String[] ids = ee.getRequest().getParameters(name);
//		for (String obj : ids) {
//			// TODO 检查obj的合法性
//			sb.append("'").append(obj).append("'").append(",");
//		}
//
//		if (sb.charAt(sb.length() - 1) == ',') {
//			sb.deleteCharAt(sb.length() - 1);
//		}
//		sb.append(")");
//		return sb.toString();
//
//	}

    public static void doPsValue(Object v, SqlColumn c, PreparedStatement ps, int idx) throws SQLException {
        if (c.getJdbcType() == Types.OTHER) {
            if (v instanceof Map) {
                v = JsonUtils.toJson(v);
            }
        }
//		Object v = getBeanValue(ee, c);
//        if (c.getJdbcType() == Types.STRUCT && v != null) {
//            Geometry userObject = (Geometry) v;
//            if (userObject instanceof Point) {
//                // 减少x,y后面的小数点看看效果
//                userObject = GisHelper._toFixed((Point) userObject);
//            }
//            WKBWriter wkbWriter = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN);
//
//            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
//                // Write SRID
//                byte[] sridBytes = new byte[4];
//                ByteOrderValues.putInt(userObject.getSRID(), sridBytes, ByteOrderValues.LITTLE_ENDIAN);
//                outputStream.write(sridBytes);
//
//                wkbWriter.write(userObject, new OutputStreamOutStream(outputStream));
//                ps.setBytes(idx, outputStream.toByteArray());
//            } catch (IOException ioe) {
//                throw new IllegalArgumentException(ioe);
//            }
//            System.err.println(2356475);
//        } else {
        ps.setObject(idx, v);
//        }
    }
}
