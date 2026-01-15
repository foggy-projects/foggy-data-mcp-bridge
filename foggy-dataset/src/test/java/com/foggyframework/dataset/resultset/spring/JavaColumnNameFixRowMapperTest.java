package com.foggyframework.dataset.resultset.spring;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
class JavaColumnNameFixRowMapperTest {


    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * TODO M_MAP_TEST 表丢失，暂时不测试
     */
    @Test
    void mapRow() {
//        jdbcTemplate.query("select * from M_MAP_TEST order by test_id ",new ResultSetExtractor(){
//            @Override
//            public Object extractData(ResultSet rs) throws SQLException, DataAccessException {
//                JavaColumnNameFixRowMapper mapper = JavaColumnNameFixRowMapper.build(rs);
//
//
//                rs.next();
//                Map<String,Object> mm1= mapper.mapRow(rs,1);
//                Assertions.assertEquals(mm1.get("testId"),"1");
//                Assertions.assertEquals(mm1.get("testC1"),"11");
//                Assertions.assertEquals(mm1.get("testC2"),"111");
//                Assertions.assertEquals(mm1.get("testC3"),1111);
//                Assertions.assertEquals(mm1.get("testC4"),"11111");
//                return mm1;
//            }
//        });
    }
}
