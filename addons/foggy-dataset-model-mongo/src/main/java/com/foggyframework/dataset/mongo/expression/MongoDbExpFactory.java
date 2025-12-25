package com.foggyframework.dataset.mongo.expression;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.exp.DefaultExpFactory;
import com.foggyframework.fsscript.exp.FunctionSet;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.bson.conversions.Bson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MongoDB 表达式工厂
 * 用于解析和创建 MongoDB 专用的表达式
 */
public class MongoDbExpFactory extends DefaultExpFactory implements ExpFactory {

    public static final MongoDbExpFactory MONGODB = new MongoDbExpFactory();

    @Override
    public FunctionSet getFunctionSet() {
        return DefaultExpFactory.DEFAULT.getFunctionSet();
    }

    public static Exp compileMongoEl(String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        ExpParser expParser = new ExpParser(MONGODB);
        return expParser.compileEl(exp);
    }

    @Override
    public Exp createMap(List is) {
        return new MongoDbMapExp(new ArrayList<MapEntry>(is));
    }

    @Override
    public Exp createArray(ListExp is) {
        fixArray(is);
        return new MongoDbListExp(new ArrayList<>(is));
    }

    public static class MongoDbListExp implements Exp, Serializable {
        private static final long serialVersionUID = 984313084333757885L;

        final List<Exp> ll;

        public MongoDbListExp(List<Exp> ll) {
            this.ll = ll;
        }

        @Override
        public Object evalValue(ExpEvaluator ee) {
            BasicDBList root = new BasicDBList();
            for (Exp e : ll) {
                e.apply2List2(root, ee);
            }
            return root;
        }

        @Override
        public Class getReturnType(ExpEvaluator ee) {
            return Map.class;
        }

        @Override
        public String toString() {
            return "MAP";
        }
    }

    public static Bson toBson(Object obj) {
        if (obj instanceof Bson) {
            return (Bson) obj;
        } else if (obj instanceof Map) {
            BasicDBObject root = new BasicDBObject();
            for (Map.Entry e : ((Map<?, ?>) obj).entrySet()) {
                root.append((String) e.getKey(), e.getValue());
            }
            return root;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public static List toBsonList(List update) {
        for (int i = 0; i < update.size(); i++) {
            Object v = update.get(i);
            if (v instanceof Bson) {
                continue;
            }
            update.set(i, toBson(v));
        }
        return update;
    }

    public static List toBsonListByArray(Object[] update) {
        for (int i = 0; i < update.length; i++) {
            Object v = update[i];
            if (v instanceof Bson) {
                continue;
            }
            update[i] = toBson(v);
        }
        return Arrays.asList(update);
    }

    public static class MongoDbMapExp implements Exp, Serializable {
        private static final long serialVersionUID = 984313084333757885L;

        final List<MapEntry> ll;

        public MongoDbMapExp(List<MapEntry> ll) {
            this.ll = ll;
        }

        @Override
        public Object evalValue(ExpEvaluator ee) {
            BasicDBObject root = new BasicDBObject();
            for (MapEntry e : ll) {
                e.applyMap(root, ee);
            }
            return root;
        }

        @Override
        public Class getReturnType(ExpEvaluator ee) {
            return Map.class;
        }

        @Override
        public String toString() {
            return "MAP";
        }
    }
}
