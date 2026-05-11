package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.db.model.spi.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 构建 QM 字段名 ↔ 物理列的双向映射
 * <p>
 * 在 QM 加载完成后调用，遍历所有字段类型（度量、属性、维度、计算字段）
 * 提取物理 table.column 信息。
 *
 * @since 8.2.0
 */
@Slf4j
public final class PhysicalColumnMappingBuilder {

    private PhysicalColumnMappingBuilder() {}

    /**
     * 从已加载的 QueryModel 构建物理列映射
     */
    public static PhysicalColumnMapping build(QueryModelSupport qm) {
        Map<String, List<PhysicalColumnRef>> qmToPhysical = new LinkedHashMap<>();
        Map<String, List<String>> physicalToQm = new LinkedHashMap<>();

        TableModel mainModel = qm.getJdbcModel();
        if (mainModel == null) {
            return new PhysicalColumnMappingImpl(qmToPhysical, physicalToQm);
        }

        String factTable = mainModel.getTableName();

        // 1. 度量
        for (DbMeasure measure : mainModel.getMeasures()) {
            String qmName = measure.getName();
            DbColumn col = measure.getJdbcColumn();
            if (col != null && col.getSqlColumnName() != null) {
                addMapping(qmToPhysical, physicalToQm, qmName, factTable, col.getSqlColumnName());
            }
        }

        // 2. 事实表属性
        if (mainModel.getProperties() != null) {
            for (DbProperty prop : mainModel.getProperties()) {
                String qmName = prop.getName();
                DbColumn col = prop.getPropertyDbColumn();
                if (col != null && col.getSqlColumnName() != null) {
                    addMapping(qmToPhysical, physicalToQm, qmName, factTable, col.getSqlColumnName());
                }
            }
        }

        // 3. 维度
        for (DbDimension dim : mainModel.getDimensions()) {
            processDimension(dim, factTable, qmToPhysical, physicalToQm);
        }

        // 4. QM 预定义计算字段（传递解析到基础字段的物理列）
        processCalculatedFields(qm.getPredefinedCalculatedFields(), qmToPhysical, physicalToQm);

        if (log.isDebugEnabled()) {
            log.debug("Built physical column mapping: {} QM fields, {} physical columns",
                    qmToPhysical.size(), physicalToQm.size());
        }

        return new PhysicalColumnMappingImpl(qmToPhysical, physicalToQm);
    }

    /**
     * 处理维度字段（$id、$caption、属性），递归处理子维度
     */
    private static void processDimension(DbDimension dim, String factTable,
                                          Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                          Map<String, List<String>> physicalToQm) {
        String dimName = dim.getEffectiveName();

        // $id → FK on fact table
        String fk = dim.getForeignKey();
        if (fk != null) {
            addMapping(qmToPhysical, physicalToQm, dimName + "$id", factTable, fk);
        }

        if (dim instanceof DbDimensionSupport dimSupport) {
            // dimSupport.getJdbcModel() 返回父模型（事实表），不是维度表自身。
            // 维度的实际物理表通过 QueryObject 获取。
            TableQueryObject tqo = dimSupport.getQueryObject() != null
                    ? dimSupport.getQueryObject().getDecorate(TableQueryObject.class) : null;
            String dimTable = tqo != null ? tqo.getTableName() : factTable;

            if (log.isDebugEnabled()) {
                log.debug("Dimension '{}': dimTable={}, properties={}",
                        dimName, dimTable, dimSupport.getJdbcProperties().size());
            }

            // $id → PK on dimension table (additional mapping)
            String pk = dimSupport.getPrimaryKey();
            if (pk != null) {
                addMapping(qmToPhysical, physicalToQm, dimName + "$id", dimTable, pk);
            }

            // $caption → caption column on dimension table
            DbColumn captionCol = dim.getCaptionDbColumn();
            if (captionCol != null && captionCol.getSqlColumnName() != null) {
                addMapping(qmToPhysical, physicalToQm, dimName + "$caption", dimTable, captionCol.getSqlColumnName());
                if (dim.getType() == DbDimensionType.DATETIME || dim.getType() == DbDimensionType.DAY) {
                    addMapping(qmToPhysical, physicalToQm, dimName, dimTable, captionCol.getSqlColumnName());
                }
            }

            // 维度属性 → dimension table columns
            for (DbProperty prop : dimSupport.getJdbcProperties()) {
                DbColumn propCol = prop.getPropertyDbColumn();
                if (propCol != null && propCol.getSqlColumnName() != null) {
                    addMapping(qmToPhysical, physicalToQm,
                            dimName + "$" + prop.getName(), dimTable, propCol.getSqlColumnName());
                }
            }

            // 递归子维度（子维度的 FK 在当前维度表上）
            for (DbDimension childDim : dimSupport.getChildDimensions()) {
                processDimension(childDim, dimTable, qmToPhysical, physicalToQm);
            }
        }
    }

    /**
     * 处理计算字段：传递解析表达式依赖，收集所有基础字段的物理列
     */
    private static void processCalculatedFields(List<CalculatedFieldDef> calcFields,
                                                  Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                                  Map<String, List<String>> physicalToQm) {
        if (calcFields == null || calcFields.isEmpty()) {
            return;
        }

        // 构建计算字段名→表达式映射
        Map<String, String> calcFieldMap = new LinkedHashMap<>();
        for (CalculatedFieldDef calc : calcFields) {
            if (calc.getName() != null && calc.getExpression() != null) {
                calcFieldMap.put(calc.getName(), calc.getExpression());
            }
        }

        for (CalculatedFieldDef calc : calcFields) {
            if (calc.getExpression() == null) continue;
            try {
                // 传递解析到基础字段
                Set<String> baseDeps = CalculatedFieldService.resolveBaseColumnReferences(
                        calc.getExpression(), calcFieldMap);
                // 收集所有基础字段的物理列
                for (String baseDep : baseDeps) {
                    List<PhysicalColumnRef> baseRefs = qmToPhysical.get(baseDep);
                    if (baseRefs != null) {
                        for (PhysicalColumnRef ref : baseRefs) {
                            addMapping(qmToPhysical, physicalToQm, calc.getName(), ref.table(), ref.column());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to resolve calculated field '{}' dependencies: {}", calc.getName(), e.getMessage());
            }
        }
    }

    /**
     * 添加双向映射
     */
    private static void addMapping(Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                    Map<String, List<String>> physicalToQm,
                                    String qmFieldName, String table, String column) {
        PhysicalColumnRef ref = new PhysicalColumnRef(table, column);

        List<PhysicalColumnRef> refs = qmToPhysical.computeIfAbsent(qmFieldName, k -> new ArrayList<>());
        if (!refs.contains(ref)) {
            refs.add(ref);
        }

        List<String> qmNames = physicalToQm.computeIfAbsent(ref.toKey(), k -> new ArrayList<>());
        if (!qmNames.contains(qmFieldName)) {
            qmNames.add(qmFieldName);
        }
    }
}
