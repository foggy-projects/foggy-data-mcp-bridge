/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset.support;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class ResultSetMetaDataSupport implements ResultSetMetaData {

	@Override
	public String getCatalogName(int column) throws SQLException {

		return null;
	}

	@Override
	public String getColumnClassName(int column) throws SQLException {

		return null;
	}

	@Override
	public int getColumnCount() throws SQLException {

		return 0;
	}

	@Override
	public int getColumnDisplaySize(int column) throws SQLException {

		return 0;
	}

	@Override
	public String getColumnLabel(int column) throws SQLException {

		return null;
	}

	@Override
	public String getColumnName(int column) throws SQLException {

		return null;
	}

	@Override
	public int getColumnType(int column) throws SQLException {

		return 0;
	}

	@Override
	public String getColumnTypeName(int column) throws SQLException {

		return null;
	}

	@Override
	public int getPrecision(int column) throws SQLException {

		return 0;
	}

	@Override
	public int getScale(int column) throws SQLException {

		return 0;
	}

	@Override
	public String getSchemaName(int column) throws SQLException {

		return null;
	}

	@Override
	public String getTableName(int column) throws SQLException {

		return null;
	}

	@Override
	public boolean isAutoIncrement(int column) throws SQLException {

		return false;
	}

	@Override
	public boolean isCaseSensitive(int column) throws SQLException {

		return false;
	}

	@Override
	public boolean isCurrency(int column) throws SQLException {

		return false;
	}

	@Override
	public boolean isDefinitelyWritable(int column) throws SQLException {

		return false;
	}

	@Override
	public int isNullable(int column) throws SQLException {

		return 0;
	}

	@Override
	public boolean isReadOnly(int column) throws SQLException {

		return false;
	}

	@Override
	public boolean isSearchable(int column) throws SQLException {

		return false;
	}

	@Override
	public boolean isSigned(int column) throws SQLException {

		return false;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {

		return false;
	}

	@Override
	public boolean isWritable(int column) throws SQLException {

		return false;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {

		return null;
	}

}
