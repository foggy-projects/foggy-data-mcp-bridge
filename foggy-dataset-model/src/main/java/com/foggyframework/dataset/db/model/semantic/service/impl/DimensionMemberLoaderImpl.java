package com.foggyframework.dataset.db.model.semantic.service.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.db.model.common.result.DbDataItem;
import com.foggyframework.dataset.db.model.def.dict.DbDictDef;
import com.foggyframework.dataset.db.model.def.dict.DbDictItemDef;
import com.foggyframework.dataset.db.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.db.model.semantic.service.DimensionMemberLoader;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.model.PagingResultImpl;
import io.swagger.annotations.ApiModelProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 维度成员加载器实现
 * TODO: 实现具体的数据加载逻辑
 */
@Service
public class DimensionMemberLoaderImpl implements DimensionMemberLoader {

    private static final Logger logger = LoggerFactory.getLogger(DimensionMemberLoaderImpl.class);

    // 缓存已加载的维度成员数据
    private final Map<String, DimensionMembers> cache = new ConcurrentHashMap<>();

    @Resource
    JdbcService jdbcService;
    @Resource
    QueryModelLoader queryModelLoader;
    @Autowired(required = false)
    DbModelDictService dbModelDictService;


    /**
     * 由于同一个维度，会在多个模型中使用，如果按模型+维度来缓存，会造成大量的重复性数据，例如网点维度， 1->沆南网点，如果按模型+维度保存，由于维度维度在非常多模型中引用，这会造成大量的内存浪费，
     * 所以我们改为使用 维度的表名为缓存
     *
     * @param jdbcQueryModel
     * @param jdbcDimension
     */
    private List<DbDataItem> loadDimDataItem(QueryModel jdbcQueryModel, DbQueryDimension jdbcDimension) {


//构建查询维度用的查询条件
        PagingRequest<DimensionDataQueryForm> queryRequest = PagingRequest.buildPagingRequest(new DimensionDataQueryForm(jdbcQueryModel.getName(), jdbcDimension.getName()));
        queryRequest.setLimit(99999);
        //查询维度数据
        PagingResultImpl<DbDataItem> v = jdbcService.queryDimensionData(queryRequest);


        return v.getItems();
    }

    /**
     * 一个自定义的key前缀，为第三方扩展准备(比如saas系统，同一个维度，也需要根据租户ID来构建不同的缓存)
     *
     * @param jdbcDimension
     * @param cachePrefix
     * @return
     */
    private String buildCacheKey(DbQueryDimension jdbcDimension, String cachePrefix) {
        //搞到维度的表名
        TableQueryObject tableQueryObject = jdbcDimension.getDimension().getQueryObject().getDecorate(TableQueryObject.class);
        if (tableQueryObject == null) {
            throw new UnsupportedOperationException("目前只支持表格作为维度的成员加载");
        }
        return cachePrefix + "-" + tableQueryObject.getTableName().toUpperCase();
    }

    private String buildCacheKey(DbQueryProperty dbQueryProperty, String cachePrefix) {
        // 优先检查 dictRef（新的 fsscript 字典引用方式）
        String dictRef = dbQueryProperty.getJdbcProperty().getDictRef();
        if (!StringUtils.isEmpty(dictRef)) {
            return cachePrefix + "-dict-" + dictRef;
        }

        // 兼容旧的 dictClass 方式
        String dictClass = dbQueryProperty.getJdbcProperty().getExtDataValue("dictClass");
        if (StringUtils.isEmpty(dictClass)) {
            throw RX.throwA(String.format("只有字典类的属性，才能构建缓存,但%s不是", dbQueryProperty.getName()));
        }
        return cachePrefix + "-" + dictClass;
    }


    private List<DbDataItem> loadPropertyDataItem(QueryModel jdbcQueryModel, DbQueryProperty jdbcProperty) {
        // 优先检查 dictRef（新的 fsscript 字典引用方式）
        String dictRef = jdbcProperty.getJdbcProperty().getDictRef();
        if (!StringUtils.isEmpty(dictRef)) {
            return loadPropertyDataItemFromDictRef(dictRef);
        }

        // 兼容旧的 dictClass 方式
        String dictClass = jdbcProperty.getJdbcProperty().getExtDataValue("dictClass");
        return loadPropertyDataItemFromDictClass(dictClass);
    }

    /**
     * 从 fsscript 字典引用加载字典项
     */
    private List<DbDataItem> loadPropertyDataItemFromDictRef(String dictRef) {
        if (dbModelDictService == null) {
            throw RX.throwA("JdbcModelDictService 未注入，无法加载字典引用: " + dictRef);
        }

        DbDictDef dictDef = dbModelDictService.getDictById(dictRef);
        if (dictDef == null) {
            throw RX.throwA("字典未找到: " + dictRef + "，请确保已通过 registerDict 注册");
        }

        List<DbDataItem> result = new ArrayList<>();
        if (dictDef.getItems() != null) {
            for (DbDictItemDef item : dictDef.getItems()) {
                result.add(new DbDataItem(item.getValue(), item.getLabel()));
            }
        }
        return result;
    }

    /**
     * 从 Java 类加载字典项（兼容旧方式）
     */
    private List<DbDataItem> loadPropertyDataItemFromDictClass(String dictClass) {
        try {
            Class<?> cls = Class.forName(dictClass);
            List<DbDataItem> sb = new ArrayList<>();
            for (Field field : cls.getFields()) {
                if (BeanInfoHelper.isStaticField(field)) {
                    ApiModelProperty amp = field.getAnnotation(ApiModelProperty.class);
                    if (amp != null) {
                        Object v = field.get(null);
                        String caption = com.foggyframework.core.utils.StringUtils.isNotEmpty(amp.name()) ? amp.name() : amp.value();
                        sb.add(new DbDataItem(v, caption));
                    }
                }
            }
            return sb;
        } catch (ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 由于同一个维度/属性(带字典)，会在多个模型中使用，如果按模型+维度来缓存，会造成大量的重复性数据，例如网点维度， 1->沆南网点，如果按模型+维度保存，由于维度维度在非常多模型中引用，这会造成大量的内存浪费，
     * 所以我们改为使用 维度的表名为缓存
     */
    public DimensionMembers loadMembers(String model, String fieldName, Map<String, Object> context) {
        //从上下文得到缓存前缀
        String cachePrefix = (String) context.get("cachePrefix");
        cachePrefix = cachePrefix == null ? "" : cachePrefix;
        /**
         * 首先，我们要判断fieldName是维度还是属性,注意这里的fieldName是不带$caption 或$id后缀的~
         */
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(model);
        String cacheKey = null;
        DbQueryDimension jdbcDimension = jdbcQueryModel.findQueryDimension(fieldName, false);
        if (jdbcDimension != null) {
            //进入给度处理逻辑
            cacheKey = buildCacheKey(jdbcDimension, cachePrefix);
        }
        DbQueryProperty jdbcProperty = jdbcQueryModel.findQueryProperty(fieldName, false);
        if (jdbcProperty != null) {
            cacheKey = buildCacheKey(jdbcProperty, cachePrefix);
        }

        if (cacheKey == null) {
            throw new UnsupportedOperationException("目前只有维度和属性可以加载成员");
        }

        // 检查缓存
        DimensionMembers cached = cache.get(cacheKey);
        if (cached != null && !isExpired(cached, model)) {
            logger.debug("使用缓存的成员数据: {}", cacheKey);
            return cached;
        }
        if (cached == null) {
            cached = DimensionMembers.of();
            cache.put(cacheKey, cached);
        }

        if (jdbcDimension != null) {
            List<DbDataItem> loadDimDataItem = loadDimDataItem(jdbcQueryModel, jdbcDimension);
            //俣计
            cached.merge(loadDimDataItem);
        } else if (jdbcProperty != null) {
            List<DbDataItem> loadDimDataItem = loadPropertyDataItem(jdbcQueryModel, jdbcProperty);
            cached.merge(loadDimDataItem);
        }
        //写入缓存时间
        cached.getModel2LoadAt().put(model, System.currentTimeMillis());

        return cached;
    }

    @Override
    public DimensionMembers loadDimensionMembers(String model, String fieldName, Map<String, Object> context) {
        // 去除$caption或$id后缀，获取基础字段名
        String baseFieldName = fieldName;
        if (fieldName.endsWith("$caption") || fieldName.endsWith("$id")) {
            baseFieldName = fieldName.substring(0, fieldName.lastIndexOf('$'));
        }
        
        // 调用新的loadMembers方法
        return loadMembers(model, baseFieldName, context);
    }

    @Override
    public Map<String, DimensionMembers> loadMultipleDimensions(String model, List<String> fieldNames, Map<String, Object> context) {
        Map<String, DimensionMembers> result = new HashMap<>();
        for (String fieldName : fieldNames) {
            result.put(fieldName, loadDimensionMembers(model, fieldName, context));
        }
        return result;
    }

    @Override
    public Object findIdByCaption(DimensionMembers members, Object caption) {
        if (members == null || caption == null) {
            return null;
        }

        // 处理批量查询（in条件）
        if (caption instanceof List) {
            List<?> captions = (List<?>) caption;
            List<Object> ids = new ArrayList<>();
            for (Object c : captions) {
                Object id = members.getCaptionToIdMap().get(c);
                if (id != null) {
                    ids.add(id);
                } else {
                    logger.warn("找不到caption对应的id: {} = {}", members.getTableName(), c);
                }
            }
            return ids;
        }

        // 单值查询
        return members.getCaptionToIdMap().get(caption);
    }

    @Override
    public Object findCaptionById(DimensionMembers members, Object id) {
        if (members == null || id == null) {
            return null;
        }
        return members.getIdToCaptionMap().get(id);
    }

    @Override
    public List<MemberItem> searchByCaption(DimensionMembers members, String pattern, int limit) {
        if (members == null || pattern == null) {
            return new ArrayList<>();
        }

        String searchPattern = pattern.toLowerCase();
        boolean isPrefix = searchPattern.endsWith("%");
        boolean isSuffix = searchPattern.startsWith("%");

        if (isPrefix) {
            searchPattern = searchPattern.substring(0, searchPattern.length() - 1);
        }
        if (isSuffix) {
            searchPattern = searchPattern.substring(1);
        }

        final String finalPattern = searchPattern;

        return members.getAllMembers().stream()
                .filter(item -> {
                    String caption = String.valueOf(item.getCaption()).toLowerCase();
                    if (isPrefix && isSuffix) {
                        return caption.contains(finalPattern);
                    } else if (isPrefix) {
                        return caption.startsWith(finalPattern);
                    } else if (isSuffix) {
                        return caption.endsWith(finalPattern);
                    } else {
                        return caption.equals(finalPattern);
                    }
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String buildCacheKey(String model, String fieldName) {
        return model + ":" + fieldName;
    }

    private boolean isExpired(DimensionMembers members, String model) {
        // 缓存过期时间：50分钟
        long expirationTime = 50 * 60 * 1000;
        
        // 根据members.model2LoadAt + model判断该模型是否过期
        Long loadTime = members.getModel2LoadAt().get(model);
        if (loadTime == null) {
            // 该模型还没有被加载过，需要加载
            return true;
        }
        
        // 检查是否超过过期时间
        return System.currentTimeMillis() - loadTime > expirationTime;
    }

}