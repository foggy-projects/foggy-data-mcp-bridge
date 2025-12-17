package com.foggyframework.dataset.utils;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.resultset.spring.BeanRowMapper;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapper;

public final class RowMapperUtils {
    public static final ColumnMapRowMapper DEFAULT_ROW_MAPPER = new ColumnMapRowMapper();

    public static RowMapper getRowMapper(Object beanClass) {


        if (StringUtils.isNotEmpty(beanClass)) {
            Class beanClazz = null;
            try {
                beanClazz = beanClass instanceof Class ? (Class) beanClass : Class.forName((String) beanClass);
            } catch (ClassNotFoundException e) {
                throw RX.throwB(e);
            }

            return new BeanRowMapper(beanClazz);
        }

        return DEFAULT_ROW_MAPPER;
    }
}
