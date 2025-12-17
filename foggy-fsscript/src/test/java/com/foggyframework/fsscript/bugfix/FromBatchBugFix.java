package com.foggyframework.fsscript.bugfix;

import com.foggyframework.core.common.MapBuilder;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.core.utils.UuidUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
 class FromBatchBugFix {

    @Resource
    ApplicationContext appCtx;
@Test
     void evalValue() {
//    Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix_import1/bug_fix_import1.fsscript");

    Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix/from_batch/es-order-core.data-sync");

    ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx,fScript.getFsscriptClosureDefinition().newFoggyClosure());
    fScript.eval(ee);
        FsscriptFunction beforeLoad = (FsscriptFunction)ee.getExportObject("beforeLoad");
            beforeLoad.autoApply(ee);
        Map<String, Object> bindQueue = (Map)ee.getExportObject("bindQueue");

        FsscriptFunction update = (FsscriptFunction)bindQueue.get("update");
        FsscriptFunction create = (FsscriptFunction)bindQueue.get("create");

        Map mm = getTestData();

    ExecutorService executor = Executors.newCachedThreadPool();
    List<String> errors = new ArrayList<>();
    for (int i = 0; i <1000 ; i++) {
        executor.execute(()->{
            try {
                Object v = update.threadSafeAccept(mm);
                Object v2 = create.threadSafeAccept(mm);

                Assertions.assertNotNull(v);
                Assertions.assertNotNull(v2);
            }catch (Throwable t){
                errors.add(t.getMessage());
                t.printStackTrace();
            }
        });
    }

    Assertions.assertEquals(0,errors.size()    );


    }

    private Map getTestData(){
    return JsonUtils.toMap(" {\"dayIncreaseKey\":\"T00111000679\",\"objOrder\":0,\"receiptPayState\":1,\"lastModifiedDate\":1684816151950,\"orderPayState\":2,\"placeTime\":1684816151943,\"placeCgVipId\":\"CV0111340937\",\"placeTeamId\":\"T00111156015\",\"clearingTeamId\":\"T00111000679\",\"signPayState\":2,\"version\":1,\"ownerTeamId\":\"T00111156015\",\"monthPayState\":1,\"createdDate\":1684816151943,\"esOrderId\":\"170039943\",\"toPriceState\":1,\"receiptForward\":1,\"goodsCode\":\"170039943\",\"state\":4,\"placeWorkonId\":\"WOR111160946\"}");
    }
}
