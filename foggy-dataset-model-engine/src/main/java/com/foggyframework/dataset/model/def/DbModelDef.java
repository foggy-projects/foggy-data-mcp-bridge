package com.foggyframework.dataset.model.def;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.def.dimension.DbDimensionDef;
import com.foggyframework.dataset.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.model.spi.DbModelType;
import lombok.Data;

import javax.sql.DataSource;
import java.util.List;

@Data
public class DbModelDef extends DbDefSupport {

    String idColumn;

    DataSource dataSource;

    /**
     * Named data source reference (e.g., "odoo").
     * When specified, the model will use the named data source registered via DataSource API.
     * This takes precedence over the default dataSource.
     *
     * @since 8.2.0
     */
    String dataSourceName;

    boolean autoLoadDimensions;

    boolean autoLoadMeasures;

    Object importDimensions;

    Object importMeasures;

    List<DbDimensionDef> dimensions;

    List<DbPropertyDef> properties;

    List<DbMeasureDef> measures;

    String tableName;

    String viewSql;

    String schema;

    String type;

    Object mongoTemplate;

    /**
     * 向量数据库配置（用于 vector 类型模型）
     */
    Object vectorConfig;

    /**
     * 预聚合配置（P1 预聚合功能）
     * <p>
     * 定义预聚合表，用于加速聚合查询。
     * </p>
     *
     * @since 8.2.0
     */
    List<PreAggregationDef> preAggregations;

    /**
     * TM-level dynamic field permission upper bound.
     */
    FieldPermissionsDef fieldPermissions;

    public void apply(TableModelSupport jdbcObjectSupport) {
        super.apply(jdbcObjectSupport);
        jdbcObjectSupport.setIdColumn(this.idColumn);
        jdbcObjectSupport.setTableName(tableName);
        jdbcObjectSupport.setFieldPermissions(fieldPermissions);
        if(StringUtils.isNotEmpty(type)){
            jdbcObjectSupport.setModelType(DbModelType.valueOf(type));
        }else{
            jdbcObjectSupport.setModelType(DbModelType.jdbc);
        }
    }

}
