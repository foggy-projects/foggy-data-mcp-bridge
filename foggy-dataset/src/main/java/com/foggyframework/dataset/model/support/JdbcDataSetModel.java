package com.foggyframework.dataset.model.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.DataSetModel;
import com.foggyframework.dataset.model.QueryConfigFormat;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.dataset.model.TotalCountSetExtractor;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.spring.JavaColumnNameFixRowMapper;
import com.foggyframework.dataset.resultset.spring.ListRowMapper;
import com.foggyframework.dataset.resultset.support.ListResultSetMetaDataSupport;
import com.foggyframework.dataset.utils.DatasetTemplate;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

@Slf4j
@Getter
@Setter
public class JdbcDataSetModel extends ResultSetModelSupport implements DataSetModel {
    public static final ColumnMapRowMapper DEFAULT_ROW_MAPPER = new ColumnMapRowMapper();

    private List<SQL> sqls;

    ListResultSetMetaData<?> metaData;

    FsscriptClosureDefinition closureDefinition;

    @Override
    public QueryExpEvaluator newQueryExpEvaluator(ApplicationContext appCtx) {
        return new QueryExpEvaluator(DefaultExpEvaluator.newInstance(appCtx, closureDefinition.newFoggyClosure()));
    }

    @Override
    public <R> R query(QueryExpEvaluator ee, ResultSetExtractor<R> extractor) {
        return query(ee, extractor, null);
    }

    private <R> R query(QueryExpEvaluator ee, ResultSetExtractor<R> extractor, TotalCountSetExtractor totalCountSetExtractor) {
        SQLKey sqlKey = getSql(ee);
        if (log.isDebugEnabled()) {
            log.debug(String.format("查询数据集 【%s】", name) + sqlKey.sql + "args:" + Arrays.toString(sqlKey.args));
        }
        DatasetTemplate template = sqlKey.getDatasetTemplate(ee);

        String sql = sqlKey.sql;
        if (sqlKey.limit <= 0) {
            if (log.isDebugEnabled()) {
                log.debug("query sql " + sqlKey + "args:" + Arrays.toString(sqlKey.args));
            }

        } else {
            // 分页
            sql = generatePagingSql(sqlKey, template);
        }
        R obj = template.getTemplate().query(sql, sqlKey.args, extractor);
        if (ee.isReturnTotal() && totalCountSetExtractor != null) {
            String totalSql = generateTotalSql(sqlKey.sql);

            Integer total = template.getTemplate().queryForObject(totalSql, sqlKey.args, Integer.class);

            return (R) totalCountSetExtractor.extractTotal(obj, sqlKey.start, sqlKey.limit, total == null ? 0 : total);
        }
        return obj;
    }

    @Override
    public Object queryWithTotal(QueryExpEvaluator ee, TotalCountSetExtractor extractor) {
        return query(ee, extractor.getResultSetExtractor(), extractor);
    }

    private String generateTotalSql(String sql) {
        return "select count(*) from (" + sql + ") x";
    }

    @Override
    public ListResultSetMetaData<?> getListResultSetMetaData(ResultSet rs) {
        if (metaData == null) {
            try {
                metaData = create(rs.getMetaData());
            } catch (SQLException e) {
                throw RX.throwB(e);
            }
        }
        return metaData;
    }

    protected ListResultSetMetaDataSupport<?> create(ResultSetMetaData m) {
        return new ListResultSetMetaDataSupport<>(m);
    }


    public void addSQL(Exp expressionExp, Exp matchExp, Exp dsExp) {
        if (sqls == null) {
            sqls = new ArrayList<>();
        }
        sqls.add(new SQL(expressionExp, matchExp, dsExp));
    }

    public SQLKey getSql(QueryExpEvaluator ee) {
//        if(closureDefinition!=null){
//            ee.pushFsscriptClosure(closureDefinition.newFoggyClosure());
//        }
        String currentSql;

        try {
            if (onStartBuild != null) {
                onStartBuild.evalValue(ee);
            }
            for (SQL sql : sqls) {
                if (sql.match(ee)) {
                    currentSql = (String) sql.evalResult(ee);
                    Object[] args = ee.getArgs().toArray();


                    if (ee.needPaging()) {
                        return new SQLKey(currentSql, args, ee.getStart(), ee.getLimit(), sql);

                    } else {
                        return new SQLKey(currentSql, args, 0, 0, sql);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw RX.throwB(e);
        } finally {
//            if(closureDefinition!=null){
//                ee.popFsscriptClosure();
//            }
        }
        throw new RuntimeException("no match sql!");
    }

    private String generatePagingSql(SQLKey key, DatasetTemplate datasetTemplate) {
        return datasetTemplate.dialect.generatePagingSql(key.sql, key.start, key.limit);
    }

    @Override
    public Map<String, Object> queryMap(QueryExpEvaluator ee) {
        ee.setLimit(1);
        ee.setReturnTotal(false);
        return query(ee, (ResultSetExtractor<Map<String, Object>>) rs -> {
            if (rs.next()) {
                RowMapper<Map<String, Object>> mapper;
                if (ee.getQueryConfig() != null) {
                    switch (ee.getQueryConfig().getFormat()) {
                        case QueryConfigFormat
                                .JAVA_FORMAT:
                            mapper = JavaColumnNameFixRowMapper.build(rs);
                            break;
                        case QueryConfigFormat
                                .NO_FORMAT:
                        default:
                            mapper = DEFAULT_ROW_MAPPER;
                    }
                } else {
                    mapper = DEFAULT_ROW_MAPPER;
                }
                return mapper.mapRow(rs, 0);
            }
            return Collections.EMPTY_MAP;
        });
    }

    @Override
    public List<Object> queryList(QueryExpEvaluator ee) {
        ee.setLimit(1);
        ee.setReturnTotal(false);
        return query(ee, (ResultSetExtractor<List<Object>>) rs -> {
            if (rs.next()) {
                return ListRowMapper.DEFAULT.mapRow(rs, 0);
            }
            return Collections.EMPTY_LIST;
        });
    }
}
