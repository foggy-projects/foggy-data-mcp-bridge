package com.foggyframework.dataset.db.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.DbModelDef;
import com.foggyframework.dataset.db.model.def.dimension.DbDimensionDef;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.db.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.LoaderSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelDimensionImpl;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelTimeDimensionImpl;
import com.foggyframework.dataset.db.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.db.model.impl.measure.DbModelMeasureImpl;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectSupport;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Setter
@Getter
public class TableModelLoaderManagerImpl extends LoaderSupport implements TableModelLoaderManager {
    @Resource
    DataSource dataSource;

//    /**
//     * MongoDB 模型加载器（可选）
//     * <p>仅当项目配置了 MongoDB（存在 MongoClient Bean）时自动注入
//     */
//    @Autowired(required = false)
//    MongoModelLoader mongoModelLoader;

    DbModelFileChangeHandler fileChangeHandler;
    List<DbModelLoadProcessor> processors;

    Map<String, TableModel> name2JdbcModel = new HashMap<>();
    Map<String, TableModelLoader> typeName2Loader = new HashMap<>();
    int dimIdx;
    int modelIdx;

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<DbModelLoadProcessor> processors, List<TableModelLoader> loaders) {
        super(systemBundlesContext, fileFsscriptLoader);
        this.processors = processors;
        loaders.forEach(loader -> typeName2Loader.put(loader.getTypeName(), loader));
    }

    @Override
    public void clearAll() {
        name2JdbcModel = new HashMap<>();
    }

    @Override
    synchronized public TableModel load(String name) {
        TableModel tm = name2JdbcModel.get(name);
        if (tm != null) {
            return tm;
        }
        Fsscript fScript = this.findFsscript(name, "tm");
        ExpEvaluator ee = fScript.eval(systemBundlesContext.getApplicationContext());
//        fScript.get
        Object model = ee.getExportObject("model");
        if (model == null) {
            throw RX.throwAUserTip(DatasetMessages.modelNotFound(name));
        }
        Bundle bundle = fScript.getFsscriptClosureDefinition().getFsscriptClosureDefinitionSpace().getBundle();
        DbModelDef def = FsscriptConversionService.getSharedInstance().convert(model, DbModelDef.class);
        fix(def);

        TableModelLoader tableModelLoader = typeName2Loader.get(def.getType());
        tm = tableModelLoader.load(fScript, def, bundle);
        tm = initialization(tm, def, bundle);
//        if (StringUtils.equals(def.getType(), "mongo")) {
//            RX.notNull(mongoModelLoader, "使用 MongoDB 模型需要在项目中配置 MongoDB 连接（确保存在 MongoClient Bean）");
//            tm = mongoModelLoader.load(fScript, def, bundle);
//        } else {
//            tm = load(def.getDataSource() == null ? dataSource : def.getDataSource(), fScript, def, bundle, null);
//        }


        name2JdbcModel.put(name, tm);
        return tm;
    }

    private void fix(DbModelDef def) {
        if (def.getProperties() != null) {
            for (DbPropertyDef property : def.getProperties()) {
                if (property == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(property.getName()) && StringUtils.isEmpty(property.getAlias())) {
                    property.setAlias(property.getName());
                }
            }
        }
        if (def.getMeasures() != null) {
            for (DbMeasureDef measure : def.getMeasures()) {
                if (measure == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(measure.getName()) && StringUtils.isEmpty(measure.getAlias())) {
                    measure.setAlias(measure.getName());
                }
            }
        }

        if (StringUtils.isEmpty(def.getType())) {
            def.setType("jdbc");
        }

    }

    public TableModel initialization(TableModel jm, DbModelDef def, Bundle bundle) {
        RX.notNull(dataSource, "加载模型时的数据源不得为空");
        RX.notNull(dataSource, "加载模型时的def不得为空");

        String tableName = def.getTableName();
        String viewSql = def.getViewSql();
        TableModelSupport jdbcModel = jm.getDecorate(TableModelSupport.class);
//        JdbcModelImpl jdbcModel = new JdbcModelImpl(dataSource,fScript);
//        def.apply(jdbcModel);
//        jdbcModel.setMongoTemplate(defMongoTemplate);

//        jdbcModel.setQueryObject(loadQueryObject(dataSource, jdbcModel.getModelType() == JdbcModelType.mongo ? null : tableName, viewSql, def.getSchema()));
        /**
         * 加入JSON列的支持,目前先让属性和度量支持
         */
        if (def.getMeasures() != null) {
            for (DbMeasureDef measure : def.getMeasures()) {
                if (measure != null && StringUtils.isNotEmpty(measure.getColumn()) && measure.getColumn().indexOf("->") > 0) {
                    jdbcModel.getQueryObject().appendSqlColumn(measure.getColumn(), "OBJECT", 0);
                }
            }
        }
        if (def.getProperties() != null) {
            for (DbPropertyDef measure : def.getProperties()) {
                if (measure != null && StringUtils.isNotEmpty(measure.getColumn()) && measure.getColumn().indexOf("->") > 0) {
                    jdbcModel.getQueryObject().appendSqlColumn(measure.getColumn(), "OBJECT", 0);
                }
            }
        }

        JdbcModelLoadContext context = new JdbcModelLoadContext(dataSource, def, jdbcModel, bundle);
        //加载维度定义
        loadDimensions(context);

        loadProperties(context);
        //加载度量
        loadMeasures(context);

        //初始化主表、维表或相关的 alias
        initAlias(context);

        jdbcModel.init();
//        initDimension(context);

//        if
//        dialect.getColumnsByTableName()

        return jdbcModel;
    }

    private void initAlias(JdbcModelLoadContext context) {
//        idx++;
        String d = "d";
        String m = "m";
        QueryObject qo = context.getJdbcModel().getQueryObject();
        qo.getDecorate(QueryObjectSupport.class).setAlias(m + (++modelIdx));

        for (DbDimension dimension : context.getJdbcModel().getDimensions()) {
            QueryObject dqo = dimension.getQueryObject();
            if (dqo == null) {
                continue;
            }
            dqo.getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
            if (dimension.getDecorate(DbModelParentChildDimensionImpl.class) != null) {
                dimension.getDecorate(DbModelParentChildDimensionImpl.class).getClosureQueryObject().getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
            }

        }
    }

    private void loadDimensions(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<DbDimensionDef> dimensionDefList = def.getDimensions();
        if (dimensionDefList != null) {
            //加载在Model上定义的维度
            dimensionDefList = dimensionDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (DbDimensionDef dimensionDef : dimensionDefList) {
                DbDimension dbDimension = loadDimension(context, dimensionDef, true);
            }
        }

        if (def.isAutoLoadDimensions()) {
            //TODO 自动加载维度
        }

        /**
         * 初始化维度的相关列
         */
        for (DbDimension dimension : context.getJdbcModel().getDimensions()) {

            DbDimensionSupport ds = dimension.getDecorate(DbDimensionSupport.class);

            //初始化维度的相关列
            ds.init();
        }

    }


    private void loadProperties(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<DbPropertyDef> jdbcPropertyDefList = def.getProperties();
        if (jdbcPropertyDefList != null) {
            jdbcPropertyDefList = jdbcPropertyDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (DbPropertyDef propertyDef : jdbcPropertyDefList) {
                try {
                    DbProperty dbProperty = loadProperty(context, null, propertyDef);
                    context.getJdbcModel().addJdbcProperty(dbProperty);
                } catch (Throwable t) {
                    log.error("加载属性发生错误", t);
                    if (propertyDef.isDeprecated()) {
                        log.warn("忽略被标记为废弃的属性:{}", propertyDef.getName());
                        context.getJdbcModel().addDeprecated(propertyDef);
                    } else {
                        throw ErrorUtils.toRuntimeException(t);
                    }
                }
            }
        }
    }


    private void loadMeasures(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<DbMeasureDef> measureDefList = def.getMeasures();
        if (measureDefList != null) {
            //加载在Model上定义的维度
            measureDefList = measureDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (DbMeasureDef measureDef : measureDefList) {
                try {
                    loadMeasure(context, measureDef);
                } catch (Throwable t) {
                    log.error("加载度量发生错误", t);
                    if (measureDef.isDeprecated()) {
                        log.warn("忽略被标记为废弃的度量:{}", measureDef.getName());
                        context.getJdbcModel().addDeprecated(measureDef);
                    } else {
                        throw ErrorUtils.toRuntimeException(t);
                    }
                }

            }
        }

        if (def.isAutoLoadDimensions()) {
            //TODO 自动加载维度
        }

    }

    private DbDimension loadDimension(JdbcModelLoadContext context, DbDimensionDef dimensionDef, boolean modelDim) {
        return loadDimension(context, dimensionDef, modelDim, null);
    }

    /**
     * 加载维度（支持嵌套维度）
     *
     * @param context         加载上下文
     * @param dimensionDef    维度定义
     * @param modelDim        是否添加到模型的维度列表
     * @param parentDimension 父维度（如果是嵌套维度）
     * @return 加载后的维度
     */
    private DbDimension loadDimension(JdbcModelLoadContext context, DbDimensionDef dimensionDef, boolean modelDim, DbDimension parentDimension) {

        /**
         * 检查数据
         */
        if (context.getJdbcModel().findJdbcDimensionByName(dimensionDef.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateDimension(dimensionDef.getName()));
        }

        /**
         * 开始加载维度
         */
        DbDimensionSupport dimension = null;
        if (DbDimensionType.DATETIME == DbDimensionType.fromString(dimensionDef.getType())) {
            //时间维
            dimension = new DbModelTimeDimensionImpl();
        } else if (StringUtils.isNotEmpty(dimensionDef.getParentKey())) {
            //父子结构~
            DbModelParentChildDimensionImpl parentChildDimension = new DbModelParentChildDimensionImpl(dimensionDef.getParentKey(), dimensionDef.getChildKey(), dimensionDef.getClosureTableName());
            dimension = parentChildDimension;
            parentChildDimension.setClosureQueryObject(loadQueryObject(dimensionDef.getDataSource() == null ? dataSource : dimensionDef.getDataSource(), dimensionDef.getClosureTableName(), null, dimensionDef.getClosureTableSchema()));
            //childKey用来作为ClosureQueryObject的primaryKey与主表进行关联，注意，childKey实际上可不是主键
            parentChildDimension.getClosureQueryObject().getDecorate(QueryObjectSupport.class).setPrimaryKey(dimensionDef.getChildKey());
        } else {
            dimension = new DbModelDimensionImpl();
        }

//        BeanUtils.copyProperties(dimensionDef, dimension);
        dimensionDef.apply(dimension);
        if (StringUtils.isEmpty(dimension.getAlias())) {
            dimension.setAlias(dimension.getName());
        }
        if (StringUtils.isEmpty(dimension.getKeyCaption())) {
            dimension.setKeyCaption(dimension.getCaption() + "主键");
        }

        // 设置父维度（如果是嵌套维度）
        if (parentDimension != null) {
            dimension.setParentDimension(parentDimension);
        }

        //加载维表
        if (StringUtils.isNotEmpty(dimensionDef.getTableName()) || StringUtils.isNotEmpty(dimensionDef.getViewSql())) {
            //有维表，或视图
            dimension.setQueryObject(loadQueryObject(dimensionDef.getDataSource() == null ? context.getDataSource() : dimensionDef.getDataSource(), dimensionDef.getTableName(), dimensionDef.getViewSql(), dimensionDef.getSchema()));
            dimension.getQueryObject().getDecorate(QueryObjectSupport.class).setPrimaryKey(dimension.getPrimaryKey());
        }

        if (dimensionDef.getProperties() != null) {
            for (DbPropertyDef propertyDef : dimensionDef.getProperties()) {
                DbProperty dbProperty = loadProperty(context, dimension, propertyDef);
                dimension.addJdbcProperty(dbProperty);
            }
        }

        processJdbcDataProvider(dimension.getDataProvider());

        DbDimension dbDimension = dimension;
        for (DbModelLoadProcessor processor : processors) {
            dbDimension = processor.processJdbcDimension(context, dbDimension);
        }
        if (modelDim) {
            context.getJdbcModel().addDimension(dbDimension);
        }

        // 递归加载嵌套子维度
        if (dimensionDef.getDimensions() != null && !dimensionDef.getDimensions().isEmpty()) {
            for (DbDimensionDef childDef : dimensionDef.getDimensions()) {
                DbDimension childDimension = loadDimension(context, childDef, true, dbDimension);
                dbDimension.addChildDimension(childDimension);
            }
        }

        return dbDimension;
    }

    private void processJdbcDataProvider(DbDataProvider dataProvider) {
        if (DbDimensionType.DICT == dataProvider.getDimensionType()) {
            RX.notNull(dataProvider.getExtData(), String.format("字典类型的维%s，必须有extData", dataProvider.getName()));

            String dictClass = dataProvider.getExtDataValue("dictClass");
            RX.hasText(dictClass, String.format("字典类型的维%s，必须有extData.dictClass", dataProvider.getName()));
            String dictName = dictClass.substring(dictClass.lastIndexOf(".") + 1);

            dataProvider.getExtData().put("dictName", dictName);
        }
    }

    private DbProperty loadProperty(JdbcModelLoadContext context, DbDimensionSupport dimension, DbPropertyDef propertyDef) {


        DbPropertyImpl property = new DbPropertyImpl();
        propertyDef.apply(property);
//        if (StringUtils.isNotEmpty(property.getType())) {
//            property.setType(property.getType());
//        }
        if (DbColumnType.DAY == property.getType()) {
            if (StringUtils.isEmpty(property.getFormat())) {
                property.setFormat("YYYY-MM-DD");
            }
        }

        property.setJdbcModel(context.getJdbcModel());
        property.setDbDimension(dimension);
        property.init();

        processJdbcDataProvider(property.getDataProvider());

        DbProperty dbProperty = property;

        if (propertyDef.getFormulaDef() != null && propertyDef.getFormulaDef().getBuilder() != null) {
            dbProperty.setFormulaBuilder(propertyDef.getFormulaDef().getBuilder());
        }
//        /**
//         * 加入维度支持
//         */
//        if(propertyDef.getDim()!=null){
//            JdbcDimension jdbcDimension = loadDimension(context, propertyDef.getDim(),true);
//
//        }


        for (DbModelLoadProcessor processor : processors) {
            dbProperty = processor.processJdbcProperty(context, dbProperty);
        }

        return dbProperty;
    }

    private void loadMeasure(JdbcModelLoadContext context, DbMeasureDef measureDef) {

        /**
         * 检查数据
         */
        if (context.getJdbcModel().findJdbcMeasureByName(measureDef.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateMeasure(measureDef.getName()));
        }

        /**
         * 开始加载维度
         */
        DbModelMeasureImpl measure = new DbModelMeasureImpl();
        measureDef.apply(measure);
//        if (StringUtils.isNotEmpty(measure.getType())) {
//            measure.setType(measure.getType().toUpperCase());
//        }
        if (StringUtils.isEmpty(measureDef.getAggregation()) && StringUtils.equalsIgnoreCase("money", measureDef.getType())) {
            //如果未定义Aggregation，且是money类型，默认sum
            measureDef.setAggregation("sum");
        }
        if (StringUtils.isNotEmpty(measureDef.getAggregation())) {
            measure.getDecorate(DbMeasureSupport.class).setAggregation(DbAggregation.valueOf(measureDef.getAggregation().toUpperCase()));
        }

        if (measureDef.getFormulaDef() != null && measureDef.getFormulaDef().getBuilder() != null) {
            measure.getDecorate(DbMeasureSupport.class).setFormulaBuilder(measureDef.getFormulaDef().getBuilder());
        }

        measure.getDecorate(DbMeasureSupport.class).init(context.getJdbcModel(), measureDef);

        DbMeasure jdbcMeasure = measure;
        for (DbModelLoadProcessor processor : processors) {
            jdbcMeasure = processor.processJdbcMeasure(context, jdbcMeasure);
        }

        context.getJdbcModel().addMeasure(jdbcMeasure);
//
//        //加载维表
//        dimension.setQueryObject(loadQueryObject(dimensionDef.getTableName(),dimensionDef.getViewSql()));

    }
}
