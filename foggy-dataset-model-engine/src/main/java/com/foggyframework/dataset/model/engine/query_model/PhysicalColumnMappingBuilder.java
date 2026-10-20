package com.foggyframework.dataset.model.engine.query_model;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.model.impl.query.QmColumnNaming;
import com.foggyframework.dataset.model.spi.*;
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

        // 4. aggregate relation 输出列（映射到 RHS 源表物理列）
        processAggregateRelationOutputColumns(qm.getJdbcQueryColumns(), qmToPhysical, physicalToQm);

        // Concrete QM names/outputs retain the SELECT and condition provenance.
        // Resolve actual bound columns, not a name-based lookup into another TM instance.
        for (DbQueryColumn column : QmColumnNaming.overrides(qm)) {
            mapNamedSource(column, column.getSelectColumn(), qmToPhysical, physicalToQm);
            if (column.getDbQueryCond() != null) {
                mapNamedSource(column, column.getDbQueryCond().getColumn(), qmToPhysical, physicalToQm);
            }
            DbDimensionColumn dimension = column.getSelectColumn().getDecorate(DbDimensionColumn.class);
            if (dimension != null && !dimension.isCaptionColumn()
                    && dimension.getDimension() instanceof DbDimensionSupport support) {
                TableQueryObject table = support.getQueryObject().getDecorate(TableQueryObject.class);
                if (table != null && support.getPrimaryKey() != null) {
                    addMappingIfNotBlank(qmToPhysical, physicalToQm, column.getName(), table.getTableName(), support.getPrimaryKey());
                    addMappingIfNotBlank(qmToPhysical, physicalToQm, column.getAlias(), table.getTableName(), support.getPrimaryKey());
                }
            }
        }

        // 5. QM 预定义计算字段（传递解析到基础字段的物理列）
        processCalculatedFields(qm, qm.getPredefinedCalculatedFields(), qmToPhysical, physicalToQm);

        if (log.isDebugEnabled()) {
            log.debug("Built physical column mapping: {} QM fields, {} physical columns",
                    qmToPhysical.size(), physicalToQm.size());
        }

        return new PhysicalColumnMappingImpl(qmToPhysical, physicalToQm);
    }

    private static void processAggregateRelationOutputColumns(List<DbQueryColumn> queryColumns,
                                                              Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                                              Map<String, List<String>> physicalToQm) {
        if (queryColumns == null || queryColumns.isEmpty()) {
            return;
        }
        for (DbQueryColumn queryColumn : queryColumns) {
            AggregateRelationOutputColumn outputColumn = queryColumn == null
                    ? null
                    : queryColumn.getDecorate(AggregateRelationOutputColumn.class);
            if (outputColumn == null) {
                continue;
            }
            DbColumn sourceColumn = outputColumn.getAggregateRelationSourceColumn();
            String sourceTable = resolvePhysicalTable(sourceColumn);
            String sourcePhysicalColumn = sourceColumn == null ? null : sourceColumn.getSqlColumnName();
            if (sourceTable == null || sourcePhysicalColumn == null) {
                continue;
            }
            addMappingIfNotBlank(qmToPhysical, physicalToQm, queryColumn.getName(), sourceTable, sourcePhysicalColumn);
            addMappingIfNotBlank(qmToPhysical, physicalToQm, queryColumn.getAlias(), sourceTable, sourcePhysicalColumn);
            addMappingIfNotBlank(qmToPhysical, physicalToQm, queryColumn.getField(), sourceTable, sourcePhysicalColumn);
        }
    }

    private static void mapNamedSource(DbQueryColumn projection, DbColumn source,
                                       Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                       Map<String, List<String>> physicalToQm) {
        String table = resolvePhysicalTable(source);
        if (table == null || source.getSqlColumnName() == null) return;
        addMappingIfNotBlank(qmToPhysical, physicalToQm, projection.getName(), table, source.getSqlColumnName());
        addMappingIfNotBlank(qmToPhysical, physicalToQm, projection.getAlias(), table, source.getSqlColumnName());
    }

    private static String resolvePhysicalTable(DbColumn column) {
        if (column == null || column.getQueryObject() == null) {
            return null;
        }
        TableQueryObject tqo = column.getQueryObject().getDecorate(TableQueryObject.class);
        if (tqo != null && tqo.getTableName() != null) {
            return tqo.getTableName();
        }
        return null;
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
    private static void processCalculatedFields(QueryModelSupport qm, List<CalculatedFieldDef> calcFields,
                                                  Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                                  Map<String, List<String>> physicalToQm) {
        if (calcFields == null || calcFields.isEmpty()) {
            return;
        }

        var dependencies = qm.getCalculatedDependencyGraph();

        for (CalculatedFieldDef calc : calcFields) {
            if (calc.getExpression() == null) continue;
            try {
                // 传递解析到基础字段
                Set<String> baseDeps = dependencies.base(calc);
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
                throw new IllegalArgumentException("Cannot resolve calculated field dependencies: " + calc.getName(), e);
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

    private static void addMappingIfNotBlank(Map<String, List<PhysicalColumnRef>> qmToPhysical,
                                             Map<String, List<String>> physicalToQm,
                                             String qmFieldName, String table, String column) {
        if (qmFieldName == null || qmFieldName.isBlank() || table == null || table.isBlank()
                || column == null || column.isBlank()) {
            return;
        }
        addMapping(qmToPhysical, physicalToQm, qmFieldName, table, column);
    }
}
