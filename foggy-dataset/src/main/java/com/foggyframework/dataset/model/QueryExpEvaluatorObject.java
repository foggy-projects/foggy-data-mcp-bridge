package com.foggyframework.dataset.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryExpEvaluatorObject {
    List<Object> args = new ArrayList<>();
    boolean returnTotal;

    int start = 0;

    int limit = -1;

    Class<?> beanCls;

    QueryConfig queryConfig;

    public void updateQueryConfig(Object queryConfigObject) {
        if (queryConfigObject instanceof QueryConfig) {
            this.queryConfig = (QueryConfig) queryConfigObject;
        } else if (queryConfigObject instanceof Map<?, ?>) {
            queryConfig = new QueryConfig();
            Number format = (Number) ((Map<?, ?>) queryConfigObject).get("format");
            if (format != null) {
                queryConfig.setFormat(format.intValue());
            }

            Number limit = (Number) ((Map<?, ?>) queryConfigObject).get("limit");
            if (limit != null) {
                this.limit = limit.intValue();
            }
            Number start = (Number) ((Map<?, ?>) queryConfigObject).get("start");
            if (start != null) {
                this.start = start.intValue();
            }
            Number returnType = (Number) ((Map<?, ?>) queryConfigObject).get("returnType");
            if (returnType != null) {
                this.queryConfig.setReturnType(returnType.intValue());
            }

        }
    }

    public boolean needPaging() {
        return limit > 0;
    }

    public void addArg(Object value) {
        args.add(value);
    }
}
