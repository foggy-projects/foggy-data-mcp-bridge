/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.Record;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListResultSetMetaDataSupport<T> extends ResultSetMetaDataSupport
		implements ResultSetMetaData, ListResultSetMetaData<T> {

	public static void main(String[] args)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {

		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(true); // never forget this!
		DocumentBuilder builder = domFactory.newDocumentBuilder();
		Document doc = builder.parse("books.xml");

		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath.compile("/news/rows");

		Object rows = expr.evaluate(doc.getFirstChild(), XPathConstants.NODE);

		XPathExpression xxx = xpath.compile("name(..)");

		System.out.println(xxx.evaluate(rows, XPathConstants.STRING));

	}

	private List<String> columnNames = new ArrayList<String>();

	private Map<String, Integer> columnName2Index = new HashMap<String, Integer>();

	public ListResultSetMetaDataSupport() {
		super();
	}

	public ListResultSetMetaDataSupport(List<String> columnNames) {
		this.columnNames = columnNames;
		reset();
	}

	public ListResultSetMetaDataSupport(ResultSetMetaData resultSetMetaData) {
		super();
		// this.beanInfoHelper = beanInfoHelper;
		if (resultSetMetaData != null) {
			try {
				int size = resultSetMetaData.getColumnCount();
				columnNames = new ArrayList<String>(size);
				for (int i = 1; i <= size; i++) {
					String name = resultSetMetaData.getColumnLabel(i);
					if (StringUtils.isEmpty(name)) {
						name = resultSetMetaData.getColumnName(i);
					}
					addColumnName(name);
				}
				// reset();
			} catch (SQLException e) {
				throw RX.throwB(e);
			}
		}
	}

	public void addColumnName(String name) {
		name = name.toUpperCase();
		columnNames.add(name);
		columnName2Index.put(name, columnNames.size());
	}

	// public ListResultSetMetaDataSupport(List<String> columnNames,
	// BeanInfoHelper beanInfoHelper) {
	// this.columnNames = columnNames;
	//
	// }

	@Override
	public T apply(Record<T> delegate, T result) {
		try {
			delegate.apply(result);
		} catch (SQLException e) {
			throw RX.throwB(e);
		}
		return result;
	}

	@Override
	public T apply(T v, Record<T> record) {
		throw new UnsupportedOperationException();
	}

//	@Override
//	public BeanInfoHelper getBeanInfoHelper() {
//		return null;
//	}

	@Override
	public int getColumnCount() throws SQLException {
		return columnNames.size();
	}

	@Override
	public int getColumnIndex(String columnName) {

		Integer x = columnName2Index.get(columnName.toUpperCase());
		// if(x==-1){
		// throw new RuntimeException("column named ["+columnName
		// +"] not found!");
		// }
		return x == null ? 0 : x;
	}

	@Override
	public String getColumnName(int column) throws SQLException {
		try {
			return columnNames.get(column - 1);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw e;
		}
	}

	@Override
	public List<String> getColumnNames() {
		return columnNames;
	}

	@Override
	public Record<T> newRecord(int id) throws SQLException {
		return new ArrayRecord<T>(new Object[getColumnCount()], this, id);
	}

	protected void reset() {
		columnName2Index.clear();
		for (int i = 1; i <= columnNames.size(); i++) {
			columnName2Index.put(columnNames.get(i - 1).toUpperCase(), i);
		}
	}

	public void setColumnNames(List<String> columnNames) {
		this.columnNames = columnNames;
		reset();
	}

}
