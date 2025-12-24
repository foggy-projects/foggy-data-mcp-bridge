package com.foggyframework.dataset.client.proxy;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.dataset.beans.DataSetModelFactory;
import com.foggyframework.dataset.client.annotates.DataSetQuery;
import com.foggyframework.dataset.client.annotates.OnDuplicate;
import com.foggyframework.dataset.client.proxy.converter.ReturnConverter;
import com.foggyframework.dataset.client.proxy.on_duplicate.BeanPropertyList;
import com.foggyframework.dataset.client.proxy.on_duplicate.BeanPropertyListList;
import com.foggyframework.dataset.db.data.dll.OnDuplicateKeyBuilderKey;
import com.foggyframework.dataset.db.data.dll.SqlTableRowEditor;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.utils.DatasetTemplate;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import lombok.Getter;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DatasetClient 代理类
 * 通过 JDK 动态代理实现接口方法的动态拦截和查询执行
 */
@Getter
public class DatasetClientProxy implements InvocationHandler {

    DataSetModelFactory dataSetModelFactory;

    ReturnConverterManager returnConverterManager;

    SystemBundlesContext systemBundlesContext;

    FileFsscriptLoader fileFsscriptLoader;

    Class<?> type;

    DatasetTemplate datasetTemplate;

    DatasetClientProxy() {
    }

    public DatasetClientProxy(SystemBundlesContext systemBundlesContext, Class<?> type) {
        Assert.notNull(systemBundlesContext, "systemBundlesContext不得为空！");
        Assert.notNull(type, "type不得为空！");

        this.systemBundlesContext = systemBundlesContext;
        this.type = type;

        dataSetModelFactory = systemBundlesContext.getApplicationContext().getBean(DataSetModelFactory.class);
        returnConverterManager = systemBundlesContext.getApplicationContext().getBean(ReturnConverterManager.class);
        fileFsscriptLoader = systemBundlesContext.getApplicationContext().getBean(FileFsscriptLoader.class);
    }

    private Map<String, JdbcQueryModel> name2Model = new HashMap<>();

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 Object 类的基本方法
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        ReturnConverter returnCover = returnConverterManager.getReturnConverter(method);

        OnDuplicate onDuplicate = method.getAnnotation(OnDuplicate.class);
        if (onDuplicate != null) {
            return onDuplicate(onDuplicate, method, args);
        }

        int maxLimit = returnCover.getDefaultMaxLimit();
        DataSetQuery dataSetQuery = method.getAnnotation(DataSetQuery.class);
        if (dataSetQuery != null) {
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

        // 获取模型
        JdbcQueryModel model = getModel(name);
        if (model == null) {
            throw RX.throwB("未能找到名为[" + name + "]的数据集");
        }

        // TODO: 实现基于 JdbcQueryModel 的查询逻辑
        // 当前版本需要根据 JdbcQueryModel 的 API 进行适配
        throw new UnsupportedOperationException("查询功能待完善，请使用 JdbcQueryModel 的原生 API");
    }

    private JdbcQueryModel getModel(String name) {
        JdbcQueryModel model = name2Model.get(name);
        if (model == null) {
            model = dataSetModelFactory.getDataSetModel(name, false);
            if (model != null) {
                name2Model.put(name, model);
            }
        }
        return model;
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
