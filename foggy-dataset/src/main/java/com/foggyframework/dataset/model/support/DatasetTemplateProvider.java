package com.foggyframework.dataset.model.support;

import com.foggyframework.dataset.utils.DatasetTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class DatasetTemplateProvider {

    private static Map<DataSource, DatasetTemplate> dataSourceJdbcTemplateMap = new HashMap<>();
    public static final DatasetTemplate getDatasetTemplate(DataSource dataSource){
        DatasetTemplate jdbcTemplate = dataSourceJdbcTemplateMap.get(dataSource);
        if(jdbcTemplate==null){
            jdbcTemplate = new DatasetTemplate(dataSource);
            dataSourceJdbcTemplateMap.put(dataSource,jdbcTemplate);
        }
        return jdbcTemplate;
    }

}
