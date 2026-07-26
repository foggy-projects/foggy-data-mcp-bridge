package com.foggyframework.fsscript.spring.cloud;

import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * {
 * service,
 * apiPath,
 * params:{},
 * data: body,
 * returnClass
 * }
 */
public class GetFunDef implements FunDef {

    @Autowired(required = false)
    FsscriptHttpClient httpClient;

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        Map<String, Object> config = (Map<String, Object>) args[0].evalResult(ee);
        return ExchangeFunDef.execute(httpClient, config, HttpMethod.GET);
    }



    @Override
    public String getName() {
        return "get";
    }

}
