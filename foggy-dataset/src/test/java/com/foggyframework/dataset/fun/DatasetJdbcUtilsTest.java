package com.foggyframework.dataset.fun;

import com.foggyframework.dataset.DatasetTestSupport;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

class DatasetJdbcUtilsTest extends DatasetTestSupport {
    @Resource
    DatasetJdbcUtils datasetJdbcUtils;

    @Test
    void getOrCreateDataSource() {
        DataSource ds = datasetJdbcUtils.getOrCreateDataSource(DatasetJdbcUtils.GetOrCreateDataSourceForm.builder()
                .beanName("test1").configPrefix("spring.test").build());
        DataSource ds2 = datasetJdbcUtils.getOrCreateDataSource(DatasetJdbcUtils.GetOrCreateDataSourceForm.builder()
                .beanName("test1").configPrefix("spring.test").build());
        Assertions.assertEquals(ds,ds2);
        try (Connection conn = ds.getConnection()) {

            ResultSet rs = conn.prepareStatement("select 1").executeQuery();
            Assert.assertTrue(rs.next());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}