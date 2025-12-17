/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset.support;


import com.foggyframework.core.AbstractDecorate;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

public class ResultSetSupport extends AbstractDecorate implements ResultSet {
	private static final Object[] EMPTY = new Object[] {};

	@Override
	public boolean absolute(int row) throws SQLException {

		return false;
	}

	@Override
	public void afterLast() throws SQLException {

	}

	@Override
	public void beforeFirst() throws SQLException {

	}

	@Override
	public void cancelRowUpdates() throws SQLException {

	}

	@Override
	public void clearWarnings() throws SQLException {

	}

	@Override
	public void close() throws SQLException {

	}

	@Override
	public void deleteRow() throws SQLException {

	}

	@Override
	public int findColumn(String columnName) throws SQLException {

		return 0;
	}

	@Override
	public boolean first() throws SQLException {

		return false;
	}

	@Override
	public Array getArray(int i) throws SQLException {

		return null;
	}

	@Override
	public Array getArray(String colName) throws SQLException {

		return null;
	}

	@Override
	public InputStream getAsciiStream(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public InputStream getAsciiStream(String columnName) throws SQLException {

		return null;
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {

		return null;
	}

	@Override
	public BigDecimal getBigDecimal(String columnName) throws SQLException {

		return null;
	}

	@Override
	public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException {

		return null;
	}

	@Override
	public InputStream getBinaryStream(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public InputStream getBinaryStream(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Blob getBlob(int i) throws SQLException {

		return null;
	}

	@Override
	public Blob getBlob(String colName) throws SQLException {

		return null;
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {

		return (Boolean) getObject(columnIndex);
	}

	@Override
	public boolean getBoolean(String columnName) throws SQLException {

		return false;
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {

		return 0;
	}

	@Override
	public byte getByte(String columnName) throws SQLException {

		return 0;
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public byte[] getBytes(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Reader getCharacterStream(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public Reader getCharacterStream(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Clob getClob(int i) throws SQLException {

		return null;
	}

	@Override
	public Clob getClob(String colName) throws SQLException {

		return null;
	}

	@Override
	public int getConcurrency() throws SQLException {

		return 0;
	}

	@Override
	public String getCursorName() throws SQLException {

		return null;
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public Date getDate(int columnIndex, Calendar cal) throws SQLException {

		return null;
	}

	@Override
	public Date getDate(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Date getDate(String columnName, Calendar cal) throws SQLException {

		return null;
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		return ((Number) getObject(columnIndex)).doubleValue();
	}

	@Override
	public double getDouble(String columnName) throws SQLException {

		return 0;
	}

	@Override
	public int getFetchDirection() throws SQLException {

		return 0;
	}

	@Override
	public int getFetchSize() throws SQLException {

		return 0;
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {

		return 0;
	}

	@Override
	public float getFloat(String columnName) throws SQLException {

		return 0;
	}

	@Override
	public int getHoldability() throws SQLException {

		return 0;
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {

		return 0;
	}

	@Override
	public int getInt(String columnName) throws SQLException {

		return 0;
	}

	public Object[] getListeners() {
		return EMPTY;
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {

		return 0;
	}

	@Override
	public long getLong(String columnName) throws SQLException {

		return 0;
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {

		return null;
	}

	@Override
	public Reader getNCharacterStream(int arg0) throws SQLException {

		return null;
	}

	@Override
	public Reader getNCharacterStream(String arg0) throws SQLException {

		return null;
	}

	@Override
	public NClob getNClob(int arg0) throws SQLException {

		return null;
	}

	@Override
	public NClob getNClob(String arg0) throws SQLException {

		return null;
	}

	@Override
	public String getNString(int arg0) throws SQLException {

		return null;
	}

	@Override
	public String getNString(String arg0) throws SQLException {

		return null;
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getObject(int i, Map<String, Class<?>> map) throws SQLException {

		return null;
	}

	@Override
	public Object getObject(String columnName) throws SQLException {

		return null;
	}

	@Override
	public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getObject(String colName, Map<String, Class<?>> map) throws SQLException {

		return null;
	}

	@Override
	public Ref getRef(int i) throws SQLException {

		return null;
	}

	@Override
	public Ref getRef(String colName) throws SQLException {

		return null;
	}

	@Override
	public int getRow() throws SQLException {

		return 0;
	}

	@Override
	public RowId getRowId(int arg0) throws SQLException {

		return null;
	}

	@Override
	public RowId getRowId(String arg0) throws SQLException {

		return null;
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {

		return 0;
	}

	@Override
	public short getShort(String columnName) throws SQLException {

		return 0;
	}

	@Override
	public SQLXML getSQLXML(int arg0) throws SQLException {

		return null;
	}

	@Override
	public SQLXML getSQLXML(String arg0) throws SQLException {

		return null;
	}

	@Override
	public Statement getStatement() throws SQLException {

		return null;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		return (String) getObject(columnIndex);
	}

	@Override
	public String getString(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public Time getTime(int columnIndex, Calendar cal) throws SQLException {

		return null;
	}

	@Override
	public Time getTime(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Time getTime(String columnName, Calendar cal) throws SQLException {

		return null;
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {

		return null;
	}

	@Override
	public Timestamp getTimestamp(String columnName) throws SQLException {

		return null;
	}

	@Override
	public Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException {

		return null;
	}

	@Override
	public int getType() throws SQLException {

		return 0;
	}

	@Override
	public InputStream getUnicodeStream(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public InputStream getUnicodeStream(String columnName) throws SQLException {

		return null;
	}

	@Override
	public URL getURL(int columnIndex) throws SQLException {

		return null;
	}

	@Override
	public URL getURL(String columnName) throws SQLException {

		return null;
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {

		return null;
	}

	@Override
	public void insertRow() throws SQLException {

	}

	@Override
	public boolean isAfterLast() throws SQLException {

		return false;
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {

		return false;
	}

	@Override
	public boolean isClosed() throws SQLException {

		return false;
	}

	@Override
	public boolean isFirst() throws SQLException {

		return false;
	}

	@Override
	public boolean isLast() throws SQLException {

		return false;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {

		return false;
	}

	@Override
	public boolean last() throws SQLException {

		return false;
	}

	@Override
	public void moveToCurrentRow() throws SQLException {

	}

	@Override
	public void moveToInsertRow() throws SQLException {

	}

	@Override
	public boolean next() throws SQLException {

		return false;
	}

	@Override
	public boolean previous() throws SQLException {

		return false;
	}

	@Override
	public void refreshRow() throws SQLException {

	}

	@Override
	public boolean relative(int rows) throws SQLException {

		return false;
	}

	@Override
	public boolean rowDeleted() throws SQLException {

		return false;
	}

	@Override
	public boolean rowInserted() throws SQLException {

		return false;
	}

	@Override
	public boolean rowUpdated() throws SQLException {

		return false;
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {

	}

	@Override
	public void setFetchSize(int rows) throws SQLException {

	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {

		return null;
	}

	@Override
	public void updateArray(int columnIndex, Array x) throws SQLException {

	}

	@Override
	public void updateArray(String columnName, Array x) throws SQLException {

	}

	@Override
	public void updateAsciiStream(int arg0, InputStream arg1) throws SQLException {

	}

	@Override
	public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {

	}

	@Override
	public void updateAsciiStream(int arg0, InputStream arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateAsciiStream(String arg0, InputStream arg1) throws SQLException {

	}

	@Override
	public void updateAsciiStream(String columnName, InputStream x, int length) throws SQLException {

	}

	@Override
	public void updateAsciiStream(String arg0, InputStream arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {

	}

	@Override
	public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException {

	}

	@Override
	public void updateBinaryStream(int arg0, InputStream arg1) throws SQLException {

	}

	@Override
	public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {

	}

	@Override
	public void updateBinaryStream(int arg0, InputStream arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateBinaryStream(String arg0, InputStream arg1) throws SQLException {

	}

	@Override
	public void updateBinaryStream(String columnName, InputStream x, int length) throws SQLException {

	}

	@Override
	public void updateBinaryStream(String arg0, InputStream arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateBlob(int columnIndex, Blob x) throws SQLException {

	}

	@Override
	public void updateBlob(int arg0, InputStream arg1) throws SQLException {

	}

	@Override
	public void updateBlob(int arg0, InputStream arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateBlob(String columnName, Blob x) throws SQLException {

	}

	@Override
	public void updateBlob(String arg0, InputStream arg1) throws SQLException {

	}

	@Override
	public void updateBlob(String arg0, InputStream arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateBoolean(int columnIndex, boolean x) throws SQLException {

	}

	@Override
	public void updateBoolean(String columnName, boolean x) throws SQLException {

	}

	@Override
	public void updateByte(int columnIndex, byte x) throws SQLException {

	}

	@Override
	public void updateByte(String columnName, byte x) throws SQLException {

	}

	@Override
	public void updateBytes(int columnIndex, byte[] x) throws SQLException {

	}

	@Override
	public void updateBytes(String columnName, byte[] x) throws SQLException {

	}

	@Override
	public void updateCharacterStream(int arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {

	}

	@Override
	public void updateCharacterStream(int arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateCharacterStream(String arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateCharacterStream(String columnName, Reader reader, int length) throws SQLException {

	}

	@Override
	public void updateCharacterStream(String arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateClob(int columnIndex, Clob x) throws SQLException {

	}

	@Override
	public void updateClob(int arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateClob(int arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateClob(String columnName, Clob x) throws SQLException {

	}

	@Override
	public void updateClob(String arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateClob(String arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateDate(int columnIndex, Date x) throws SQLException {

	}

	@Override
	public void updateDate(String columnName, Date x) throws SQLException {

	}

	@Override
	public void updateDouble(int columnIndex, double x) throws SQLException {

	}

	@Override
	public void updateDouble(String columnName, double x) throws SQLException {

	}

	@Override
	public void updateFloat(int columnIndex, float x) throws SQLException {

	}

	@Override
	public void updateFloat(String columnName, float x) throws SQLException {

	}

	@Override
	public void updateInt(int columnIndex, int x) throws SQLException {

	}

	@Override
	public void updateInt(String columnName, int x) throws SQLException {

	}

	@Override
	public void updateLong(int columnIndex, long x) throws SQLException {

	}

	@Override
	public void updateLong(String columnName, long x) throws SQLException {

	}

	@Override
	public void updateNCharacterStream(int arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateNCharacterStream(int arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateNCharacterStream(String arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateNCharacterStream(String arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateNClob(int arg0, NClob arg1) throws SQLException {

	}

	@Override
	public void updateNClob(int arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateNClob(int arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateNClob(String arg0, NClob arg1) throws SQLException {

	}

	@Override
	public void updateNClob(String arg0, Reader arg1) throws SQLException {

	}

	@Override
	public void updateNClob(String arg0, Reader arg1, long arg2) throws SQLException {

	}

	@Override
	public void updateNString(int arg0, String arg1) throws SQLException {

	}

	@Override
	public void updateNString(String arg0, String arg1) throws SQLException {

	}

	@Override
	public void updateNull(int columnIndex) throws SQLException {

	}

	@Override
	public void updateNull(String columnName) throws SQLException {

	}

	@Override
	public void updateObject(int columnIndex, Object x) throws SQLException {

	}

	@Override
	public void updateObject(int columnIndex, Object x, int scale) throws SQLException {

	}

	@Override
	public void updateObject(String columnName, Object x) throws SQLException {

	}

	@Override
	public void updateObject(String columnName, Object x, int scale) throws SQLException {

	}

	@Override
	public void updateRef(int columnIndex, Ref x) throws SQLException {

	}

	@Override
	public void updateRef(String columnName, Ref x) throws SQLException {

	}

	@Override
	public void updateRow() throws SQLException {

	}

	@Override
	public void updateRowId(int arg0, RowId arg1) throws SQLException {

	}

	@Override
	public void updateRowId(String arg0, RowId arg1) throws SQLException {

	}

	@Override
	public void updateShort(int columnIndex, short x) throws SQLException {

	}

	@Override
	public void updateShort(String columnName, short x) throws SQLException {

	}

	@Override
	public void updateSQLXML(int arg0, SQLXML arg1) throws SQLException {

	}

	@Override
	public void updateSQLXML(String arg0, SQLXML arg1) throws SQLException {

	}

	@Override
	public void updateString(int columnIndex, String x) throws SQLException {

	}

	@Override
	public void updateString(String columnName, String x) throws SQLException {

	}

	@Override
	public void updateTime(int columnIndex, Time x) throws SQLException {

	}

	@Override
	public void updateTime(String columnName, Time x) throws SQLException {

	}

	@Override
	public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {

	}

	@Override
	public void updateTimestamp(String columnName, Timestamp x) throws SQLException {

	}

	@Override
	public boolean wasNull() throws SQLException {

		return false;
	}

}
