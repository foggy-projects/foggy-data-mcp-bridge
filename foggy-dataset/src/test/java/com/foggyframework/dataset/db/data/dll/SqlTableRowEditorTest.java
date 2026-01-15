package com.foggyframework.dataset.db.data.dll;

import com.foggyframework.dataset.DatasetTestSupport;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

class SqlTableRowEditorTest extends DatasetTestSupport {
    @Autowired
    FileFsscriptLoader fileFsscriptLoader;

    @Autowired
    ApplicationContext appCtx;
    @Test
    void buildGtTimeOnDuplicateInsertSql() {
        Fsscript fScript = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/dataset/db/fsscript/SyncSqlTableTest.fsscript");

        ExpEvaluator ee = fScript.eval(appCtx);

        Object sqlTable = ee.getExportObject("sqlTable");


        SqlTableRowEditor sqlTableRowEditor =  new SqlTableRowEditor((SqlTable) sqlTable);

        OnDuplicateKeyBuilderKey sql = sqlTableRowEditor.buildGtTimeOnDuplicateKey(
               Arrays.asList("c1","test_id"),null);
        System.err.println(sql.getSql());
    }
}
