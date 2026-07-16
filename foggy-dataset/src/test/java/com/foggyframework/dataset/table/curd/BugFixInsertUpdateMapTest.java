package com.foggyframework.dataset.table.curd;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
class BugFixInsertUpdateMapTest {

    @Autowired
    FileFsscriptLoader fileFsscriptLoader;

    @Autowired
    ApplicationContext appCtx;

    @Test
    void execute() {

//        org.springframework.core.io.Resource res = appCtx.getResource("classpath:/com/foggyframework/dataset/db/fscript/SyncSqlTableTest.fsscript");

        Fsscript fScript = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/dataset/db/table/curd/bug_fix_insertUpdateMapData.fsscript");

        ExpEvaluator ee = fScript.eval(appCtx);

        FsscriptFunction buildApply = Assertions.assertInstanceOf(
                FsscriptFunction.class,
                ee.getExportObject("buildApply"));

        Date createdDate = new Date(1677386657361L);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("orderType", 10);
        formData.put("type", 200);
        formData.put("orderId", "3511597");
        formData.put("id", "FMD11120");
        formData.put("state", 100);
        formData.put("createdDate", createdDate);
        formData.put("version", 1);
        formData.put("latestMessageItem", Map.of("messageId", "m1"));
        formData.put("processType", "manual");
        formData.put("processState", 1);
        formData.put("applyAction", Map.of("action", "update"));

        Map<?, ?> result = Assertions.assertInstanceOf(
                Map.class,
                buildApply.apply(new Object[]{formData}));
        Assertions.assertEquals(Set.of("key", "mongo", "row"), result.keySet());
        Assertions.assertEquals("updateApplyCard", result.get("key"));
        Assertions.assertEquals(
                Map.of(
                        "es_order_id", "3511597",
                        "update_apply_form_id", "FMD11120",
                        "update_apply_state", 100,
                        "update_apply_time", createdDate),
                Assertions.assertInstanceOf(Map.class, result.get("row")));
    }
}
