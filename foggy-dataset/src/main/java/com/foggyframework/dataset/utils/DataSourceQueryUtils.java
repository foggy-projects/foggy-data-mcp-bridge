/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.utils;


import com.foggyframework.dataset.model.support.DatasetTemplateProvider;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

/**
 *
 */
@Slf4j
public final class DataSourceQueryUtils {
    public static DatasetTemplate getDatasetTemplate(DataSource dataSource) {

        try {
            return DatasetTemplateProvider.getDatasetTemplate(dataSource);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }



    /**
     * import {queryCount} from @dataSourceQueryUtils;
     * var count = queryCount(dataSource,sql,args);
     *
     * @param dataSource
     * @param sql
     * @param args
     * @return
     */
    public static Long queryCount(DataSource dataSource,
                           String sql,
                           Object[] args) {

        return getDatasetTemplate(dataSource).queryCount(sql,args);
    }
}
