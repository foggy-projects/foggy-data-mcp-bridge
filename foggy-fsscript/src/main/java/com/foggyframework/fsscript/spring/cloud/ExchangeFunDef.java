package com.foggyframework.fsscript.spring.cloud;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

@Slf4j
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
    public static Object execute(RestOperations restTemplate, Map<String, Object> config, HttpMethod httpMethod) {
        Assert.notNull(restTemplate, "需要定义restTemplate才能使用该函数！");

        String serviceName = (String) config.get("service");
        String apiPath = (String) config.get("apiPath");
        Map<String, ?> params = (Map<String, ?>) config.get("params");
        Object returnClass = config.get("returnClass");

        String url = "http://" + serviceName + "" + apiPath;
        if (log.isDebugEnabled()) {
            log.debug("get url: " + url);
            log.debug("params: " + params);
            log.debug("returnClass: " + returnClass);
        }

        UriComponentsBuilder builder = genUriComponentsBuilder(url, params);
        URI uri = builder.build().toUri();
        ResponseEntity<?> response = null;
        switch (httpMethod.name()) {
            case "POST":
                Object data = config.get("data");
                response = restTemplate.postForEntity(uri, data, toClass(returnClass));
                break;
            case "GET":
                response = restTemplate.getForEntity(uri, toClass(returnClass));
                break;
            default:
                throw RX.throwB("当前不支持httpMethod：" + httpMethod);
        }
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        }
        throw RX.throwB(response.getStatusCode() + "");
    }

    private static UriComponentsBuilder genUriComponentsBuilder(String url, Map<String, ?> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (params != null) {
            for (Map.Entry<String, ?> e : params.entrySet()) {
                if (e.getValue() instanceof Collection) {
                    builder.queryParam(e.getKey(), (Collection) e.getValue());
                } else {
                    builder.queryParam(e.getKey(), e.getValue());
                }
            }

        }
        return builder;
    }

    private static Class<?> toClass(Object returnClass) {
        if (StringUtils.isEmpty(returnClass)) {
            return Object.class;
        } else if (returnClass instanceof Class) {
            return (Class<?>) returnClass;
        } else if (returnClass instanceof String) {
            try {
                return Class.forName((String) returnClass);
            } catch (ClassNotFoundException e) {
                throw RX.throwB(e);
            }
        } else {
            throw RX.throwB("不支持的returnClass：" + returnClass);
        }

    }
}
