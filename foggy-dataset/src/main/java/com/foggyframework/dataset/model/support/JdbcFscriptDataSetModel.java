package com.foggyframework.dataset.model.support;

import com.foggyframework.bundle.BundleResource;
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
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
public class JdbcFscriptDataSetModel extends ResultSetModelSupport implements DataSetModel {
    public static final ColumnMapRowMapper DEFAULT_ROW_MAPPER = new ColumnMapRowMapper();

    private BundleResource bundleResource;

    ListResultSetMetaData<?> metaData;

    FileFsscriptLoader fileFsscriptLoader;

    public JdbcFscriptDataSetModel(BundleResource bundleResource, FileFsscriptLoader fileFsscriptLoader) {
        this.bundleResource = bundleResource;
        this.fileFsscriptLoader = fileFsscriptLoader;
    }

    public static class FsscriptSQLKey extends SQLKey {

        DataSource dataSource;

        public FsscriptSQLKey(String sq, Object[] args, int start, int limit, DataSource dataSource) {
            super(sq, args, start, limit, null);
            this.dataSource = dataSource;
        }

        @Override
        public DataSource getDataSource(ExpEvaluator ee) {
            return dataSource;
        }
    }

    public FsscriptClosureDefinition getClosureDefinition() {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource);
        FsscriptClosureDefinition closureDefinition = fsscript.getFsscriptClosureDefinition();

        return closureDefinition;
    }

    @Override
    public QueryExpEvaluator newQueryExpEvaluator(ApplicationContext appCtx) {
        FsscriptClosureDefinition closureDefinition = getClosureDefinition();
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


    public SQLKey getSql(QueryExpEvaluator ee) {

        try {

            Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource.getResource());
            fsscript.eval(ee);
            DataSource dataSource = ee.getExportObject("dataSource");
            if (dataSource == null) {
                if (log.isDebugEnabled()) {
                    log.debug(bundleResource.getResource() + "未定义数据库，使用系统自带的dataSource");
                }
                dataSource = (DataSource) ee.getApplicationContext().getBean("dataSource");
            }
            Assert.notNull(dataSource,"dataSource不能为空");
            String sql = ee.getExportObject("sql");

            if (sql == null) {
                //如果脚本没有export sql 则必须导出 buildSql 函数
                FsscriptFunction buildSql = ee.getExportObject("buildSql");
                if (buildSql == null) {
                    throw RX.throwB(bundleResource.getResource() + "未导出sql或buildSql");
                }
                sql = (String) buildSql.autoApply(ee);
            }

            Object[] args = ee.getArgs().toArray();
            if (ee.needPaging()) {
                return new FsscriptSQLKey(sql, args, ee.getStart(), ee.getLimit(), dataSource);

            } else {
                return new FsscriptSQLKey(sql, args, 0, 0, dataSource);
            }

        } catch (IllegalArgumentException e) {
            throw RX.throwB(e);
        } finally {
        }
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
