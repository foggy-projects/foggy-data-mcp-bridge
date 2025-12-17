package com.foggyframework.fsscript.client.test.support;

import com.foggyframework.fsscript.client.annotates.FsscriptClient;
import com.foggyframework.fsscript.client.annotates.FsscriptClientMethod;

import java.util.Map;

@FsscriptClient
public interface FsscriptClientTest {

    DemoRet demo(String aa);

    @FsscriptClientMethod(name = "demo.fsscript",functionName = "test")
    DemoRet demo2(String cc);

    @FsscriptClientMethod(name = "BatchReceivableItemSettledV5Form_test.fsscript",functionName = "build")
    Map build(Object cc);
    @FsscriptClientMethod(name = "BatchReceivableItemSettledV5Form_test.fsscript",functionName = "build2")
    Map build2(Object aa,Object cc);
    @FsscriptClientMethod(name = "BatchReceivableItemSettledV5Form_test.fsscript",functionName = "build3")
    Map build3(Object aa,Object cc1,Object cc,Object c4);
    @FsscriptClientMethod(name = "BatchReceivableItemSettledV5Form_test.fsscript",functionName = "build4",cacheScript = true)
    Map build4(Object aa,Object cc1,Object cc,Object c4);

    @FsscriptClientMethod(name = "BatchReceivableItemSettledV5Form_test.fsscript",functionName = "build4")
    Map build4X(Object aa,Object cc1,Object cc,Object c4);
}
