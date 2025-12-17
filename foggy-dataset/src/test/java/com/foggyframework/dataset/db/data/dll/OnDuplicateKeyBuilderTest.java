package com.foggyframework.dataset.db.data.dll;

import com.foggyframework.core.common.MapBuilder;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.sql.Types;

@Slf4j
class OnDuplicateKeyBuilderTest {

    @Test
    void genByConfigs() {
        SqlTable table = new SqlTable("test1", "test1");
        table.addSqlColumn(new SqlColumn("c1", "c1", Types.VARCHAR));
        table.addSqlColumn(new SqlColumn("c2", "c2", Types.INTEGER));
        table.addSqlColumn(new SqlColumn("c3", "c3", Types.INTEGER));
        table.setIdColumn(table.getSqlColumn(0));

        InsertBuilder insertBuilder = new InsertBuilder(table);
        insertBuilder.addColumn(1, table.getSqlColumn(0));
        insertBuilder.addColumn(2, table.getSqlColumn(1));
        insertBuilder.addColumn(3, table.getSqlColumn(2));
        OnDuplicateKeyBuilder onDuplicateKeyBuilder = new OnDuplicateKeyBuilder(insertBuilder);

        String str = onDuplicateKeyBuilder.genByConfigs(MapBuilder.builder().put("updates", MapBuilder.builder().put("c2", "c2").build()).build());

        log.debug(str);
        Assert.assertEquals("insert into test1 (c1,c2,c3) values (?,?,?) on duplicate key update c2=c2,c3=values(c3)",str);
    }
}