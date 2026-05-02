package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.path.DimensionPath;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Pivot Properties 函数依赖验证器
 *
 * <p>验证 {@code pivot.properties} 中的每个属性字段是否满足函数依赖条件：
 * 即对应维度的主键（{@code $id}）已出现在行轴或列轴中。</p>
 *
 * <p>只有满足 PK → property 唯一性保证的属性才允许贴合，
 * 否则直接拒绝并给出 LLM 友好的纠正建议。</p>
 */
public class PropertyResolver {

    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);

    /**
     * 已验证的属性描述
     */
    public static class ResolvedProperty {
        private final String dimensionName;
        private final String propertyName;
        private final String lookupKeyField; // 轴上用于 lookup 的字段名, e.g. "product$id"
        private final String fullFieldName;  // 完整字段名, e.g. "product$categoryName"

        public ResolvedProperty(String dimensionName, String propertyName,
                                String lookupKeyField, String fullFieldName) {
            this.dimensionName = dimensionName;
            this.propertyName = propertyName;
            this.lookupKeyField = lookupKeyField;
            this.fullFieldName = fullFieldName;
        }

        public String getDimensionName() { return dimensionName; }
        public String getPropertyName() { return propertyName; }
        public String getLookupKeyField() { return lookupKeyField; }
        public String getFullFieldName() { return fullFieldName; }

        @Override
        public String toString() {
            return fullFieldName + " (dim=" + dimensionName + ", key=" + lookupKeyField + ")";
        }
    }

    /**
     * 验证并解析 properties 列表
     *
     * @param queryModel   查询模型
     * @param properties   用户请求的 properties 字段列表
     * @param axisFields   所有轴字段名集合 (rowFields ∪ colFields)
     * @return 已验证的 ResolvedProperty 列表
     * @throws IllegalArgumentException 当某个 property 无法证明函数依赖时
     */
    public static List<ResolvedProperty> resolve(QueryModel queryModel,
                                                   List<String> properties,
                                                   Set<String> axisFields) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }

        List<ResolvedProperty> resolved = new ArrayList<>();

        for (String prop : properties) {
            DimensionPath path = DimensionPath.parse(prop);

            if (!path.hasColumnName() || path.isEmpty()) {
                throw new IllegalArgumentException(
                        "property '" + prop + "' 格式不合法。"
                                + "请使用 '维度名$属性名' 格式，例如 'product$categoryName'");
            }

            String dimName = path.first();
            String propName = path.getColumnName();

            // 验证维度存在
            DbDimension dimension = queryModel.findDimension(dimName);
            if (dimension == null) {
                throw new IllegalArgumentException(
                        "property '" + prop + "' 引用了不存在的维度 '" + dimName + "'。"
                                + "请检查模型中是否有此维度定义");
            }

            // 验证属性存在
            DbProperty dbProperty = dimension.findPropertyByName(propName);
            if (dbProperty == null) {
                throw new IllegalArgumentException(
                        "property '" + prop + "' 在维度 '" + dimName + "' 中找不到名为 '"
                                + propName + "' 的属性。请检查维度的 properties 定义");
            }

            // 函数依赖验证：轴上必须包含该维度的 $id 字段
            String idField = dimName + "$id";
            if (!axisFields.contains(idField)) {
                // 尝试匹配其他可能的主键引用格式
                boolean foundKey = false;
                for (String axisField : axisFields) {
                    DimensionPath axisPath = DimensionPath.parse(axisField);
                    if (dimName.equals(axisPath.first())
                            && ("id".equals(axisPath.getColumnName())
                            || "key".equals(axisPath.getColumnName()))) {
                        idField = axisField;
                        foundKey = true;
                        break;
                    }
                }

                if (!foundKey) {
                    throw new IllegalArgumentException(
                            "property '" + prop + "' 无法证明函数依赖：轴上缺少维度 '"
                                    + dimName + "' 的主键字段。"
                                    + "请将 '" + dimName + "$id' 加入 rows 或 columns 后重试，"
                                    + "以保证属性值的唯一性");
                }
            }

            logger.debug("[PropertyResolver] 已验证: {} → lookup by {}", prop, idField);
            resolved.add(new ResolvedProperty(dimName, propName, idField, prop));
        }

        return resolved;
    }
}
