/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.model;

import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;

/**
 * </pre>
 *
 * @author Foggy
 * @since foggy-1.0
 */
public interface DataSetModel extends KpiModel{



    <T> T query(QueryExpEvaluator ee, ResultSetExtractor<T> extractor);

    Object queryWithTotal(QueryExpEvaluator ee, TotalCountSetExtractor extractor);

    ListResultSetMetaData getListResultSetMetaData(ResultSet rs);

    FsscriptClosureDefinition getClosureDefinition();

}
