package com.foggyframework.dataset.model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpEvaluatorDelegate;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.Data;
import lombok.experimental.Delegate;
import org.springframework.context.ApplicationContext;

@Data
public class QueryExpEvaluator extends ExpEvaluatorDelegate {

    public QueryExpEvaluator() {
        queryObject = new QueryExpEvaluatorObject();
    }
    public QueryExpEvaluator(QueryExpEvaluatorObject queryObject,ExpEvaluator expEvaluator) {
        super(expEvaluator);
        RX.notNull(queryObject,"QueryExpEvaluatorObject不能为空");
        this.queryObject = queryObject;
    }

    @Override
    public ExpEvaluator clone() {
        ExpEvaluator c = delegate.clone();
        return new QueryExpEvaluator(queryObject,c);
    }

    public static QueryExpEvaluator newInstance(ApplicationContext appCtx) {
        return new QueryExpEvaluator(DefaultExpEvaluator.newInstance(appCtx));
    }
    public static QueryExpEvaluator newInstance(ApplicationContext appCtx, FsscriptClosure fScriptClosure) {
        return new QueryExpEvaluator(DefaultExpEvaluator.newInstance(appCtx,fScriptClosure));
    }
    //
    public QueryExpEvaluator(ExpEvaluator delegate) {
        super(delegate);
        queryObject = new QueryExpEvaluatorObject();
    }

    @Delegate
    QueryExpEvaluatorObject queryObject;



//    public void addArg(Object value) {
//        args.add(value);
//    }

//    public void updateQueryConfig(Object queryConfigObject) {
//        queryObject.updateQueryConfig(queryConfigObject);
//        if (queryConfigObject instanceof QueryConfig) {
//            this.queryConfig = (QueryConfig) queryConfigObject;
//        } else if (queryConfigObject instanceof Map<?, ?>) {
//            queryConfig = new QueryConfig();
//            Number format = (Number) ((Map<?, ?>) queryConfigObject).get("format");
//            if (format != null) {
//                queryConfig.setFormat(format.intValue());
//            }
//
//            Number limit = (Number) ((Map<?, ?>) queryConfigObject).get("limit");
//            if (limit != null) {
//                this.limit = limit.intValue();
//            }
//            Number start = (Number) ((Map<?, ?>) queryConfigObject).get("start");
//            if (start != null) {
//                this.start = start.intValue();
//            }
//            Number returnType = (Number) ((Map<?, ?>) queryConfigObject).get("returnType");
//            if (returnType != null) {
//                this.queryConfig.setReturnType(returnType.intValue());
//            }
//
//        }
//    }
}
