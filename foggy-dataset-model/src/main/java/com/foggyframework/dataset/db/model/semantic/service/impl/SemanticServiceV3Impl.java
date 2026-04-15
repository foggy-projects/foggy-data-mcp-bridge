package com.foggyframework.dataset.db.model.semantic.service.impl;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.dataset.db.model.def.dict.DbDictDef;
import com.foggyframework.dataset.db.model.def.dict.DbDictItemDef;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.*;
import io.swagger.annotations.ApiModelProperty;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

/**
 * V3版本语义服务实现
 *
 * <p>核心变化：将维度字段展开为独立的 $id 和 $caption 字段</p>
 *
 * <p>例如：维度 salesDate 会展开为两个独立字段：</p>
 * <ul>
 *   <li>salesDate$id - 可以有独立的描述（如：格式 yyyymmdd）</li>
 *   <li>salesDate$caption - 可以有独立的描述（如：格式 yyyy年mm月dd日）</li>
 * </ul>
 */
@Slf4j
@Service
public class SemanticServiceV3Impl implements SemanticServiceV3 {

    @Resource
    private QueryModelLoader queryModelLoader;

    @Autowired(required = false)
    private DbModelDictService dbModelDictService;

    @Override
    public SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format,
                                                SemanticRequestContext context) {
        String namespace = context.getNamespace();
        Set<String> fieldAccess = context.getFieldAccess();
        try {
            // 设置namespace到ThreadLocal（供模型加载使用）
            if (namespace != null) {
                NamespaceContext.setNamespace(namespace);
            }

            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setFormat(format);

            if ("json".equalsIgnoreCase(format)) {
                Map<String, Object> data = buildJsonMetadata(request, namespace, fieldAccess);
                response.setData(data);
                response.setContent(null);
            } else {
                String markdownContent = buildMarkdownMetadata(request, namespace, fieldAccess);
                response.setContent(markdownContent);
                response.setData(null);
            }

            return response;
        } finally {
            // 清理namespace ThreadLocal
            if (namespace != null) {
                NamespaceContext.clear();
            }
        }
    }

    /**
     * 构建JSON格式的元数据（V3版本：维度展开）
     *
     * @param fieldAccess 运行时列权限白名单（null 表示不限制）
     */
    private Map<String, Object> buildJsonMetadata(SemanticMetadataRequest request, String namespace,
                                                   Set<String> fieldAccess) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("prompt", buildPrompt());
        data.put("version", "v3");

        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, Object> models = new LinkedHashMap<>();

        for (String qmModelName : request.getQmModels()) {
            try {
                QueryModel queryModel = queryModelLoader.getJdbcQueryModel(qmModelName, namespace);
                if (queryModel == null) {
                    log.warn("metadata 构建跳过模型 '{}': 模型不存在或加载返回 null", qmModelName);
                    continue;
                }

                // 处理字段信息（展开维度字段，按列权限裁剪）
                processModelFieldsV3(queryModel, fields, request.getFields(), request.getLevels(), fieldAccess);

                // 处理模型信息
                processModelInfo(queryModel, models);
            } catch (Exception e) {
                // 单模型加载失败不拖垮整包 metadata
                log.warn("metadata 构建跳过模型 '{}': {}", qmModelName, e.getMessage());
            }
        }

        data.put("fields", fields);
        data.put("models", models);

        return data;
    }

    private String buildPrompt() {
        StringBuilder md = new StringBuilder();
        md.append("## 使用说明 (V3版本)\n");
//        md.append("- 所有字段直接使用字段名，无需判断是否需要后缀\n");
//        md.append("- 维度字段已展开为独立的 $id 和 $caption 字段\n");
        md.append("- 格式约定：字段类型 | 数据类型 | 格式说明\n");
        return md.toString();
    }

    /**
     * 构建Markdown格式的元数据（V3版本）
     *
     * <p>根据请求的模型数量选择不同的输出格式：</p>
     * <ul>
     *   <li>单模型：详细格式，包含完整字段信息表格</li>
     *   <li>多模型：精简索引格式，按业务含义分组</li>
     * </ul>
     */
    private String buildMarkdownMetadata(SemanticMetadataRequest request, String namespace,
                                         Set<String> fieldAccess) {
        List<String> qmModels = request.getQmModels();

        // 单模型：使用详细格式
        if (qmModels != null && qmModels.size() == 1) {
            return buildSingleModelMarkdown(qmModels.get(0), request, namespace, fieldAccess);
        }

        // 多模型：使用精简索引格式
        return buildMultiModelMarkdown(request, namespace, fieldAccess);
    }

    /**
     * 构建单模型详细描述（用于 DescriptionModelTool）
     *
     * <p>包含完整的模型信息：</p>
     * <ul>
     *   <li>模型基本信息：表名、主键、说明</li>
     *   <li>维度字段表格：字段名、名称、类型、说明</li>
     *   <li>属性字段表格</li>
     *   <li>度量字段表格</li>
     *   <li>字典定义</li>
     * </ul>
     */
    private String buildSingleModelMarkdown(String modelName, SemanticMetadataRequest request, String namespace,
                                              Set<String> fieldAccess) {
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(modelName, namespace);
        if (queryModel == null) {
            return "# 错误\n\n模型不存在: " + modelName;
        }

        TableModel jdbcModel = queryModel.getJdbcModel();
        StringBuilder md = new StringBuilder();

        // 收集字典引用
        Set<String> referencedDictIds = new LinkedHashSet<>();
        Set<DictInfo> referencedDictClasses = new LinkedHashSet<>();
        // 收集维度字段名，用于在属性字段中排除
        Set<String> dimensionFieldNames = new HashSet<>();

        String caption = queryModel.getCaption() != null ? queryModel.getCaption() : modelName;
        md.append("# ").append(modelName).append(" - ").append(caption).append("\n\n");

        // ========== 模型信息 ==========
        md.append("## 模型信息\n");
        md.append("- 表名: ").append(jdbcModel.getTableName()).append("\n");
        // 只为非jdbc模型添加类型标识（jdbc是默认类型，不需要标注以节省token）
        DbModelType modelType = jdbcModel.getModelType();
        if (modelType != null && modelType != DbModelType.jdbc) {
            md.append("- 类型: ").append(modelType.name()).append("\n");
        }
        if (jdbcModel.getIdColumn() != null) {
            md.append("- 主键: ").append(jdbcModel.getIdColumn()).append("\n");
        }
        if (queryModel.getDescription() != null) {
            md.append("- 说明: ").append(queryModel.getDescription()).append("\n");
        }
        md.append("\n");

        // ========== 维度字段 ==========
        List<DbDimension> dimensions = jdbcModel.getDimensions();
        if (dimensions != null && !dimensions.isEmpty()) {
            md.append("## 维度字段\n");
            md.append("| 字段名 | 名称 | 类型 | 层级 | 说明 |\n");
            md.append("|--------|------|------|------|------|\n");

            for (DbDimension dimension : dimensions) {
                if (!isFieldInLevels(dimension.getAi(), request.getLevels())) {
                    continue;
                }
                String dimName = dimension.getEffectiveName();

                // 检查 QM 是否暴露了该维度（$id 或 $caption 至少有一个在 QM columnGroups 中）
                if (queryModel.findJdbcQueryColumnByName(dimName + "$id", false) == null
                        && queryModel.findJdbcQueryColumnByName(dimName + "$caption", false) == null) {
                    continue;
                }

                // fieldAccess 列权限裁剪
                if (fieldAccess != null && !fieldAccess.contains(dimName)) {
                    continue;
                }

                String dimCaption = dimension.getCaption() != null ? dimension.getCaption() : dimName;
                boolean isHier = isHierarchicalDimension(dimension);
                String hierLabel = isHier ? "✅ selfAndDescendantsOf / selfAndAncestorsOf" : "-";

                // $id 字段
                String idFieldName = dimName + "$id";
                dimensionFieldNames.add(idFieldName);
                String idDesc = escapeMarkdownTable(
                        dimension.getKeyDescription() != null ? dimension.getKeyDescription() : "");
                md.append("| ").append(idFieldName)
                        .append(" | ").append(dimCaption).append("(ID)")
                        .append(" | ").append(getIdTypeDescription(dimension))
                        .append(" | ").append(hierLabel)
                        .append(" | ").append(idDesc)
                        .append(" |\n");

                // $caption 字段
                String captionFieldName = dimName + "$caption";
                dimensionFieldNames.add(captionFieldName);
                md.append("| ").append(captionFieldName)
                        .append(" | ").append(dimCaption).append("(名称)")
                        .append(" | TEXT")
                        .append(" | -")
                        .append(" | ").append(dimCaption).append("显示名称")
                        .append(" |\n");

                // 维度属性（仅输出QM暴露的属性）
                if (dimension instanceof DbDimensionSupport) {
                    for (DbProperty prop : ((DbDimensionSupport) dimension).getJdbcProperties()) {
                        // 先尝试使用默认格式查找
                        String defaultPropFieldName = dimName + "$" + prop.getName();
                        DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(defaultPropFieldName, false);

                        if (queryColumn == null) {
                            continue;
                        }

                        if (!isFieldInLevels(prop.getAi(), request.getLevels())) {
                            continue;
                        }

                        // 使用 QM 中定义的列名（name），而不是默认格式
                        // 这样可以支持用户在 QM 中使用 alias 重命名字段
                        String propFieldName = queryColumn.getName();
                        dimensionFieldNames.add(propFieldName);
                        String propCaption = prop.getCaption() != null ? prop.getCaption() : prop.getName();
                        String propType = getDataTypeDescription(prop.getPropertyDbColumn().getType());
                        String propDesc = prop.getDescription() != null ? prop.getDescription() : "";

                        // 处理字典引用
                        String dictRef = prop.getDictRef();
                        if (StringUtils.isNotEmpty(dictRef)) {
                            referencedDictIds.add(dictRef);
                            propDesc = propDesc + " (字典:" + dictRef + ")";
                        } else if (StringUtils.equals(prop.getPropertyDbColumn().getType(), "DICT")) {
                            String dictClass = prop.getExtDataValue("dictClass");
                            if (StringUtils.isNotEmpty(dictClass)) {
                                String[] names = dictClass.split("\\.");
                                String name = names[names.length - 1];
                                referencedDictClasses.add(new DictInfo(name, dictClass));
                                propDesc = propDesc + " (字典:" + name + ")";
                            }
                        }

                        md.append("| ").append(propFieldName)
                                .append(" | ").append(propCaption)
                                .append(" | ").append(propType)
                                .append(" | -")
                                .append(" | ").append(escapeMarkdownTable(propDesc))
                                .append(" |\n");
                    }
                }
            }
            md.append("\n");
        }

        // ========== 属性字段 ==========
        List<DbQueryProperty> queryProperties = queryModel.getQueryProperties();
        if (queryProperties != null && !queryProperties.isEmpty()) {
            // 过滤掉已在维度字段中输出的属性
            // 注意：使用 qp.getName() 而不是 qp.getProperty().getName()，因为 dimensionFieldNames 中存储的是 QM 中定义的列名
            List<DbQueryProperty> filteredProperties = queryProperties.stream()
                    .filter(qp -> !dimensionFieldNames.contains(qp.getName()))
                    .toList();

            if (!filteredProperties.isEmpty()) {
                md.append("## 属性字段\n");
                md.append("| 字段名 | 名称 | 类型 | 说明 |\n");
                md.append("|--------|------|------|------|\n");

                for (DbQueryProperty queryProperty : filteredProperties) {
                    if (!isFieldInLevels(queryProperty.getAi(), request.getLevels())) {
                        continue;
                    }
                    DbProperty property = queryProperty.getProperty();
                    String fieldName = property.getName();

                    // fieldAccess 列权限裁剪
                    if (fieldAccess != null && !fieldAccess.contains(fieldName)) {
                        continue;
                    }
                    String fieldCaption = property.getCaption() != null ? property.getCaption() : fieldName;
                    String fieldType = getDataTypeDescription(property.getPropertyDbColumn().getType());
                    String fieldDesc = property.getDescription() != null ? property.getDescription() : "";

                    // 处理字典引用
                    String dictRef = property.getDictRef();
                    if (StringUtils.isNotEmpty(dictRef)) {
                        referencedDictIds.add(dictRef);
                        fieldDesc = fieldDesc + " (字典:" + dictRef + ")";
                    } else if (StringUtils.equals(property.getPropertyDbColumn().getType(), "DICT")) {
                        String dictClass = property.getExtDataValue("dictClass");
                        if (StringUtils.isNotEmpty(dictClass)) {
                            String[] names = dictClass.split("\\.");
                            String name = names[names.length - 1];
                            referencedDictClasses.add(new DictInfo(name, dictClass));
                            fieldDesc = fieldDesc + " (字典:" + name + ")";
                        }
                    }

                    md.append("| ").append(fieldName)
                            .append(" | ").append(fieldCaption)
                            .append(" | ").append(fieldType)
                            .append(" | ").append(escapeMarkdownTable(fieldDesc))
                            .append(" |\n");
                }
                md.append("\n");
            }
        }

        // ========== 度量字段 ==========
        List<DbMeasure> measures = jdbcModel.getMeasures();
        if (measures != null && !measures.isEmpty()) {
            md.append("## 度量字段\n");
            md.append("| 字段名 | 名称 | 类型 | 聚合 | 说明 |\n");
            md.append("|--------|------|------|------|------|\n");

            for (DbMeasure measure : measures) {
                if (!isFieldInLevels(measure.getAi(), request.getLevels())) {
                    continue;
                }
                String fieldName = measure.getName();

                // fieldAccess 列权限裁剪
                if (fieldAccess != null && !fieldAccess.contains(fieldName)) {
                    continue;
                }

                String fieldCaption = measure.getCaption() != null ? measure.getCaption() : fieldName;
                String fieldType = getDataTypeDescription(measure.getJdbcColumn().getType());
                String aggregation = measure.getAggregation() != null ? measure.getAggregation().name() : "SUM";
                String fieldDesc = measure.getDescription() != null ? measure.getDescription() : "";

                md.append("| ").append(fieldName)
                        .append(" | ").append(fieldCaption)
                        .append(" | ").append(fieldType)
                        .append(" | ").append(aggregation)
                        .append(" | ").append(escapeMarkdownTable(fieldDesc))
                        .append(" |\n");
            }
            md.append("\n");
        }

        // ========== 字典定义 ==========
        if (!referencedDictIds.isEmpty() || !referencedDictClasses.isEmpty()) {
            md.append("## 字典定义\n");
            md.append("| ID | 名称 | 取值 |\n");
            md.append("|----|------|------|\n");

            for (String dictId : referencedDictIds) {
                if (dbModelDictService != null) {
                    DbDictDef dictDef = dbModelDictService.getDictById(dictId);
                    if (dictDef != null) {
                        String dictCaption = dictDef.getCaption() != null ? dictDef.getCaption() : dictId;
                        String itemsSummary = dictDef.getItemsSummary();
                        md.append("| ").append(dictId)
                                .append(" | ").append(dictCaption)
                                .append(" | ").append(itemsSummary)
                                .append(" |\n");
                    }
                }
            }

            for (DictInfo dictInfo : referencedDictClasses) {
                String itemsSummary = buildDictItemsSummary(dictInfo.getDictClass());
                md.append("| ").append(dictInfo.getName())
                        .append(" | ").append(dictInfo.getName())
                        .append(" | ").append(itemsSummary)
                        .append(" |\n");
            }
            md.append("\n");
        }

        // ========== 使用提示 ==========
        md.append("## 使用提示\n");
        md.append("- 维度用 `xxx$id`(查询/过滤), `xxx$caption`(展示), `xxx$property`(维度属性)\n");
        md.append("- 度量支持内联聚合: `sum(salesAmount) as total`\n");
        md.append("- 系统自动处理 groupBy，通常无需手动指定\n");
        md.append("- 层级维度支持 `selfAndDescendantsOf`(值及其所有下级) 和 `selfAndAncestorsOf`(值及其所有上级) 操作符\n");

        return md.toString();
    }

    /**
     * 转义 Markdown 表格中的特殊字符
     */
    private String escapeMarkdownTable(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replace("\n", " ");
    }

    /**
     * 构建多模型精简索引（用于 MetadataTool）
     *
     * <p>采用精简结构：</p>
     * <ol>
     *   <li>模型索引：简称 + 模型名 + 说明</li>
     *   <li>字段索引：按业务含义分组，用模型简称代替长名称</li>
     *   <li>字典定义：被引用的字典ID及取值</li>
     * </ol>
     */
    private String buildMultiModelMarkdown(SemanticMetadataRequest request, String namespace,
                                           Set<String> fieldAccess) {
        StringBuilder md = new StringBuilder();

        md.append("# 数据模型语义索引 V3\n\n");

        // 收集字段信息
        Map<String, FieldInfoV3> allFields = new LinkedHashMap<>();
        Map<String, QueryModel> modelMap = new LinkedHashMap<>();
        // 收集被引用的字典（包括 fsscript 字典和 Java 类字典）
        Set<String> referencedDictIds = new LinkedHashSet<>();
        Set<DictInfo> referencedDictClasses = new LinkedHashSet<>();

        for (String qmModelName : request.getQmModels()) {
            QueryModel queryModel = queryModelLoader.getJdbcQueryModel(qmModelName, namespace);
            if (queryModel == null) {
                continue;
            }
            modelMap.put(qmModelName, queryModel);
            collectFieldsInfoV3(queryModel, allFields, request.getFields(), request.getLevels(),
                    referencedDictIds, referencedDictClasses, fieldAccess);
        }

        // 构建模型简称映射（使用 JdbcQueryModel 的 shortAlias）
        Map<String, String> modelAliasMap = new LinkedHashMap<>();
        for (Map.Entry<String, QueryModel> entry : modelMap.entrySet()) {
            String modelName = entry.getKey();
            QueryModel queryModel = entry.getValue();
            String shortAlias = queryModel.getShortAlias();
            // 如果没有简称（可能是老版本），使用模型名前缀作为fallback
            if (shortAlias == null || shortAlias.isEmpty()) {
                shortAlias = extractFallbackAlias(modelName);
            }
            modelAliasMap.put(modelName, shortAlias);
        }

        // ========== 模型索引 ==========
        md.append("## 模型索引\n");
        for (Map.Entry<String, String> entry : modelAliasMap.entrySet()) {
            String modelName = entry.getKey();
            String alias = entry.getValue();
            QueryModel queryModel = modelMap.get(modelName);
            String caption = queryModel.getCaption() != null ? queryModel.getCaption() : modelName;
            // 只为非jdbc模型添加类型标识（jdbc是默认类型，不需要标注以节省token）
            TableModel jdbcModel = queryModel.getJdbcModel();
            DbModelType modelType = jdbcModel.getModelType();
            String typeTag = (modelType != null && modelType != DbModelType.jdbc)
                    ? " [" + modelType.name() + "]" : "";
            // 格式: 简称(模型名) [类型]: 说明
            md.append("- ").append(alias).append("(").append(modelName).append(")").append(typeTag).append(": ").append(caption).append("\n");
        }
        md.append("\n");

        // ========== 字典定义（放在字段索引前面）==========
        if (!referencedDictIds.isEmpty() || !referencedDictClasses.isEmpty()) {
            md.append("## 字典定义\n");
            md.append("| ID | 名称 | 取值 |\n");
            md.append("|----|------|------|\n");

            // 输出 fsscript 字典
            for (String dictId : referencedDictIds) {
                if (dbModelDictService != null) {
                    DbDictDef dictDef = dbModelDictService.getDictById(dictId);
                    if (dictDef != null) {
                        String caption = dictDef.getCaption() != null ? dictDef.getCaption() : dictId;
                        String itemsSummary = dictDef.getItemsSummary();
                        md.append("| ").append(dictId)
                                .append(" | ").append(caption)
                                .append(" | ").append(itemsSummary)
                                .append(" |\n");
                    }
                }
            }

            // 输出 Java 类字典（兼容旧方式）
            for (DictInfo dictInfo : referencedDictClasses) {
                String itemsSummary = buildDictItemsSummary(dictInfo.getDictClass());
                md.append("| ").append(dictInfo.getName())
                        .append(" | ").append(dictInfo.getName())
                        .append(" | ").append(itemsSummary)
                        .append(" |\n");
            }
            md.append("\n");
        }

        // ========== 字段索引（按业务含义分组）==========
        md.append("## 字段索引\n\n");

        md.append("## 索引格式\n");
        md.append("```\n");
        md.append("### 字段业务名\n");
        md.append("- 描述\n");
        md.append("    - 实际字段名 | 模型索引\n");
        md.append("```\n");
        md.append("**重要**: 查询时必须使用缩进行中的「实际字段名」(全小写)，而非标题中的业务名。\n\n");

        // 按 displayName（业务含义）分组
        Map<String, List<FieldEntry>> groupedByDisplayName = new LinkedHashMap<>();
        for (Map.Entry<String, FieldInfoV3> entry : allFields.entrySet()) {
            String fieldName = entry.getKey();
            FieldInfoV3 fieldInfo = entry.getValue();

            // 提取基础业务名称（去掉 (ID)/(名称) 后缀）
            String groupName = extractGroupName(fieldInfo.getDisplayName());

            groupedByDisplayName.computeIfAbsent(groupName, k -> new ArrayList<>())
                    .add(new FieldEntry(fieldName, fieldInfo));
        }

        // 生成字段索引（三层结构：业务含义 → 描述 → 字段+模型）
        for (Map.Entry<String, List<FieldEntry>> group : groupedByDisplayName.entrySet()) {
            String groupName = group.getKey();
            List<FieldEntry> fields = group.getValue();

            md.append("### ").append(groupName).append("\n");

            // 按 description 二次分组
            // Key: description, Value: Map<fieldName, List<modelAlias>>
            Map<String, Map<String, List<String>>> descToFieldsMap = new LinkedHashMap<>();

            for (FieldEntry fe : fields) {
                for (Map.Entry<String, FieldInfoV3.ModelUsage> usage : fe.fieldInfo.getModelUsages().entrySet()) {
                    String modelName = usage.getKey();
                    String desc = usage.getValue().getDescription();
                    // 简化描述
                    desc = simplifyDescription(desc, fe.fieldName);
                    // 如果描述为空或无意义，用 displayName 兜底
                    if (desc == null || desc.isEmpty() || desc.equals(fe.fieldName)) {
                        desc = fe.fieldInfo.getDisplayName();
                        if (desc == null || desc.isEmpty()) {
                            desc = groupName;
                        }
                    }

                    // 如果字段有字典引用，添加标注
                    String dictRef = usage.getValue().getDictRef();
                    if (StringUtils.isNotEmpty(dictRef)) {
                        desc = desc + " (字典:" + dictRef + ")";
                    } else if (usage.getValue().getDictInfo() != null) {
                        desc = desc + " (字典:" + usage.getValue().getDictInfo().getName() + ")";
                    }

                    String modelAlias = modelAliasMap.get(modelName);
                    if (modelAlias != null) {
                        descToFieldsMap
                                .computeIfAbsent(desc, k -> new LinkedHashMap<>())
                                .computeIfAbsent(fe.fieldName, k -> new ArrayList<>())
                                .add(modelAlias);
                    }
                }
            }

            // 输出三层结构
            for (Map.Entry<String, Map<String, List<String>>> descEntry : descToFieldsMap.entrySet()) {
                String desc = descEntry.getKey();
                Map<String, List<String>> fieldsMap = descEntry.getValue();

                md.append("- ").append(desc).append("\n");

                for (Map.Entry<String, List<String>> fieldEntry : fieldsMap.entrySet()) {
                    String fieldName = fieldEntry.getKey();
                    List<String> aliases = fieldEntry.getValue();
                    Collections.sort(aliases);
                    String aliasStr = String.join(",", aliases);
                    // 使用 4 空格缩进和 [field:] 标记，强调这是实际字段名
                    md.append("    - [field:").append(fieldName).append("] | ").append(aliasStr).append("\n");
                }
            }
            md.append("\n");
        }

        return md.toString();
    }

    /**
     * 构建 Java 类字典的取值摘要
     */
    private String buildDictItemsSummary(String dictClass) {
        try {
            Class<?> cls = Class.forName(dictClass);
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Field field : cls.getFields()) {
                if (BeanInfoHelper.isStaticField(field)) {
                    ApiModelProperty amp = field.getAnnotation(ApiModelProperty.class);
                    if (amp != null) {
                        if (!first) {
                            sb.append(", ");
                        }
                        Object v = field.get(null);
                        String caption = StringUtils.isNotEmpty(amp.name()) ? amp.name() : amp.value();
                        sb.append(v).append("=").append(caption);
                        first = false;
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 生成fallback简称（用于没有分配shortAlias的旧模型）
     */
    private String extractFallbackAlias(String modelName) {
        // 去掉 QueryModel 后缀
        String baseName = modelName;
        if (baseName.endsWith("QueryModel")) {
            baseName = baseName.substring(0, baseName.length() - "QueryModel".length());
        } else if (baseName.endsWith("Model")) {
            baseName = baseName.substring(0, baseName.length() - "Model".length());
        }

        // 提取驼峰词首字母
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < baseName.length(); i++) {
            char c = baseName.charAt(i);
            if (Character.isUpperCase(c)) {
                initials.append(c);
            }
        }

        return initials.length() > 0 ? initials.toString() : baseName.substring(0, Math.min(2, baseName.length())).toUpperCase();
    }

    /**
     * 提取分组名称（去掉 (ID)/(名称) 等后缀）
     */
    private String extractGroupName(String displayName) {
        if (displayName == null) return "其他";
        return displayName
                .replace("(ID)", "")
                .replace("(名称)", "")
                .replace("（ID）", "")
                .replace("（名称）", "")
                .trim();
    }

    /**
     * 简化描述（去掉字段名前缀）
     */
    private String simplifyDescription(String desc, String fieldName) {
        if (desc == null) return "";
        // 去掉 "fieldName | " 前缀
        if (desc.startsWith(fieldName + " | ")) {
            desc = desc.substring(fieldName.length() + 3);
        }
        // 去掉 "fieldName$xxx | " 前缀
        int pipeIndex = desc.indexOf(" | ");
        if (pipeIndex > 0 && desc.substring(0, pipeIndex).contains("$")) {
            desc = desc.substring(pipeIndex + 3);
        }
        return desc;
    }

    /**
     * 字段条目（用于分组）
     */
    private static class FieldEntry {
        String fieldName;
        FieldInfoV3 fieldInfo;

        FieldEntry(String fieldName, FieldInfoV3 fieldInfo) {
            this.fieldName = fieldName;
            this.fieldInfo = fieldInfo;
        }
    }

    /**
     * 处理模型字段（V3版本：展开维度）
     *
     * @param fieldAccess 运行时列权限白名单（null 表示不限制）
     */
    private void processModelFieldsV3(QueryModel queryModel, Map<String, Object> fields,
                                      List<String> fieldFilter, List<Integer> levels,
                                      Set<String> fieldAccess) {
        TableModel jdbcModel = queryModel.getJdbcModel();

        // 收集维度字段名，用于在属性字段中排除（与 markdown 方法对齐）
        // 避免同一个 sourceColumn 出现重复映射（如 company$id 和 company 都映射到 company_id），
        // 导致下游 FieldMappingRegistry 反向映射时后者覆盖前者，权限注入字段名错误。
        Set<String> dimensionFieldNames = new HashSet<>();

        // 处理维度（展开为 $id 和 $caption，仅包含QM暴露的维度）
        for (DbDimension dimension : jdbcModel.getDimensions()) {
            if (!isFieldInLevels(dimension.getAi(), levels)) {
                continue;
            }

            String baseName = dimension.getEffectiveName();
            if (fieldFilter != null && !fieldFilter.contains(baseName)
                    && !fieldFilter.contains(baseName + "$id")
                    && !fieldFilter.contains(baseName + "$caption")) {
                continue;
            }

            // fieldAccess 列权限裁剪：维度基础名不在白名单则跳过
            if (fieldAccess != null && !fieldAccess.contains(baseName)) {
                continue;
            }

            // 检查 QM 是否暴露了该维度（$id 或 $caption 至少有一个在 QM columnGroups 中）
            String idFieldName = baseName + "$id";
            String captionFieldName = baseName + "$caption";
            if (queryModel.findJdbcQueryColumnByName(idFieldName, false) == null
                    && queryModel.findJdbcQueryColumnByName(captionFieldName, false) == null) {
                continue;
            }

            // 展开为两个独立字段
            // 1. $id 字段
            dimensionFieldNames.add(idFieldName);
            Map<String, Object> idFieldInfo = createDimensionIdFieldInfo(dimension, queryModel.getName());
            fields.put(idFieldName, idFieldInfo);

            // 2. $caption 字段
            dimensionFieldNames.add(captionFieldName);
            Map<String, Object> captionFieldInfo = createDimensionCaptionFieldInfo(dimension, queryModel.getName());
            fields.put(captionFieldName, captionFieldInfo);

            // 3. 处理维度属性（仅包含QM暴露的属性）
            for (DbProperty prop : ((DbDimensionSupport) dimension).getJdbcProperties()) {
                // 先尝试使用默认格式查找
                String defaultPropFieldName = baseName + "$" + prop.getName();
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(defaultPropFieldName, false);

                if (queryColumn == null) {
                    continue;
                }

                if (!isFieldInLevels(prop.getAi(), levels)) {
                    continue;
                }

                // 使用 QM 中定义的列名（name），而不是默认格式
                // 这样可以支持用户在 QM 中使用 alias 重命名字段
                String propFieldName = queryColumn.getName();
                dimensionFieldNames.add(propFieldName);
                Map<String, Object> propFieldInfo = createDimensionPropertyFieldInfo(dimension, prop, queryModel.getName(), propFieldName);
                fields.put(propFieldName, propFieldInfo);
            }
        }

        // 处理属性（排除已在维度字段中输出的，与 markdown buildSingleModelMarkdown 对齐）
        for (DbQueryProperty queryProperty : queryModel.getQueryProperties()) {
            if (!isFieldInLevels(queryProperty.getAi(), levels)) {
                continue;
            }

            DbProperty property = queryProperty.getProperty();
            String fieldName = property.getName();

            // 跳过已作为维度字段输出的属性，避免 sourceColumn 重复映射
            if (dimensionFieldNames.contains(queryProperty.getName())) {
                continue;
            }

            if (fieldFilter != null && !fieldFilter.contains(fieldName)) {
                continue;
            }

            // fieldAccess 列权限裁剪
            if (fieldAccess != null && !fieldAccess.contains(fieldName)) {
                continue;
            }

            Map<String, Object> fieldInfo = createPropertyFieldInfo(property, queryModel.getName());
            fields.put(fieldName, fieldInfo);
        }

        // 处理度量
        for (DbMeasure measure : jdbcModel.getMeasures()) {
            if (!isFieldInLevels(measure.getAi(), levels)) {
                continue;
            }

            String fieldName = measure.getName();
            if (fieldFilter != null && !fieldFilter.contains(fieldName)) {
                continue;
            }

            // fieldAccess 列权限裁剪
            if (fieldAccess != null && !fieldAccess.contains(fieldName)) {
                continue;
            }

            Map<String, Object> fieldInfo = createMeasureFieldInfo(measure, queryModel.getName());
            fields.put(fieldName, fieldInfo);
        }

        // 处理 QM 预定义计算字段
        for (CalculatedFieldDef calc : queryModel.getPredefinedCalculatedFields()) {
            String fieldName = calc.getName();
            if (fieldFilter != null && !fieldFilter.contains(fieldName)) {
                continue;
            }

            // fieldAccess 列权限裁剪：按依赖源字段判定
            if (!isCalculatedFieldAccessible(calc, fieldAccess)) {
                continue;
            }

            Map<String, Object> fieldInfo = createCalculatedFieldInfo(calc, queryModel.getName());
            fields.put(fieldName, fieldInfo);
        }
    }

    /**
     * 创建维度 $id 字段信息
     */
    private Map<String, Object> createDimensionIdFieldInfo(DbDimension dimension, String modelName) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();
        String baseName = dimension.getEffectiveName();

        fieldInfo.put("name", (dimension.getCaption() != null ? dimension.getCaption() : baseName) + "(ID)");
        fieldInfo.put("fieldName", baseName + "$id");

        // 获取 $id 的类型描述
        String idType = getIdTypeDescription(dimension);
        String idFormatHint = getIdFormatHint(dimension);

        String metaStr = "维度ID | " + idType + (idFormatHint != null ? " | " + idFormatHint : "");
        if (isHierarchicalDimension(dimension)) {
            metaStr += " | 层级维度(selfAndDescendantsOf/selfAndAncestorsOf)";
        }
        fieldInfo.put("meta", metaStr);

        // 前端需要的字段
        fieldInfo.put("type", getJdbcColumnType(dimension));
        fieldInfo.put("filterType", "dimension");
        fieldInfo.put("filterable", true);
        fieldInfo.put("measure", false);
        fieldInfo.put("aggregatable", false);

        // 输出事实表外键列名（供外部系统做 DB列名→QM字段 自动映射）
        String fk = dimension.getForeignKey();
        if (fk != null) {
            fieldInfo.put("sourceColumn", fk);
        }

        // 层级维度标记：支持 selfAndDescendantsOf/selfAndAncestorsOf 等层级操作符
        if (isHierarchicalDimension(dimension)) {
            fieldInfo.put("hierarchical", true);
            fieldInfo.put("hierarchyOps", Arrays.asList(
                    "selfAndDescendantsOf", "descendantsOf", "childrenOf",
                    "selfAndAncestorsOf", "ancestorsOf"));
        }

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("description", buildIdDescription(dimension));
        modelInfo.put("usage", isHierarchicalDimension(dimension)
                ? "用于精确查询、层级查询（如查某节点及其所有子节点）、作为外键关联、排序"
                : "用于精确查询、作为外键关联、排序");

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, modelInfo);
        fieldInfo.put("models", models);

        return fieldInfo;
    }

    /**
     * 创建维度 $caption 字段信息
     */
    private Map<String, Object> createDimensionCaptionFieldInfo(DbDimension dimension, String modelName) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();
        String baseName = dimension.getEffectiveName();

        fieldInfo.put("name", (dimension.getCaption() != null ? dimension.getCaption() : baseName) + "(名称)");
        fieldInfo.put("fieldName", baseName + "$caption");

        String captionFormatHint = getCaptionFormatHint(dimension);
        fieldInfo.put("meta", "维度名称 | 文本" + (captionFormatHint != null ? " | " + captionFormatHint : ""));

        // 前端需要的字段
        fieldInfo.put("type", "TEXT");
        fieldInfo.put("filterType", "dimension");
        fieldInfo.put("filterable", true);
        fieldInfo.put("measure", false);
        fieldInfo.put("aggregatable", false);

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("description", buildCaptionDescription(dimension));
        modelInfo.put("usage", "用于展示、模糊查询");

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, modelInfo);
        fieldInfo.put("models", models);

        return fieldInfo;
    }

    /**
     * 创建维度属性字段信息
     */
    private Map<String, Object> createDimensionPropertyFieldInfo(DbDimension dimension, DbProperty prop, String modelName, String fieldName) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();

        fieldInfo.put("name", (prop.getCaption() != null ? prop.getCaption() : prop.getName()));
        fieldInfo.put("fieldName", fieldName);

        DbColumnType columnType = prop.getPropertyDbColumn().getType();
        String dataType = getDataTypeDescription(columnType);
        fieldInfo.put("meta", "维度属性 | " + dataType);

        // 前端需要的字段
        fieldInfo.put("type", columnType != null ? columnType.name() : "TEXT");
        fieldInfo.put("filterType", getFilterTypeForProperty(columnType));
        fieldInfo.put("filterable", true);
        fieldInfo.put("measure", false);
        fieldInfo.put("aggregatable", false);

        // 如果是字典类型，添加字典信息
        if (StringUtils.isNotEmpty(prop.getDictRef())) {
            fieldInfo.put("dictId", prop.getDictRef());
            // 内联字典选项，供 frontend-meta v1 直接使用
            if (dbModelDictService != null) {
                DbDictDef dictDef = dbModelDictService.getDictById(prop.getDictRef());
                if (dictDef != null && dictDef.getItems() != null) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (DbDictItemDef item : dictDef.getItems()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("value", item.getValue());
                        m.put("label", item.getLabel());
                        items.add(m);
                    }
                    fieldInfo.put("dictItems", items);
                }
            }
        }

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("description", prop.getDescription() != null ? prop.getDescription() : prop.getCaption());

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, modelInfo);
        fieldInfo.put("models", models);

        return fieldInfo;
    }

    /**
     * 获取 $id 的类型描述
     */
    private String getIdTypeDescription(DbDimension dimension) {
        DbDimensionType type = dimension.getType();
        if (DbDimensionType.DATETIME == type || DbDimensionType.DAY == type) {
            return "日期";
        }
        return "数值/文本";
    }

    /**
     * 判断维度是否为层级维度（父子维度）
     */
    private boolean isHierarchicalDimension(DbDimension dimension) {
        return dimension instanceof DbModelParentChildDimensionImpl;
    }

    /**
     * 获取 $id 的格式提示（从 AI 配置或 keyDescription 获取）
     */
    private String getIdFormatHint(DbDimension dimension) {
        // 优先从 keyDescription 获取
        String keyDesc = dimension.getKeyDescription();
        if (StringUtils.isNotEmpty(keyDesc)) {
            return keyDesc;
        }

        // 其次从 AI 配置获取
        AiObject ai = dimension.getAi();
        if (ai != null && StringUtils.isNotEmpty(ai.getPrompt())) {
            // 检查 prompt 中是否包含格式说明
            String prompt = ai.getPrompt();
            if (prompt.contains("格式") || prompt.contains("format")) {
                return prompt;
            }
        }

        // 基于维度类型推断（仅类型，不基于名称）
        DbDimensionType type = dimension.getType();
        if (DbDimensionType.DATETIME == type) {
            return "格式: yyyyMMddHHmmss";
        } else if (DbDimensionType.DAY == type) {
            return "格式: yyyyMMdd";
        }

        // 不再基于名称推断，返回 null
        return null;
    }

    /**
     * 获取 $caption 的格式提示
     */
    private String getCaptionFormatHint(DbDimension dimension) {
        // 检查是否有 keyCaption 配置
        if (dimension instanceof DbDimensionSupport) {
            String keyCaption = ((DbDimensionSupport) dimension).getKeyCaption();
            if (StringUtils.isNotEmpty(keyCaption)) {
                return keyCaption;
            }
        }

        // 基于维度类型推断
        DbDimensionType type = dimension.getType();
        if (DbDimensionType.DATETIME == type) {
            return "格式: yyyy-MM-dd HH:mm:ss";
        } else if (DbDimensionType.DAY == type) {
            return "格式: yyyy年MM月dd日 或 yyyy-MM-dd";
        }

        return null;
    }

    /**
     * 构建 $id 字段描述
     */
    private String buildIdDescription(DbDimension dimension) {
        StringBuilder sb = new StringBuilder();
        sb.append(dimension.getFullPathForAlias()).append("$id");

        String caption = dimension.getCaption();
        if (StringUtils.isNotEmpty(caption)) {
            sb.append(" | ").append(caption).append("的ID/值");
        }

        String hint = getIdFormatHint(dimension);
        if (hint != null) {
            sb.append(" | ").append(hint);
        }

        return sb.toString();
    }

    /**
     * 构建 $caption 字段描述
     */
    private String buildCaptionDescription(DbDimension dimension) {
        StringBuilder sb = new StringBuilder();
        sb.append(dimension.getFullPathForAlias()).append("$caption");

        String caption = dimension.getCaption();
        if (StringUtils.isNotEmpty(caption)) {
            sb.append(" | ").append(caption).append("的显示名称");
        }

        String hint = getCaptionFormatHint(dimension);
        if (hint != null) {
            sb.append(" | ").append(hint);
        }

        return sb.toString();
    }

    /**
     * 收集字段信息（V3版本：维度展开）
     *
     * @param queryModel 查询模型
     * @param allFields 所有字段信息
     * @param fieldFilter 字段过滤器
     * @param levels AI级别
     * @param referencedDictIds 收集被引用的 fsscript 字典ID
     * @param referencedDictClasses 收集被引用的 Java 类字典
     * @param fieldAccess 运行时列权限白名单（null 表示不限制）
     */
    private void collectFieldsInfoV3(QueryModel queryModel, Map<String, FieldInfoV3> allFields,
                                     List<String> fieldFilter, List<Integer> levels,
                                     Set<String> referencedDictIds, Set<DictInfo> referencedDictClasses,
                                     Set<String> fieldAccess) {
        TableModel jdbcModel = queryModel.getJdbcModel();

        // 收集维度信息（展开为 $id 和 $caption，仅包含QM暴露的维度）
        for (DbDimension dimension : jdbcModel.getDimensions()) {
            if (!isFieldInLevels(dimension.getAi(), levels)) {
                continue;
            }

            String baseName = dimension.getEffectiveName();
            if (fieldFilter != null && !fieldFilter.contains(baseName)
                    && !fieldFilter.contains(baseName + "$id")
                    && !fieldFilter.contains(baseName + "$caption")) {
                continue;
            }

            // fieldAccess 列权限裁剪
            if (fieldAccess != null && !fieldAccess.contains(baseName)) {
                continue;
            }

            // 检查 QM 是否暴露了该维度（$id 或 $caption 至少有一个在 QM columnGroups 中）
            String idFieldName = baseName + "$id";
            String captionFieldName = baseName + "$caption";
            if (queryModel.findJdbcQueryColumnByName(idFieldName, false) == null
                    && queryModel.findJdbcQueryColumnByName(captionFieldName, false) == null) {
                continue;
            }

            // $id 字段
            FieldInfoV3 idFieldInfo = allFields.computeIfAbsent(idFieldName, k -> new FieldInfoV3());
            idFieldInfo.addDimensionId(dimension, queryModel.getName(), this);

            // $caption 字段
            FieldInfoV3 captionFieldInfo = allFields.computeIfAbsent(captionFieldName, k -> new FieldInfoV3());
            captionFieldInfo.addDimensionCaption(dimension, queryModel.getName(), this);

            // 维度属性（仅包含QM暴露的属性）
            for (DbProperty prop : ((DbDimensionSupport) dimension).getJdbcProperties()) {
                // 先尝试使用默认格式查找
                String defaultPropFieldName = baseName + "$" + prop.getName();
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(defaultPropFieldName, false);
                
                if (queryColumn == null) {
                    continue;
                }
                
                if (!isFieldInLevels(prop.getAi(), levels)) {
                    continue;
                }
                
                // 使用 QM 中定义的列名（name），而不是默认格式
                // 这样可以支持用户在 QM 中使用 alias 重命名字段
                String propFieldName = queryColumn.getName();
                FieldInfoV3 propFieldInfo = allFields.computeIfAbsent(propFieldName, k -> new FieldInfoV3());
                propFieldInfo.addDimensionProperty(dimension, prop, queryModel.getName(), this,
                        referencedDictIds, referencedDictClasses);
            }
        }

        // 收集属性信息
        for (DbQueryProperty queryProperty : queryModel.getQueryProperties()) {
            if (!isFieldInLevels(queryProperty.getAi(), levels)) {
                continue;
            }

            DbProperty property = queryProperty.getProperty();
            String fieldName = property.getName();
            if (fieldFilter != null && !fieldFilter.contains(fieldName)) {
                continue;
            }

            // fieldAccess 列权限裁剪
            if (fieldAccess != null && !fieldAccess.contains(fieldName)) {
                continue;
            }

            FieldInfoV3 fieldInfo = allFields.computeIfAbsent(fieldName, k -> new FieldInfoV3());
            fieldInfo.addProperty(queryProperty, queryModel.getName(), this,
                    referencedDictIds, referencedDictClasses);
        }

        // 收集度量信息
        for (DbQueryColumn queryColumn : queryModel.getJdbcQueryColumns()) {
            if (queryColumn.isMeasure()) {
                if (!isFieldInLevels(queryColumn.getAi(), levels)) {
                    continue;
                }

                String fieldName = queryColumn.getName();
                if (fieldFilter != null && !fieldFilter.contains(fieldName)) {
                    continue;
                }

                // fieldAccess 列权限裁剪
                if (fieldAccess != null && !fieldAccess.contains(fieldName)) {
                    continue;
                }

                FieldInfoV3 fieldInfo = allFields.computeIfAbsent(fieldName, k -> new FieldInfoV3());
                fieldInfo.addMeasure(queryColumn, queryModel.getName(), this);
            }
        }

        // 收集 QM 预定义计算字段信息
        for (CalculatedFieldDef calc : queryModel.getPredefinedCalculatedFields()) {
            String fieldName = calc.getName();
            if (fieldFilter != null && !fieldFilter.contains(fieldName)) {
                continue;
            }

            // fieldAccess 列权限裁剪：按依赖源字段判定
            if (!isCalculatedFieldAccessible(calc, fieldAccess)) {
                continue;
            }

            FieldInfoV3 fieldInfo = allFields.computeIfAbsent(fieldName, k -> new FieldInfoV3());
            fieldInfo.addCalculatedField(calc, queryModel.getName());
        }
    }

    /**
     * 判断计算字段的所有依赖源字段是否都在 fieldAccess 白名单内。
     * fieldAccess 为 null 或表达式为空时视为可访问。
     * 解析失败时 fail-closed（视为不可访问）。
     */
    private boolean isCalculatedFieldAccessible(CalculatedFieldDef calc, Set<String> fieldAccess) {
        if (fieldAccess == null || calc.getExpression() == null) {
            return true;
        }
        try {
            Set<String> deps = com.foggyframework.dataset.db.model.engine.expression
                    .CalculatedFieldService.extractColumnReferences(calc.getExpression());
            for (String dep : deps) {
                if (!fieldAccess.contains(dep)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            // fail-closed：无法解析依赖时不暴露
            return false;
        }
    }

    private boolean isFieldInLevels(AiObject ai, List<Integer> requestedLevels) {
        if (requestedLevels == null || requestedLevels.isEmpty()) {
            if (ai == null || !ai.isEnabled()) {
                return true;
            }
            List<Integer> fieldLevels = ai.getLevels();
            if (fieldLevels == null || fieldLevels.isEmpty()) {
                return true;
            }
            return fieldLevels.contains(1);
        }

        if (ai == null || !ai.isEnabled()) {
            return requestedLevels.contains(1);
        }

        List<Integer> fieldLevels = ai.getLevels();
        if (fieldLevels == null || fieldLevels.isEmpty()) {
            return requestedLevels.contains(1);
        }

        for (Integer fieldLevel : fieldLevels) {
            if (requestedLevels.contains(fieldLevel)) {
                return true;
            }
        }
        return false;
    }

    private void processModelInfo(QueryModel queryModel, Map<String, Object> models) {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("name", queryModel.getCaption() != null ? queryModel.getCaption() : queryModel.getName());

        // 只为非jdbc模型添加类型标识（jdbc是默认类型，不需要标注以节省token）
        TableModel jdbcModel = queryModel.getJdbcModel();
        DbModelType modelType = jdbcModel.getModelType();
        if (modelType != null && modelType != DbModelType.jdbc) {
            modelInfo.put("type", modelType.name());
        }

        // 输出事实表名（供外部系统做 DB列名→QM字段 自动映射）
        String tableName = jdbcModel.getTableName();
        if (tableName != null) {
            modelInfo.put("factTable", tableName);
        }

        modelInfo.put("purpose", "数据查询和分析");
        modelInfo.put("scenarios", Arrays.asList("数据查询", "统计分析", "报表生成"));
        models.put(queryModel.getName(), modelInfo);
    }

    private Map<String, Object> createPropertyFieldInfo(DbProperty property, String modelName) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();
        fieldInfo.put("name", property.getCaption() != null ? property.getCaption() : property.getName());
        fieldInfo.put("fieldName", property.getName());

        DbColumnType columnType = property.getPropertyDbColumn().getType();
        String dataType = getDataTypeDescription(columnType);
        fieldInfo.put("meta", "属性 | " + dataType);

        // 前端需要的字段
        fieldInfo.put("type", columnType != null ? columnType.name() : "TEXT");
        fieldInfo.put("filterType", getFilterTypeForProperty(columnType));
        fieldInfo.put("filterable", true);
        fieldInfo.put("measure", false);
        fieldInfo.put("aggregatable", false);

        // 如果是字典类型，添加字典信息
        if (StringUtils.isNotEmpty(property.getDictRef())) {
            fieldInfo.put("dictId", property.getDictRef());
            // 内联字典选项，供 frontend-meta v1 直接使用
            if (dbModelDictService != null) {
                DbDictDef dictDef = dbModelDictService.getDictById(property.getDictRef());
                if (dictDef != null && dictDef.getItems() != null) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (DbDictItemDef item : dictDef.getItems()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("value", item.getValue());
                        m.put("label", item.getLabel());
                        items.add(m);
                    }
                    fieldInfo.put("dictItems", items);
                }
            }
        }

        // 输出源列名（供外部系统做 DB列名→QM字段 自动映射）
        String sqlCol = property.getPropertyDbColumn().getSqlColumnName();
        if (sqlCol != null) {
            fieldInfo.put("sourceColumn", sqlCol);
        }

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("description", property.getCaption());

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, modelInfo);
        fieldInfo.put("models", models);

        return fieldInfo;
    }

    private Map<String, Object> createCalculatedFieldInfo(CalculatedFieldDef calc, String modelName) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();
        fieldInfo.put("name", calc.getCaption() != null ? calc.getCaption() : calc.getName());
        fieldInfo.put("fieldName", calc.getName());

        boolean isWindow = calc.getPartitionBy() != null || calc.getWindowOrderBy() != null;
        String typeStr = calc.getType() != null ? calc.getType() : "NUMBER";
        fieldInfo.put("meta", (isWindow ? "窗口计算字段" : "计算字段") + " | " + typeStr);

        fieldInfo.put("type", typeStr);
        fieldInfo.put("filterType", "number");
        fieldInfo.put("filterable", false);
        fieldInfo.put("measure", false);
        fieldInfo.put("aggregatable", false);
        fieldInfo.put("calculated", true);
        fieldInfo.put("predefined", true);

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("description", (calc.getCaption() != null ? calc.getCaption() : calc.getName())
                + " (公式: " + calc.getExpression() + ")");
        modelInfo.put("usage", "预定义计算字段，直接在 columns 中引用即可，不要在 calculatedFields 中重复定义");

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, modelInfo);
        fieldInfo.put("models", models);

        return fieldInfo;
    }

    private Map<String, Object> createMeasureFieldInfo(DbMeasure measure, String modelName) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();
        fieldInfo.put("name", measure.getCaption() != null ? measure.getCaption() : measure.getName());
        fieldInfo.put("fieldName", measure.getName());

        String aggregation = measure.getAggregation() != null ? measure.getAggregation().name() : "SUM";
        fieldInfo.put("meta", "度量 | 数值 | 默认聚合:" + aggregation);

        // 前端需要的字段
        DbColumnType columnType = measure.getJdbcColumn().getType();
        fieldInfo.put("type", columnType != null ? columnType.name() : "NUMBER");
        fieldInfo.put("filterType", "number");
        fieldInfo.put("filterable", true);
        fieldInfo.put("measure", true);
        fieldInfo.put("aggregatable", true);
        fieldInfo.put("aggregation", aggregation);

        // 输出源列名（供外部系统做 DB列名→QM字段 自动映射）
        String sqlCol = measure.getJdbcColumn().getSqlColumnName();
        if (sqlCol != null) {
            fieldInfo.put("sourceColumn", sqlCol);
        }

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("description", measure.getCaption() + " (聚合方式: " + aggregation + ")");

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, modelInfo);
        fieldInfo.put("models", models);

        return fieldInfo;
    }

    private String getDataTypeDescription(DbColumnType dbColumnType) {
        if (dbColumnType == null) return "文本";

        switch (dbColumnType) {
            case DICT:
                return "字典";
            case MONEY:
                return "金额";
            case DAY:
                return "日期(yyyy-MM-dd)";
            case DATETIME:
                return "日期时间";
            case NUMBER:
                return "数值";
            case BOOL:
                return "布尔";
            case TEXT:
            default:
                return "文本";
        }
    }

    /**
     * 获取维度的 JDBC 列类型
     */
    private String getJdbcColumnType(DbDimension dimension) {
        DbDimensionType type = dimension.getType();
        if (type == null) {
            return "TEXT";
        }

        switch (type) {
            case DAY:
                return "DAY";
            case DATETIME:
                return "DATETIME";
            case INTEGER:
                return "INTEGER";
            case DOUBLE:
                return "NUMBER";
            case DICT:
                return "DICT";
            case BOOL:
                return "BOOL";
            case NORMAL:
            default:
                return "TEXT";
        }
    }

    /**
     * 根据列类型推断前端过滤器类型
     */
    private String getFilterTypeForProperty(DbColumnType columnType) {
        if (columnType == null) {
            return "text";
        }

        switch (columnType) {
            case MONEY:
            case NUMBER:
            case INTEGER:
            case BIGINT:
                return "number";
            case DAY:
            case DATETIME:
                return "date";
            case BOOL:
                return "bool";
            case DICT:
                return "dict";
            case TEXT:
            case STRING:
            default:
                return "text";
        }
    }

    private String buildDictMarkdown(DictInfo dictInfo) {
        try {
            Class<?> cls = Class.forName(dictInfo.getDictClass());
            StringBuilder sb = new StringBuilder();
            sb.append(" - ").append(dictInfo.getName()).append(":");
            for (Field field : cls.getFields()) {
                if (BeanInfoHelper.isStaticField(field)) {
                    try {
                        ApiModelProperty amp = field.getAnnotation(ApiModelProperty.class);
                        if (amp != null) {
                            Object v = field.get(null);
                            String caption = StringUtils.isNotEmpty(amp.name()) ? amp.name() : amp.value();
                            sb.append(v).append("->").append(caption).append(";");
                        }
                    } catch (IllegalAccessException e) {
                        // ignore
                    }
                }
            }
            sb.append("\n");
            return sb.toString();
        } catch (ClassNotFoundException e) {
            return "";
        }
    }

    String getCaption(DbObject dbObject) {
        return dbObject.getCaption();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    private static class DictInfo {
        String name;
        private String dictClass;
    }

    /**
     * V3版本字段信息
     */
    @Data
    private static class FieldInfoV3 {
        private String displayName;
        private String meta;
        private String fieldType;
        private Map<String, ModelUsage> modelUsages = new LinkedHashMap<>();

        public void addDimensionId(DbDimension dimension, String modelName, SemanticServiceV3Impl service) {
            String baseName = dimension.getEffectiveName();
            this.displayName = service.getCaption(dimension) + "(ID)";

            String idType = service.getIdTypeDescription(dimension);
            String idHint = service.getIdFormatHint(dimension);
            this.meta = "维度ID | " + idType + (idHint != null ? " | " + idHint : "");
            if (service.isHierarchicalDimension(dimension)) {
                this.meta += " | 层级维度(selfAndDescendantsOf/selfAndAncestorsOf)";
            }
            this.fieldType = "dimension_id";

            ModelUsage usage = new ModelUsage();
            String desc = service.buildIdDescription(dimension);
            if (service.isHierarchicalDimension(dimension)) {
                desc = desc + " (层级维度，支持selfAndDescendantsOf等层级操作符)";
            }
            usage.setDescription(desc);
            modelUsages.put(modelName, usage);
        }

        public void addDimensionCaption(DbDimension dimension, String modelName, SemanticServiceV3Impl service) {
            String baseName = dimension.getEffectiveName();
            this.displayName = service.getCaption(dimension) + "(名称)";

            String captionHint = service.getCaptionFormatHint(dimension);
            this.meta = "维度名称 | 文本" + (captionHint != null ? " | " + captionHint : "");
            this.fieldType = "dimension_caption";

            ModelUsage usage = new ModelUsage();
            usage.setDescription(service.buildCaptionDescription(dimension));
            modelUsages.put(modelName, usage);
        }

        public void addDimensionProperty(DbDimension dimension, DbProperty prop, String modelName,
                                         SemanticServiceV3Impl service,
                                         Set<String> referencedDictIds, Set<DictInfo> referencedDictClasses) {
            this.displayName = prop.getCaption() != null ? prop.getCaption() : prop.getName();

            String dataType = service.getDataTypeDescription(prop.getPropertyDbColumn().getType());
            this.meta = "维度属性 | " + dataType;
            this.fieldType = "dimension_property";

            ModelUsage usage = new ModelUsage();
            usage.setDescription(prop.getDescription() != null ? prop.getDescription() : prop.getCaption());

            // 处理 dictRef（fsscript 字典引用）
            String dictRef = prop.getDictRef();
            if (StringUtils.isNotEmpty(dictRef)) {
                usage.setDictRef(dictRef);
                referencedDictIds.add(dictRef);
                this.meta += " (字典:" + dictRef + ")";
            }
            // 处理 Java 类字典（兼容旧方式）
            else if (StringUtils.equals(prop.getPropertyDbColumn().getType(), "DICT")) {
                String dictClass = prop.getExtDataValue("dictClass");
                if (StringUtils.isNotEmpty(dictClass)) {
                    String[] names = dictClass.split("\\.");
                    String name = names[names.length - 1];
                    DictInfo dictInfo = new DictInfo(name, dictClass);
                    usage.setDictInfo(dictInfo);
                    referencedDictClasses.add(dictInfo);
                    this.meta += ":" + name;
                }
            }

            modelUsages.put(modelName, usage);
        }

        public void addProperty(DbQueryProperty queryProperty, String modelName, SemanticServiceV3Impl service,
                                Set<String> referencedDictIds, Set<DictInfo> referencedDictClasses) {
            DbProperty property = queryProperty.getProperty();
            this.displayName = service.getCaption(property);

            String dataType = service.getDataTypeDescription(property.getPropertyDbColumn().getType());
            this.meta = "属性 | " + dataType;
            this.fieldType = "property";

            ModelUsage usage = new ModelUsage();
            usage.setDescription(property.getDescription() != null ? property.getDescription() : property.getCaption());

            // 处理 dictRef（fsscript 字典引用）
            String dictRef = property.getDictRef();
            if (StringUtils.isNotEmpty(dictRef)) {
                usage.setDictRef(dictRef);
                referencedDictIds.add(dictRef);
                this.meta += " (字典:" + dictRef + ")";
            }
            // 处理 Java 类字典（兼容旧方式）
            else if (StringUtils.equals(property.getPropertyDbColumn().getType(), "DICT")) {
                String dictClass = property.getExtDataValue("dictClass");
                if (StringUtils.isNotEmpty(dictClass)) {
                    String[] names = dictClass.split("\\.");
                    String name = names[names.length - 1];
                    DictInfo dictInfo = new DictInfo(name, dictClass);
                    usage.setDictInfo(dictInfo);
                    referencedDictClasses.add(dictInfo);
                    this.meta += ":" + name;
                }
            }

            modelUsages.put(modelName, usage);
        }

        public void addMeasure(DbQueryColumn measure, String modelName, SemanticServiceV3Impl service) {
            this.displayName = service.getCaption(measure);
            this.meta = "度量 | 数值" + (measure.getAggregation() != null ? " | 默认聚合:" + measure.getAggregation() : "");
            this.fieldType = "measure";

            ModelUsage usage = new ModelUsage();
            usage.setDescription(measure.getDescription() != null ? measure.getDescription() : measure.getCaption());
            usage.setAggregation(measure.getAggregation());
            modelUsages.put(modelName, usage);
        }

        public void addCalculatedField(CalculatedFieldDef calc, String modelName) {
            this.displayName = calc.getCaption() != null ? calc.getCaption() : calc.getName();
            boolean isWindow = calc.getPartitionBy() != null || calc.getWindowOrderBy() != null;
            String typeStr = calc.getType() != null ? calc.getType() : "NUMBER";
            this.meta = (isWindow ? "窗口计算字段" : "计算字段") + " | " + typeStr;
            this.fieldType = isWindow ? "window_calculated" : "calculated";

            ModelUsage usage = new ModelUsage();
            usage.setDescription(this.displayName + " (公式: " + calc.getExpression() + ")");
            modelUsages.put(modelName, usage);
        }

        @Data
        public static class ModelUsage {
            private String description;
            private DbAggregation aggregation;
            /** fsscript 字典引用ID */
            private String dictRef;
            /** Java类字典信息（兼容旧方式） */
            private DictInfo dictInfo;
        }
    }
}
