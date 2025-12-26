package com.foggyframework.dataset.jdbc.model.def.dimension;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.DbDefSupport;
import com.foggyframework.dataset.jdbc.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.jdbc.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.jdbc.model.spi.DbDimensionType;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Data
public class DbDimensionDef extends DbDefSupport {
    @ApiModelProperty("维表")
    String tableName;
    @ApiModelProperty(value = "视图SQL", notes = "视图sql，和维表，只取一个，优先使用tableName")
    String viewSql;

    String foreignKey;

    @ApiModelProperty("强制索引")
    String forceIndex;

    String primaryKey;

    String closureTableName;
    String closureTableSchema;
    String parentKey;
    String childKey;

    @ApiModelProperty(value = "维表的主键字段的caption",notes = "如果没有指定，默认使用${caption}Key,例如产品，叫产品Key")
    String keyCaption;

    @ApiModelProperty(value = "维表的主键字段的description", notes = "用于描述$id字段的详细说明，如格式、取值范围等")
    String keyDescription;

    String captionColumn;
    String type;
    String schema;
    Map<String, Object> extData;

    List<DbPropertyDef> properties;

    FsscriptFunction dimensionDataSql;

    FsscriptFunction onBuilder;

    @ApiModelProperty("维度的别名，用于在QM中重新定义列名前缀，避免嵌套路径过长")
    String alias;

    @ApiModelProperty("嵌套子维度列表，形成雪花结构。子维度的foreignKey指向父维度表上的列")
    List<DbDimensionDef> dimensions;

    @Deprecated
    @ApiModelProperty("已废弃，请使用嵌套维度方式。使某个维度与其他维度关联，形成多级结构")
    String joinTo;

    DataSource dataSource;

    public void apply(DbDimensionSupport dimension) {
        super.apply(dimension);
        BeanUtils.copyProperties(this, dimension, "type"); // 排除 type，因为类型不同
        dimension.setAlias(alias);
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            dimension.setType(DbDimensionType.fromString(type));
        }
    }
}
