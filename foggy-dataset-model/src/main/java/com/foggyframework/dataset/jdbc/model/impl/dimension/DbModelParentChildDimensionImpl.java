package com.foggyframework.dataset.jdbc.model.impl.dimension;

import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import com.foggyframework.dataset.jdbc.model.spi.QueryObject;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public DbModelParentChildDimensionImpl(String parentKey, String childKey, String closureTableName) {
        this.parentKey = parentKey;
        this.childKey = childKey;
        this.closureTableName = closureTableName;
    }

    @Override
    public void init() {
        super.init();
        parentKeyJdbcColumn = new DimensionDbColumn(closureQueryObject.getSqlColumn(parentKey, true));
        childKeyJdbcColumn = new DimensionDbColumn(closureQueryObject.getSqlColumn(childKey, true));
    }

    
}
