package com.foggyframework.fsscript.spring.cloud;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.Resource;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

/**
 * {
 * serviceName,
 * apiPath,
 * params:{},
 * data: body,
 * returnClass
 * }
 */
@Slf4j
public class PostFunDef implements FunDef {

    @Autowired(required = false)
    RestTemplate restTemplate;

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        Map<String, Object> config = (Map<String, Object>) args[0].evalResult(ee);
        return ExchangeFunDef.execute(restTemplate,config, HttpMethod.POST);
//        Map<String, Object> mm = (Map<String, Object>) args[0].evalResult(ee);
//        String serviceName = (String) mm.get("service");
//        String apiPath = (String) mm.get("apiPath");
//        Map<String, ?> params = (Map<String, ?>) mm.get("params");
//        Object data = mm.get("data");
//        Object returnClass = mm.get("returnClass");
//
//        String url = "http://" + serviceName + "" + apiPath;
//        if (log.isDebugEnabled()) {
//            log.debug("post url: " + url);
//            log.debug("params: " + params);
//            log.debug("data: " + data);
//            log.debug("returnClass: " + returnClass);
//        }
//        UriComponentsBuilder builder = GetFunDef.genUriComponentsBuilder(url, params);
//        URI uri = builder.build().toUri();
//
//        return restTemplate.postForEntity(uri, data, GetFunDef.toClass(returnClass));
    }


    @Override
    public String getName() {
        return "post";
    }

}
