/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset.support;


import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.PagingResultSet;
import com.foggyframework.dataset.resultset.Record;
import lombok.Data;

import java.sql.SQLException;
import java.util.List;

/**
 * 采用组合的方式替代
 *
 * @author seasoul
 * @since foggy-1.0
 */
@Data
public class ListPagingResultSet extends ListResultSetSupport implements PagingResultSet, ListResultSet {

    int limit;

    int start;

    long total;

    @Override
    public List getItems() {
        return data;
    }

    public void setItems(List items) {
        data = items;
    }

    public ListPagingResultSet(ListResultSetMetaData meta, List data, long total, int start, int limit) {
        super(meta, data);
        this.total = total;
        this.start = start;
        this.limit = limit;
    }

    // @Override
    // public Boolean fireEvent(EventObject eventObject) {
    // if (eventObject instanceof ResultSetChangeEvent) {
    // if (((ResultSetChangeEvent) eventObject).getType() == Type.INSERT) {
    // total++;
    // } else if (((ResultSetChangeEvent) eventObject).getType() == Type.DELETE)
    // {
    // total--;
    // }
    // }
    // return super.fireEvent(eventObject);
    // }

    @Override
    public void deleteRow() throws SQLException {
        super.deleteRow();
        total--;
    }


    @Override
    public boolean hasNextPage() {
        return total > (start + limit);
    }

    @Override
    public boolean hasPreviousPage() {
        return start > 0;
    }

    @Override
    public Record insertRecord(Record rec, int pos) throws SQLException {
        total++;
        return super.insertRecord(rec, pos);
    }

}
