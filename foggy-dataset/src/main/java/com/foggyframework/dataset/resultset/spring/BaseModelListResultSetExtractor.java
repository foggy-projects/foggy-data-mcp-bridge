package com.foggyframework.dataset.resultset.spring;

import com.foggyframework.dataset.model.DataSetModel;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.support.ListResultSetSupport;
import lombok.Data;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 把查询结果，转换为ListResultSet返回
 * 线程安全类
 */
@Data
public class BaseModelListResultSetExtractor implements ResultSetExtractor<ListResultSet> {

    DataSetModel model;

    public BaseModelListResultSetExtractor() {
    }

    public BaseModelListResultSetExtractor(DataSetModel model) {
        this.model = model;
    }

    @Override
    public ListResultSet extractData(ResultSet rs) throws SQLException, DataAccessException {

        ListResultSetMetaData meta = model.getListResultSetMetaData(rs);

        List<Record<?>> data = new ArrayList<Record<?>>();

        int l = rs.getMetaData().getColumnCount();

        l = Math.min(l, meta.getColumnCount());
        int cursor = 0;

        int start = 0;
        while (rs.next()) {

            Record rec = meta.newRecord(cursor + start + 1);
            for (int i = 0; i < l; i++) {
                rec.set(i + 1, rs.getObject(i + 1));
            }
            data.add(rec);
            // }
            cursor++;
        }
        ListResultSetSupport<?> listResultSet = new ListResultSetSupport(meta, data);

        return listResultSet;
    }

    public ListResultSet queryResultSet(QueryExpEvaluator ee){


       return (ListResultSet) model.query(ee,this);
    }

}
