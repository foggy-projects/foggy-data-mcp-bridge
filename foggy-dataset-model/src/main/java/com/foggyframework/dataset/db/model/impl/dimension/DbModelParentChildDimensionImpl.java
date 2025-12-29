package com.foggyframework.dataset.db.model.impl.dimension;

import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 父子维度（Parent-Child Dimension）实现
 *
 * <p>父子维度提供三种访问视角：
 * <ul>
 *   <li><b>默认视角</b>（team$id, team$caption）：维度表通过 fact.team_id 直接关联，
 *       保持原有行为</li>
 *   <li><b>层级汇总视角</b>（team$hierarchy$id, team$hierarchy$caption）：维度表通过 closure.parent_id 关联，
 *       用于层级汇总查询，返回祖先节点信息</li>
 *   <li><b>明细视角</b>（team$self$id, team$self$caption）：维度表通过 fact.team_id 关联，
 *       用于查看后代明细，返回实际节点信息</li>
 * </ul>
 *
 * <p>查询示例：
 * <ul>
 *   <li>层级汇总：slice team$id=T001, groupBy team$hierarchy$caption → 返回 1 条（汇总到 T001）</li>
 *   <li>后代明细：slice team$id=T001, groupBy team$self$caption → 返回 N 条（各后代明细）</li>
 *   <li>精确匹配：slice team$self$id=T001 → 只查 T001 自身，不使用闭包表</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class DbModelParentChildDimensionImpl extends DbDimensionSupport {

    @ApiModelProperty("closure表的parent_id")
    String parentKey;

    @ApiModelProperty("closure表的child_id，例如team_id")
    String childKey;

    String closureTableName;

    DbColumn parentKeyJdbcColumn;
    DbColumn childKeyJdbcColumn;

    QueryObject closureQueryObject;

    // ========== 明细视角（self）相关字段 ==========

    /**
     * 明细视角的维度表 QueryObject
     * <p>与主 queryObject 共享同一个维度表，但使用不同的别名，
     * 用于通过 fact.team_id 直接关联（而非 closure.parent_id）
     */
    QueryObject selfQueryObject;

    /**
     * 明细视角的主键列（team$self$id）
     */
    DbColumn selfPrimaryKeyDbColumn;

    /**
     * 明细视角的标题列（team$self$caption）
     */
    DbColumn selfCaptionDbColumn;

    /**
     * 明细视角的属性列列表（team$self$xxx）
     */
    List<DimensionPropertyDbColumn> selfPropertyDbColumns = new ArrayList<>();

    // ========== 层级汇总视角（hierarchy）相关字段 ==========

    /**
     * 层级汇总视角的维度表 QueryObject
     * <p>与主 queryObject 共享同一个维度表，但使用不同的别名，
     * 用于通过 closure.parent_id 关联（实现层级汇总）
     */
    QueryObject hierarchyQueryObject;

    /**
     * 层级汇总视角的主键列（team$hierarchy$id）
     */
    DbColumn hierarchyPrimaryKeyDbColumn;

    /**
     * 层级汇总视角的标题列（team$hierarchy$caption）
     */
    DbColumn hierarchyCaptionDbColumn;

    /**
     * 层级汇总视角的属性列列表（team$hierarchy$xxx）
     */
    List<DimensionPropertyDbColumn> hierarchyPropertyDbColumns = new ArrayList<>();

    public DbModelParentChildDimensionImpl(String parentKey, String childKey, String closureTableName) {
        this.parentKey = parentKey;
        this.childKey = childKey;
        this.closureTableName = closureTableName;
    }

    @Override
    public void init() {
        super.init();
        // 初始化闭包表列
        parentKeyJdbcColumn = new DimensionDbColumn(closureQueryObject.getSqlColumn(parentKey, true));
        childKeyJdbcColumn = new DimensionDbColumn(closureQueryObject.getSqlColumn(childKey, true));

        // 初始化明细视角（self）列
        initSelfColumns();

        // 初始化层级汇总视角（hierarchy）列
        initHierarchyColumns();
    }

    /**
     * 初始化明细视角的列
     * <p>selfQueryObject 在 TableModelLoaderManagerImpl 中初始化
     */
    private void initSelfColumns() {
        if (selfQueryObject == null) {
            return;
        }

        // 明细视角的主键列：team$self$id
        selfPrimaryKeyDbColumn = new SelfDimensionPrimaryKeyDbColumn(
                selfQueryObject.getSqlColumn(primaryKey, true)
        );

        // 明细视角的标题列：team$self$caption
        selfCaptionDbColumn = new SelfDimensionCaptionDbColumn(
                selfQueryObject,
                selfQueryObject.getSqlColumn(captionColumn, true)
        );

        // 明细视角的属性列：team$self$xxx
        for (DbProperty property : jdbcProperties) {
            SqlColumn sqlColumn = selfQueryObject.getSqlColumn(
                    property.getPropertyDbColumn().getSqlColumn().getName(), true
            );
            SelfDimensionPropertyDbColumn pc = new SelfDimensionPropertyDbColumn(sqlColumn, property);
            selfPropertyDbColumns.add(pc);
        }
    }

    /**
     * 初始化层级汇总视角的列
     * <p>hierarchyQueryObject 在 TableModelLoaderManagerImpl 中初始化
     */
    private void initHierarchyColumns() {
        if (hierarchyQueryObject == null) {
            return;
        }

        // 层级汇总视角的主键列：team$hierarchy$id
        hierarchyPrimaryKeyDbColumn = new HierarchyDimensionPrimaryKeyDbColumn(
                hierarchyQueryObject.getSqlColumn(primaryKey, true)
        );

        // 层级汇总视角的标题列：team$hierarchy$caption
        hierarchyCaptionDbColumn = new HierarchyDimensionCaptionDbColumn(
                hierarchyQueryObject,
                hierarchyQueryObject.getSqlColumn(captionColumn, true)
        );

        // 层级汇总视角的属性列：team$hierarchy$xxx
        for (DbProperty property : jdbcProperties) {
            SqlColumn sqlColumn = hierarchyQueryObject.getSqlColumn(
                    property.getPropertyDbColumn().getSqlColumn().getName(), true
            );
            HierarchyDimensionPropertyDbColumn pc = new HierarchyDimensionPropertyDbColumn(sqlColumn, property);
            hierarchyPropertyDbColumns.add(pc);
        }
    }

    // ========== 明细视角的列类型定义 ==========

    /**
     * 明细视角的主键列（team$self$id）
     */
    public class SelfDimensionPrimaryKeyDbColumn extends DimensionDbColumnSupport implements DbColumn {
        String keyAlias;

        public SelfDimensionPrimaryKeyDbColumn(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        @Override
        public QueryObject getQueryObject() {
            return selfQueryObject;
        }

        @Override
        public String getAlias() {
            if (keyAlias == null) {
                keyAlias = getFullPathForAlias() + "$self$id";
            }
            return keyAlias;
        }

        @Override
        public String getName() {
            return getAlias();
        }

        @Override
        public String getCaption() {
            return keyCaption;
        }
    }

    /**
     * 明细视角的标题列（team$self$caption）
     */
    public class SelfDimensionCaptionDbColumn extends DimensionDbColumnSupport implements DbColumn {
        QueryObject queryObject;
        String captionAlias;

        public SelfDimensionCaptionDbColumn(QueryObject queryObject, SqlColumn sqlColumn) {
            super(sqlColumn);
            this.queryObject = queryObject;
        }

        @Override
        public QueryObject getQueryObject() {
            return queryObject;
        }

        @Override
        public String getName() {
            if (captionAlias == null) {
                captionAlias = getFullPathForAlias() + "$self$caption";
            }
            return captionAlias;
        }

        @Override
        public String getAlias() {
            return getName();
        }

        @Override
        public boolean isCaptionColumn() {
            return true;
        }
    }

    /**
     * 明细视角的属性列（team$self$xxx）
     */
    public class SelfDimensionPropertyDbColumn extends DimensionPropertyDbColumn {
        String selfAlias;

        public SelfDimensionPropertyDbColumn(SqlColumn sqlColumn, DbProperty property) {
            super(sqlColumn, property);
        }

        @Override
        public QueryObject getQueryObject() {
            return selfQueryObject;
        }

        @Override
        public String getAlias() {
            if (selfAlias == null) {
                String fullPathAlias = getFullPathForAlias();
                selfAlias = fullPathAlias + "$self$" + property.getPropertyDbColumn().getAlias();
            }
            return selfAlias;
        }

        @Override
        public String getName() {
            return getAlias();
        }
    }

    /**
     * 获取所有明细视角的列
     */
    public List<DbColumn> getSelfDbColumns() {
        List<DbColumn> columns = new ArrayList<>();
        if (selfPrimaryKeyDbColumn != null) {
            columns.add(selfPrimaryKeyDbColumn);
        }
        if (selfCaptionDbColumn != null) {
            columns.add(selfCaptionDbColumn);
        }
        columns.addAll(selfPropertyDbColumns);
        return columns;
    }

    // ========== 层级汇总视角的列类型定义 ==========

    /**
     * 层级汇总视角的主键列（team$hierarchy$id）
     */
    public class HierarchyDimensionPrimaryKeyDbColumn extends DimensionDbColumnSupport implements DbColumn {
        String keyAlias;

        public HierarchyDimensionPrimaryKeyDbColumn(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        @Override
        public QueryObject getQueryObject() {
            return hierarchyQueryObject;
        }

        @Override
        public String getAlias() {
            if (keyAlias == null) {
                keyAlias = getFullPathForAlias() + "$hierarchy$id";
            }
            return keyAlias;
        }

        @Override
        public String getName() {
            return getAlias();
        }

        @Override
        public String getCaption() {
            return keyCaption;
        }
    }

    /**
     * 层级汇总视角的标题列（team$hierarchy$caption）
     */
    public class HierarchyDimensionCaptionDbColumn extends DimensionDbColumnSupport implements DbColumn {
        QueryObject queryObject;
        String captionAlias;

        public HierarchyDimensionCaptionDbColumn(QueryObject queryObject, SqlColumn sqlColumn) {
            super(sqlColumn);
            this.queryObject = queryObject;
        }

        @Override
        public QueryObject getQueryObject() {
            return queryObject;
        }

        @Override
        public String getName() {
            if (captionAlias == null) {
                captionAlias = getFullPathForAlias() + "$hierarchy$caption";
            }
            return captionAlias;
        }

        @Override
        public String getAlias() {
            return getName();
        }

        @Override
        public boolean isCaptionColumn() {
            return true;
        }
    }

    /**
     * 层级汇总视角的属性列（team$hierarchy$xxx）
     */
    public class HierarchyDimensionPropertyDbColumn extends DimensionPropertyDbColumn {
        String hierarchyAlias;

        public HierarchyDimensionPropertyDbColumn(SqlColumn sqlColumn, DbProperty property) {
            super(sqlColumn, property);
        }

        @Override
        public QueryObject getQueryObject() {
            return hierarchyQueryObject;
        }

        @Override
        public String getAlias() {
            if (hierarchyAlias == null) {
                String fullPathAlias = getFullPathForAlias();
                hierarchyAlias = fullPathAlias + "$hierarchy$" + property.getPropertyDbColumn().getAlias();
            }
            return hierarchyAlias;
        }

        @Override
        public String getName() {
            return getAlias();
        }
    }

    /**
     * 获取所有层级汇总视角的列
     */
    public List<DbColumn> getHierarchyDbColumns() {
        List<DbColumn> columns = new ArrayList<>();
        if (hierarchyPrimaryKeyDbColumn != null) {
            columns.add(hierarchyPrimaryKeyDbColumn);
        }
        if (hierarchyCaptionDbColumn != null) {
            columns.add(hierarchyCaptionDbColumn);
        }
        columns.addAll(hierarchyPropertyDbColumns);
        return columns;
    }
}
