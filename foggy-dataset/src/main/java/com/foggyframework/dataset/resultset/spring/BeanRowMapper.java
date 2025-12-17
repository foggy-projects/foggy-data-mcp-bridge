package com.foggyframework.dataset.resultset.spring;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 优化从数据库查询返回的列名称
 * 呃，把xx_aa这样的列，转换成xxAa返回
 */
public class BeanRowMapper<T> implements RowMapper<T> {

    Class<T> clazz;
    Map<String, BeanProperty> name2BeanProperty;

    public BeanRowMapper(Class clazz) {
        Assert.notNull(clazz, "clazz不得为空,如果没有clazz,您可以使用 JavaColumnNameFixRowMapper");
        this.clazz = clazz;

    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        if (name2BeanProperty == null) {
            //建立rs字段到BeanProperty的映射关系
            name2BeanProperty = new HashMap<>();
            BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(clazz);

            int c = rs.getMetaData().getColumnCount();

            for (int i = 1; i <= c; i++) {
                String name = rs.getMetaData().getColumnLabel(i);
                BeanProperty beanProperty = beanInfoHelper.getBeanProperty(name);
                if (beanProperty == null && name.indexOf("_") > 0) {

                    beanProperty = beanInfoHelper.getBeanProperty(StringUtils.to(name));

                }
                if (beanProperty != null && beanProperty.hasWriter()) {
                    name2BeanProperty.put(name, beanProperty);
                }

            }

        }
        try {
            T inst = clazz.newInstance();
            for (Map.Entry<String, BeanProperty> e : name2BeanProperty.entrySet()) {
                Object value = rs.getObject(e.getKey());
                if (value != null) {
                    e.getValue().setBeanValue(inst, value);
                }
            }
            return inst;
        } catch (InstantiationException e) {
            throw RX.throwB(e);
        } catch (IllegalAccessException e) {
            throw RX.throwB(e);
        }


    }
}
