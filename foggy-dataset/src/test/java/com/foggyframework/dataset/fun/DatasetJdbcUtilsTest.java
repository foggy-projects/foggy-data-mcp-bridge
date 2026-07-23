package com.foggyframework.dataset.fun;

import com.foggyframework.dataset.DatasetTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class DatasetJdbcUtilsTest extends DatasetTestSupport {
    @Autowired
    DatasetJdbcUtils datasetJdbcUtils;

    @Test
    void getOrCreateDataSource() throws SQLException {
        DataSource ds = datasetJdbcUtils.getOrCreateDataSource(DatasetJdbcUtils.GetOrCreateDataSourceForm.builder()
                .beanName("test1").configPrefix("spring.test").build());
        DataSource ds2 = datasetJdbcUtils.getOrCreateDataSource(DatasetJdbcUtils.GetOrCreateDataSourceForm.builder()
                .beanName("test1").configPrefix("spring.test").build());
        Assertions.assertSame(ds, ds2);
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {
            Assertions.assertEquals(1, resultSet.getMetaData().getColumnCount());
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals(1, resultSet.getInt(1));
            Assertions.assertFalse(resultSet.next());
        }
    }
}
