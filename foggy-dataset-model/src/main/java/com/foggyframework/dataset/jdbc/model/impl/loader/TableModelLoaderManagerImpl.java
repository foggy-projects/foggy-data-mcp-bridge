package com.foggyframework.dataset.jdbc.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.dataset.jdbc.model.def.dimension.JdbcDimensionDef;
import com.foggyframework.dataset.jdbc.model.def.measure.JdbcMeasureDef;
import com.foggyframework.dataset.jdbc.model.def.property.JdbcPropertyDef;
import com.foggyframework.dataset.jdbc.model.engine.mongo.MongoModelLoader;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcModelFileChangeHandler;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.LoaderSupport;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcDimensionSupport;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcModelDimensionImpl;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcModelParentChildDimensionImpl;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcModelTimeDimensionImpl;
import com.foggyframework.dataset.jdbc.model.impl.measure.JdbcMeasureSupport;
import com.foggyframework.dataset.jdbc.model.impl.measure.JdbcModelMeasureImpl;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelImpl;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelSupport;
import com.foggyframework.dataset.jdbc.model.impl.property.JdbcPropertyImpl;
import com.foggyframework.dataset.jdbc.model.impl.utils.QueryObjectSupport;
import com.foggyframework.dataset.jdbc.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.jdbc.model.impl.utils.ViewSqlQueryObject;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

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

    /**
     * MongoDB 模型加载器（可选）
     * <p>仅当项目配置了 MongoDB（存在 MongoClient Bean）时自动注入
     */
    @Autowired(required = false)
    MongoModelLoader mongoModelLoader;

    JdbcModelFileChangeHandler fileChangeHandler;
    List<JdbcModelLoadProcessor> processors;

    Map<String, JdbcModel> name2JdbcModel = new HashMap<>();
    Map<String, TableModelLoader> typeName2Loader = new HashMap<>();
    int dimIdx;
    int modelIdx;

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<JdbcModelLoadProcessor> processors, List<TableModelLoader> loaders) {
        super(systemBundlesContext, fileFsscriptLoader);
        this.processors = processors;
        loaders.forEach(loader -> typeName2Loader.put(loader.getTypeName(), loader));
    }

    @Override
    public void clearAll() {
        name2JdbcModel = new HashMap<>();
    }

    @Override
    synchronized public JdbcModel load(String name) {
        JdbcModel tm = name2JdbcModel.get(name);
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
        JdbcModelDef def = FsscriptConversionService.getSharedInstance().convert(model, JdbcModelDef.class);
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

    private void fix(JdbcModelDef def) {
        if (def.getProperties() != null) {
            for (JdbcPropertyDef property : def.getProperties()) {
                if (property == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(property.getName()) && StringUtils.isEmpty(property.getAlias())) {
                    property.setAlias(property.getName());
                }
            }
        }
        if (def.getMeasures() != null) {
            for (JdbcMeasureDef measure : def.getMeasures()) {
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

    public JdbcModel initialization(JdbcModel jm, JdbcModelDef def, Bundle bundle) {
        RX.notNull(dataSource, "加载模型时的数据源不得为空");
        RX.notNull(dataSource, "加载模型时的def不得为空");

        String tableName = def.getTableName();
        String viewSql = def.getViewSql();
        JdbcModelSupport jdbcModel = jm.getDecorate(JdbcModelSupport.class);
//        JdbcModelImpl jdbcModel = new JdbcModelImpl(dataSource,fScript);
//        def.apply(jdbcModel);
//        jdbcModel.setMongoTemplate(defMongoTemplate);

//        jdbcModel.setQueryObject(loadQueryObject(dataSource, jdbcModel.getModelType() == JdbcModelType.mongo ? null : tableName, viewSql, def.getSchema()));
        /**
         * 加入JSON列的支持,目前先让属性和度量支持
         */
        if (def.getMeasures() != null) {
            for (JdbcMeasureDef measure : def.getMeasures()) {
                if (measure != null && StringUtils.isNotEmpty(measure.getColumn()) && measure.getColumn().indexOf("->") > 0) {
                    jdbcModel.getQueryObject().appendSqlColumn(measure.getColumn(), "OBJECT", 0);
                }
            }
        }
        if (def.getProperties() != null) {
            for (JdbcPropertyDef measure : def.getProperties()) {
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

        for (JdbcDimension dimension : context.getJdbcModel().getDimensions()) {
            QueryObject dqo = dimension.getQueryObject();
            if (dqo == null) {
                continue;
            }
            dqo.getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
            if (dimension.getDecorate(JdbcModelParentChildDimensionImpl.class) != null) {
                dimension.getDecorate(JdbcModelParentChildDimensionImpl.class).getClosureQueryObject().getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
            }

        }
    }

    private void loadDimensions(JdbcModelLoadContext context) {
        JdbcModelDef def = context.getDef();
        List<JdbcDimensionDef> dimensionDefList = def.getDimensions();
        if (dimensionDefList != null) {
            //加载在Model上定义的维度
            dimensionDefList = dimensionDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (JdbcDimensionDef dimensionDef : dimensionDefList) {
                JdbcDimension jdbcDimension = loadDimension(context, dimensionDef, true);
            }
        }

        if (def.isAutoLoadDimensions()) {
            //TODO 自动加载维度
        }

        /**
         * 初始化维度的相关列
         */
        for (JdbcDimension dimension : context.getJdbcModel().getDimensions()) {

            JdbcDimensionSupport ds = dimension.getDecorate(JdbcDimensionSupport.class);

            //初始化维度的相关列
            ds.init();
        }

    }


    private void loadProperties(JdbcModelLoadContext context) {
        JdbcModelDef def = context.getDef();
        List<JdbcPropertyDef> jdbcPropertyDefList = def.getProperties();
        if (jdbcPropertyDefList != null) {
            jdbcPropertyDefList = jdbcPropertyDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (JdbcPropertyDef propertyDef : jdbcPropertyDefList) {
                try {
                    JdbcProperty jdbcProperty = loadProperty(context, null, propertyDef);
                    context.getJdbcModel().addJdbcProperty(jdbcProperty);
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
        JdbcModelDef def = context.getDef();
        List<JdbcMeasureDef> measureDefList = def.getMeasures();
        if (measureDefList != null) {
            //加载在Model上定义的维度
            measureDefList = measureDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (JdbcMeasureDef measureDef : measureDefList) {
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

    private JdbcDimension loadDimension(JdbcModelLoadContext context, JdbcDimensionDef dimensionDef, boolean modelDim) {
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
    private JdbcDimension loadDimension(JdbcModelLoadContext context, JdbcDimensionDef dimensionDef, boolean modelDim, JdbcDimension parentDimension) {

        /**
         * 检查数据
         */
        if (context.getJdbcModel().findJdbcDimensionByName(dimensionDef.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateDimension(dimensionDef.getName()));
        }

        /**
         * 开始加载维度
         */
        JdbcDimensionSupport dimension = null;
        if (JdbcDimensionType.DATETIME == JdbcDimensionType.fromString(dimensionDef.getType())) {
            //时间维
            dimension = new JdbcModelTimeDimensionImpl();
        } else if (StringUtils.isNotEmpty(dimensionDef.getParentKey())) {
            //父子结构~
            JdbcModelParentChildDimensionImpl parentChildDimension = new JdbcModelParentChildDimensionImpl(dimensionDef.getParentKey(), dimensionDef.getChildKey(), dimensionDef.getClosureTableName());
            dimension = parentChildDimension;
            parentChildDimension.setClosureQueryObject(loadQueryObject(dimensionDef.getDataSource() == null ? dataSource : dimensionDef.getDataSource(), dimensionDef.getClosureTableName(), null, dimensionDef.getClosureTableSchema()));
            //childKey用来作为ClosureQueryObject的primaryKey与主表进行关联，注意，childKey实际上可不是主键
            parentChildDimension.getClosureQueryObject().getDecorate(QueryObjectSupport.class).setPrimaryKey(dimensionDef.getChildKey());
        } else {
            dimension = new JdbcModelDimensionImpl();
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
            for (JdbcPropertyDef propertyDef : dimensionDef.getProperties()) {
                JdbcProperty jdbcProperty = loadProperty(context, dimension, propertyDef);
                dimension.addJdbcProperty(jdbcProperty);
            }
        }

        processJdbcDataProvider(dimension.getDataProvider());

        JdbcDimension jdbcDimension = dimension;
        for (JdbcModelLoadProcessor processor : processors) {
            jdbcDimension = processor.processJdbcDimension(context, jdbcDimension);
        }
        if (modelDim) {
            context.getJdbcModel().addDimension(jdbcDimension);
        }

        // 递归加载嵌套子维度
        if (dimensionDef.getDimensions() != null && !dimensionDef.getDimensions().isEmpty()) {
            for (JdbcDimensionDef childDef : dimensionDef.getDimensions()) {
                JdbcDimension childDimension = loadDimension(context, childDef, true, jdbcDimension);
                jdbcDimension.addChildDimension(childDimension);
            }
        }

        return jdbcDimension;
    }

    private void processJdbcDataProvider(JdbcDataProvider dataProvider) {
        if (JdbcDimensionType.DICT == dataProvider.getDimensionType()) {
            RX.notNull(dataProvider.getExtData(), String.format("字典类型的维%s，必须有extData", dataProvider.getName()));

            String dictClass = dataProvider.getExtDataValue("dictClass");
            RX.hasText(dictClass, String.format("字典类型的维%s，必须有extData.dictClass", dataProvider.getName()));
            String dictName = dictClass.substring(dictClass.lastIndexOf(".") + 1);

            dataProvider.getExtData().put("dictName", dictName);
        }
    }

    private JdbcProperty loadProperty(JdbcModelLoadContext context, JdbcDimensionSupport dimension, JdbcPropertyDef propertyDef) {


        JdbcPropertyImpl property = new JdbcPropertyImpl();
        propertyDef.apply(property);
//        if (StringUtils.isNotEmpty(property.getType())) {
//            property.setType(property.getType());
//        }
        if (JdbcColumnType.DAY == property.getType()) {
            if (StringUtils.isEmpty(property.getFormat())) {
                property.setFormat("YYYY-MM-DD");
            }
        }

        property.setJdbcModel(context.getJdbcModel());
        property.setJdbcDimension(dimension);
        property.init();

        processJdbcDataProvider(property.getDataProvider());

        JdbcProperty jdbcProperty = property;

        if (propertyDef.getFormulaDef() != null && propertyDef.getFormulaDef().getBuilder() != null) {
            jdbcProperty.setFormulaBuilder(propertyDef.getFormulaDef().getBuilder());
        }
//        /**
//         * 加入维度支持
//         */
//        if(propertyDef.getDim()!=null){
//            JdbcDimension jdbcDimension = loadDimension(context, propertyDef.getDim(),true);
//
//        }


        for (JdbcModelLoadProcessor processor : processors) {
            jdbcProperty = processor.processJdbcProperty(context, jdbcProperty);
        }

        return jdbcProperty;
    }

    private void loadMeasure(JdbcModelLoadContext context, JdbcMeasureDef measureDef) {

        /**
         * 检查数据
         */
        if (context.getJdbcModel().findJdbcMeasureByName(measureDef.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateMeasure(measureDef.getName()));
        }

        /**
         * 开始加载维度
         */
        JdbcModelMeasureImpl measure = new JdbcModelMeasureImpl();
        measureDef.apply(measure);
//        if (StringUtils.isNotEmpty(measure.getType())) {
//            measure.setType(measure.getType().toUpperCase());
//        }
        if (StringUtils.isEmpty(measureDef.getAggregation()) && StringUtils.equalsIgnoreCase("money", measureDef.getType())) {
            //如果未定义Aggregation，且是money类型，默认sum
            measureDef.setAggregation("sum");
        }
        if (StringUtils.isNotEmpty(measureDef.getAggregation())) {
            measure.getDecorate(JdbcMeasureSupport.class).setAggregation(JdbcAggregation.valueOf(measureDef.getAggregation().toUpperCase()));
        }

        if (measureDef.getFormulaDef() != null && measureDef.getFormulaDef().getBuilder() != null) {
            measure.getDecorate(JdbcMeasureSupport.class).setFormulaBuilder(measureDef.getFormulaDef().getBuilder());
        }

        measure.getDecorate(JdbcMeasureSupport.class).init(context.getJdbcModel(), measureDef);

        JdbcMeasure jdbcMeasure = measure;
        for (JdbcModelLoadProcessor processor : processors) {
            jdbcMeasure = processor.processJdbcMeasure(context, jdbcMeasure);
        }

        context.getJdbcModel().addMeasure(jdbcMeasure);
//
//        //加载维表
//        dimension.setQueryObject(loadQueryObject(dimensionDef.getTableName(),dimensionDef.getViewSql()));

    }
}
