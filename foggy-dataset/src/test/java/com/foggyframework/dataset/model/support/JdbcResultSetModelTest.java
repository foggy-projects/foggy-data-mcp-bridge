package com.foggyframework.dataset.model.support;

import com.foggyframework.dataset.DatasetTestSupport;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.ObjectExp;
import com.foggyframework.fsscript.exp.StringExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;


public class JdbcResultSetModelTest extends DatasetTestSupport {


    /**
     * 简单的测试
     */
    @Test
    public void getSql() {
        JdbcDataSetModel model = _build();

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        QueryExpEvaluator queryExpEvaluator = new QueryExpEvaluator(ee);

        SQLKey sqlKey =   model.getSql(queryExpEvaluator);

        Assert.assertNotNull(sqlKey);
    }

    @Test
    public void testQueryListResultSet(){
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        QueryExpEvaluator queryExpEvaluator = new QueryExpEvaluator(ee);

//        QueryExpEvaluator.
    }


    private JdbcDataSetModel _build(){
        JdbcDataSetModel model = new JdbcDataSetModel();

        model.addSQL(new StringExp("select * from M_MAP_TEST"),null,new ObjectExp<DataSource>(dataSource));


        return model;
    }
}