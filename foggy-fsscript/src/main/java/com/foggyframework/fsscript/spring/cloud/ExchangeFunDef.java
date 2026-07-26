package com.foggyframework.fsscript.spring.cloud;

import com.foggyframework.core.ex.RX;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

import java.util.Map;

public class ExchangeFunDef {

    /**
     * <pre>
     * config = {
     *  service,
     *  apiPath,
     *  params:{},
     *  data: body,
     *  returnClass
     * }
     * </pre>
     */
    public static Object execute(
            FsscriptHttpClient httpClient,
            Map<String, Object> config,
            HttpMethod httpMethod
    ) {
        Assert.notNull(httpClient, "需要定义 fsscriptHttpClient 才能使用该函数！");
        if (httpMethod != HttpMethod.GET && httpMethod != HttpMethod.POST) {
            throw RX.throwB("当前不支持 httpMethod：" + httpMethod);
        }
        return httpClient.execute(config, httpMethod);
    }
}
