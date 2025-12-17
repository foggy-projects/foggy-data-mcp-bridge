package com.foggyframework.dataset.resultset.spring;

import com.foggyframework.dataset.DatasetTestSupport;
import com.foggyframework.dataset.model.support.JdbcDataSetModel;
import com.foggyframework.fsscript.exp.ObjectExp;
import com.foggyframework.fsscript.exp.StringExp;
import org.junit.Test;

import javax.sql.DataSource;

public class BaseModelListResultSetExtractorTest extends DatasetTestSupport {

    /**
     * TODO M_MAP_TEST 表丢失，暂时不测试
     */
    @Test
    public void queryResultSet() {

//        BaseModelListResultSetExtractor extractor = new BaseModelListResultSetExtractor(_build());
//
//        QueryExpEvaluator queryExpEvaluator = QueryExpEvaluator.newInstance(appCtx);
//        ListResultSet listResultSet = extractor.queryResultSet(queryExpEvaluator);
//
//        Assert.assertTrue(!listResultSet.isEmpty());


    }

    private JdbcDataSetModel _build() {
        JdbcDataSetModel model = new JdbcDataSetModel();

        model.addSQL(new StringExp("select * from M_MAP_TEST"), null, new ObjectExp<DataSource>(dataSource));


        return model;
    }
}