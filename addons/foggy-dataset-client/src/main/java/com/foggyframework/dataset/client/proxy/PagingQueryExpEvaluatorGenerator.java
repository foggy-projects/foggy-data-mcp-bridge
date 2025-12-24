package com.foggyframework.dataset.client.proxy;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.QueryExpEvaluator;

import java.util.HashMap;
import java.util.Map;

public class PagingQueryExpEvaluatorGenerator implements QueryExpEvaluatorGenerator {
    public PagingQueryExpEvaluatorGenerator(String[] methodArgs, boolean returnTotal) {
        this(methodArgs, returnTotal, 0);
    }

    public PagingQueryExpEvaluatorGenerator(String[] methodArgs, boolean returnTotal, int maxLimit) {
        this.methodArgs = methodArgs;
        this.maxLimit = maxLimit;
        this.returnTotal = returnTotal;

        if (this.maxLimit <= 0) {
            this.maxLimit = 10;
        }
    }

    String[] methodArgs;

    boolean returnTotal;

    int maxLimit;

    @Override
    public QueryExpEvaluator generator(QueryExpEvaluator ee, Object[] objects) {
        int start = -1;
        int limit = -1;
        int page = -1;
        int pageSize = -1;
        Map<String, Object> param = new HashMap<>();

        if (objects != null) {
            for (int i = 0; i < objects.length; i++) {
                String name = methodArgs[i];
                Object obj = objects[i];

                ee.setVar("$" + name, obj);

                if (name.equals("start") && (obj instanceof Integer || obj instanceof Long)) {
                    start = (int) obj;
                } else if (name.equals("limit") && (obj instanceof Integer || obj instanceof Long)) {
                    limit = (int) obj;
                } else if (name.equals("page") && (obj instanceof Integer || obj instanceof Long)) {
                    page = (int) obj;
                } else if (name.equals("pageSize") && (obj instanceof Integer || obj instanceof Long)) {
                    pageSize = (int) obj;
                }
                if (obj instanceof PagingRequest) {
                    // PagingRequest 参数特殊处理
                    PagingRequest p = (PagingRequest) obj;
                    start = p.getStart();
                    limit = p.getLimit();

                    if (p.getParam() instanceof Map) {
                        param.putAll((Map<? extends String, ?>) p.getParam());
                    } else {
                        if (p.getParam() != null) {
                            for (BeanProperty readMethod : BeanInfoHelper.getClassHelper(p.getParam().getClass()).getReadMethods()) {
                                param.put(readMethod.getName(), readMethod.getBeanValue(p.getParam()));
                            }
                        }
                    }

                } else {
                    param.put(name, obj);
                    ee.setVar(name, obj);
                }
            }
        }

        if (limit <= 0) {
            limit = pageSize;
            if (limit < 0) {
                limit = this.maxLimit;
            }
        }

        if (start <= 0) {
            start = page * limit;
            if (start < 0) {
                start = 0;
            }
        }
        ee.setStart(start);
        ee.setLimit(limit);
        ee.setReturnTotal(returnTotal);
        PagingRequest pagingQuery = new PagingRequest();
        pagingQuery.setStart(start);
        pagingQuery.setLimit(limit);
        pagingQuery.setParam(param);
        ee.setVar("form", pagingQuery);

        if (ee.getVarDef("_param") == null) {
            ee.setVar("_param", param);
        }
        ee.setVar("pagingQuery", pagingQuery);

        return ee;
    }
}
