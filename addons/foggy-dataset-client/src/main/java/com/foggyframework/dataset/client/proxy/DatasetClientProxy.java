package com.foggyframework.dataset.client.proxy;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.FoggyBeanUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.dataset.client.annotates.DataSetQuery;
import com.foggyframework.dataset.client.annotates.OnDuplicate;
import com.foggyframework.dataset.client.proxy.converter.ReturnConverter;
import com.foggyframework.dataset.client.proxy.on_duplicate.BeanPropertyList;
import com.foggyframework.dataset.client.proxy.on_duplicate.BeanPropertyListList;
import com.foggyframework.dataset.db.data.dll.OnDuplicateKeyBuilderKey;
import com.foggyframework.dataset.db.data.dll.SqlTableRowEditor;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.model.DataSetModel;
import com.foggyframework.dataset.model.KpiModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.dataset.model.support.JdbcFscriptDataSetModel;
import com.foggyframework.dataset.model.support.MapperBeanResultSetExtractor;
import com.foggyframework.dataset.model.support.PagingResultSetExtractor;
import com.foggyframework.dataset.mongo.MongoModel;
import com.foggyframework.dataset.mongo.funs.MongoFileFsscriptLoader;
import com.foggyframework.dataset.mongo.support.MongoFscriptDataSetModel;
import com.foggyframework.dataset.utils.DatasetTemplate;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DatasetClient 代理类
 * 通过 JDK 动态代理实现接口方法的动态拦截和查询执行
 * 支持 .ds (fsscript JDBC) 和 .ms (fsscript MongoDB) 文件格式
 */
@Getter
@Slf4j
public class DatasetClientProxy implements InvocationHandler {

    ReturnConverterManager returnConverterManager;

    SystemBundlesContext systemBundlesContext;

    FileFsscriptLoader fileFsscriptLoader;

    // MongoDB 加载器 (可选，仅当 foggy-dataset-mongo 模块存在时可用)
    MongoFileFsscriptLoader mongoFileFsscriptLoader;

    Class<?> type;

    DatasetTemplate datasetTemplate;

    // 模型类型常量
    private static final int FSSCRIPT_JDBC = 4;
    private static final int FSSCRIPT_MONGO = 5;

    // 缓存模型名称到类型的映射
    private final Map<String, Integer> name2Type = new HashMap<>();

    // 缓存已构建的 DataSetModel (JDBC)
    private final Map<String, DataSetModel> name2JdbcModel = new HashMap<>();

    // 缓存已构建的 MongoModel
    private final Map<String, MongoModel> name2MongoModel = new HashMap<>();

    // returnTotal 模式常量
    private static final int RETURN_TOTAL_MODE_TRUE = 1;
    private static final int RETURN_TOTAL_MODE_FALSE = 2;
    private static final int RETURN_TOTAL_MODE_AUTO = 3;

    // MongoDB 是否可用
    private boolean mongoEnabled = false;

    DatasetClientProxy() {
    }

    public DatasetClientProxy(SystemBundlesContext systemBundlesContext, Class<?> type) {
        Assert.notNull(systemBundlesContext, "systemBundlesContext不得为空！");
        Assert.notNull(type, "type不得为空！");

        this.systemBundlesContext = systemBundlesContext;
        this.type = type;

        returnConverterManager = systemBundlesContext.getApplicationContext().getBean(ReturnConverterManager.class);
        fileFsscriptLoader = systemBundlesContext.getApplicationContext().getBean(FileFsscriptLoader.class);

        // 尝试获取 MongoDB 加载器 (可选)
        try {
            mongoFileFsscriptLoader = systemBundlesContext.getApplicationContext().getBean(MongoFileFsscriptLoader.class);
            mongoEnabled = true;
            log.debug("MongoDB support enabled for DatasetClient");
        } catch (NoSuchBeanDefinitionException e) {
            mongoEnabled = false;
            log.debug("MongoDB support not available (foggy-dataset-mongo module not present)");
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 Object 类的基本方法
        if (method.getDeclaringClass() == Object.class) {
            String methodName = method.getName();
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return type.getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            }
            return method.invoke(this, args);
        }

        ReturnConverter returnCover = returnConverterManager.getReturnConverter(method);

        // 处理 OnDuplicate 注解
        OnDuplicate onDuplicate = method.getAnnotation(OnDuplicate.class);
        if (onDuplicate != null) {
            return onDuplicate(onDuplicate, method, args);
        }

        // 解析查询配置
        int returnTotalMode = RETURN_TOTAL_MODE_AUTO;
        int maxLimit = returnCover.getDefaultMaxLimit();
        DataSetQuery dataSetQuery = method.getAnnotation(DataSetQuery.class);
        if (dataSetQuery != null) {
            returnTotalMode = dataSetQuery.returnTotal() ? RETURN_TOTAL_MODE_TRUE : RETURN_TOTAL_MODE_FALSE;
            maxLimit = dataSetQuery.maxLimit();
        }

        // 找数据集名称
        String name = null;
        if (dataSetQuery != null) {
            name = dataSetQuery.name();
        }
        if (StringUtils.isEmpty(name)) {
            String methodName = method.getName();
            if (methodName.startsWith("find")) {
                name = methodName.substring("find".length());
            } else if (methodName.startsWith("query")) {
                name = methodName.substring("query".length());
            } else if (methodName.startsWith("get")) {
                name = methodName.substring("get".length());
            } else {
                name = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            }
        }

        // 获取方法参数名
        String[] methodArgs = FoggyBeanUtils.getParameterNames(method);

        // 确定是否返回总数
        boolean returnTotal;
        switch (returnTotalMode) {
            case RETURN_TOTAL_MODE_TRUE:
                returnTotal = true;
                break;
            case RETURN_TOTAL_MODE_FALSE:
                returnTotal = false;
                break;
            case RETURN_TOTAL_MODE_AUTO:
            default:
                returnTotal = returnCover.getDefaultReturnTotal();
                break;
        }

        // 构建查询生成器
        QueryExpEvaluatorGenerator generator = new PagingQueryExpEvaluatorGenerator(methodArgs, returnTotal, maxLimit);

        // 执行查询
        return executeQuery(generator, args, name, method.getGenericReturnType(), methodArgs, returnCover);
    }

    /**
     * 执行查询
     */
    private Object executeQuery(QueryExpEvaluatorGenerator generator, Object[] args, String name,
                                Type genericReturnType, String[] methodArgs, ReturnConverter returnCover) {
        Integer modelType = name2Type.get(name);

        if (modelType == null) {
            // 首次查询，尝试构建模型
            // 优先尝试 .ds 文件
            DataSetModel jdbcModel = buildJdbcFscriptDataSetModel(name, ".ds", false);
            if (jdbcModel != null) {
                name2Type.put(name, FSSCRIPT_JDBC);
                name2JdbcModel.put(name, jdbcModel);
                return visitDataSetModel(generator, args, jdbcModel, genericReturnType, returnCover);
            }

            // 尝试 .ms 文件 (如果 MongoDB 可用)
            if (mongoEnabled) {
                MongoModel mongoModel = buildMongoFscriptDataSetModel(name, false);
                if (mongoModel != null) {
                    name2Type.put(name, FSSCRIPT_MONGO);
                    name2MongoModel.put(name, mongoModel);
                    return visitMongoModel(generator, args, mongoModel, genericReturnType, returnCover);
                }
            }

            throw RX.throwB("未能找到名为[" + name + "]的数据集(.ds" + (mongoEnabled ? "或.ms" : "") + "文件)");
        } else {
            // 已缓存模型类型
            switch (modelType) {
                case FSSCRIPT_JDBC:
                    DataSetModel jdbcModel = name2JdbcModel.get(name);
                    if (jdbcModel == null) {
                        jdbcModel = buildJdbcFscriptDataSetModel(name, ".ds", true);
                        name2JdbcModel.put(name, jdbcModel);
                    }
                    return visitDataSetModel(generator, args, jdbcModel, genericReturnType, returnCover);
                case FSSCRIPT_MONGO:
                    MongoModel mongoModel = name2MongoModel.get(name);
                    if (mongoModel == null) {
                        mongoModel = buildMongoFscriptDataSetModel(name, true);
                        name2MongoModel.put(name, mongoModel);
                    }
                    return visitMongoModel(generator, args, mongoModel, genericReturnType, returnCover);
                default:
                    throw RX.throwB("不支持的模型类型: " + modelType);
            }
        }
    }

    /**
     * 构建基于 fsscript 的 JDBC DataSetModel
     */
    private JdbcFscriptDataSetModel buildJdbcFscriptDataSetModel(String name, String suffix, boolean errorIfNotFound) {
        if (!name.endsWith(suffix)) {
            name = name + suffix;
        }
        Bundle bundle = systemBundlesContext.getBundleByClassName(type.getName(), true);
        BundleResource bundleResource = bundle.findBundleResource(name, errorIfNotFound);

        if (bundleResource == null) {
            return null;
        }

        return new JdbcFscriptDataSetModel(bundleResource, fileFsscriptLoader);
    }

    /**
     * 构建基于 fsscript 的 MongoDB DataSetModel
     */
    private MongoFscriptDataSetModel buildMongoFscriptDataSetModel(String name, boolean errorIfNotFound) {
        if (!name.endsWith(".ms")) {
            name = name + ".ms";
        }
        Bundle bundle = systemBundlesContext.getBundleByClassName(type.getName(), true);
        BundleResource bundleResource = bundle.findBundleResource(name, errorIfNotFound);

        if (bundleResource == null) {
            return null;
        }

        return new MongoFscriptDataSetModel(bundleResource, mongoFileFsscriptLoader);
    }

    /**
     * 访问 DataSetModel 执行查询 (JDBC)
     */
    private Object visitDataSetModel(QueryExpEvaluatorGenerator generator, Object[] args,
                                     DataSetModel dataSetModel, Type genericReturnType, ReturnConverter returnCover) {
        // 构建查询表达式
        QueryExpEvaluator queryExpEvaluator = generator.generator(
                dataSetModel.newQueryExpEvaluator(systemBundlesContext.getApplicationContext()), args);

        // 获取 RowMapper
        RowMapper rowMapper = returnCover.getRowMapper(genericReturnType);
        final MapperBeanResultSetExtractor extractor = new MapperBeanResultSetExtractor(rowMapper);

        Object result;
        if (queryExpEvaluator.isReturnTotal()) {
            // 带总数的分页查询
            PagingResultSetExtractor pagingResultSetExtractor = new PagingResultSetExtractor(extractor);
            PagingResultImpl pagingResult = (PagingResultImpl) dataSetModel.queryWithTotal(queryExpEvaluator, pagingResultSetExtractor);
            result = returnCover.convertPagingResult(pagingResult);
        } else {
            // 普通查询
            List list = (List) dataSetModel.query(queryExpEvaluator, extractor);
            result = returnCover.convertList(queryExpEvaluator.getStart(), queryExpEvaluator.getLimit(), list);
        }
        return result;
    }

    /**
     * 访问 MongoModel 执行查询 (MongoDB)
     */
    private Object visitMongoModel(QueryExpEvaluatorGenerator generator, Object[] args,
                                   MongoModel mongoModel, Type genericReturnType, ReturnConverter returnCover) {
        // 构建查询表达式
        QueryExpEvaluator queryExpEvaluator = generator.generator(
                mongoModel.newQueryExpEvaluator(systemBundlesContext.getApplicationContext()), args);

        // 设置 Bean 类型
        Class<?> beanClazz = returnCover.getBeanClazz(genericReturnType);
        queryExpEvaluator.setBeanCls(beanClazz);

        // 执行分页查询
        PagingResultImpl pagingResult = mongoModel.queryPaging(queryExpEvaluator);
        return returnCover.convertPagingResult(pagingResult);
    }

    /**
     * OnDuplicate 处理
     */
    private Object onDuplicate(OnDuplicate onDuplicate, Method method, Object[] args) {
        String tableName = onDuplicate.table();
        String versionColumn = onDuplicate.versionColumn();
        Parameter[] pp = method.getParameters();
        Assert.isTrue(pp.length == 1, "OnDuplicate注释所在的函数，只能有且只有一个参数类型！" + method);
        Assert.hasText(tableName, "OnDuplicate必须定义表名" + method);
        Object form = args[0];
        Assert.notNull(form, "OnDuplicate调用参数不能为空！");
        Parameter p = pp[0];

        Class<?> formClass;
        int type = 0;
        if (Collection.class.isAssignableFrom(p.getType())) {
            if (p.getParameterizedType() instanceof ParameterizedType) {
                // OK
            } else {
                throw RX.throwB("OnDuplicate的参数如果是list，需要定义泛型！" + method);
            }
            formClass = (Class<?>) ((ParameterizedType) p.getParameterizedType()).getActualTypeArguments()[0];
            type = 1;
        } else {
            formClass = p.getType();
        }

        if (datasetTemplate == null) {
            datasetTemplate = new DatasetTemplate(systemBundlesContext.getApplicationContext().getBean(DataSource.class));
        }
        DataSource dataSource = datasetTemplate.getDataSource();
        SqlTable sqlTable = datasetTemplate.getSqlTableUsingCache(tableName, true);
        Assert.notNull(sqlTable.getIdColumn(), "OnDuplicate对应的表需要有id列才能正确的工作【" + tableName + "】," + method);
        SqlTableRowEditor sqlTableRowEditor = new SqlTableRowEditor(sqlTable, dataSource);
        Map<String, Object> configs = new HashMap<>();

        List<SqlColumn> formColumns = new ArrayList<>();
        List<BeanProperty> beanProperties = BeanInfoHelper.getClassHelper(formClass).getFieldProperties();
        for (BeanProperty fieldProperty : beanProperties) {
            SqlColumn sqlColumn = sqlTable.getSqlColumn(fieldProperty.getName(), false);
            if (sqlColumn == null) {
                sqlColumn = sqlTable.getSqlColumn(StringUtils.to_sm_string(fieldProperty.getName()), false);
            }
            if (sqlColumn == null) {
                Assert.notNull(sqlColumn, "OnDuplicate中的表单【" + formClass + "】定义了变量【" + fieldProperty.getName() + "】但是表中没有对应的字段！");
            }
            formColumns.add(sqlColumn);
        }

        OnDuplicateKeyBuilderKey builderKey;
        if (StringUtils.isEmpty(versionColumn)) {
            builderKey = sqlTableRowEditor.buildInsertOnDuplicateKey1(formColumns.stream().map(SqlColumn::getName).collect(Collectors.toList()), configs);
        } else {
            configs.put("versionColumn", versionColumn);
            builderKey = sqlTableRowEditor.buildGtTimeOnDuplicateKey(formColumns.stream().map(SqlColumn::getName).collect(Collectors.toList()), configs);
        }

        try {
            switch (type) {
                case 0:
                    BeanPropertyList beanPropertyList = new BeanPropertyList(form, beanProperties);
                    return sqlTableRowEditor.insertUpdateListData(dataSource, builderKey, beanPropertyList, configs, true);
                case 1:
                    BeanPropertyListList listList = new BeanPropertyListList(((List<?>) form), beanProperties);
                    return sqlTableRowEditor.insertUpdateByListList(dataSource, builderKey, listList, configs, false);
                default:
                    throw new UnsupportedOperationException();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    int getTypeMode(Class<?> cls) {
        if (com.foggyframework.dataset.model.PagingResult.class.isAssignableFrom(cls)) {
            return 2; // PAGING_RESULT
        } else if (cls.isAssignableFrom(List.class)) {
            return 1; // list
        } else if (cls.isAssignableFrom(Map.class)) {
            return 3; // map
        } else if (BeanInfoHelper.isBaseClass(cls)) {
            return 4; // simpleObject
        } else {
            return 5; // bean
        }
    }
}
