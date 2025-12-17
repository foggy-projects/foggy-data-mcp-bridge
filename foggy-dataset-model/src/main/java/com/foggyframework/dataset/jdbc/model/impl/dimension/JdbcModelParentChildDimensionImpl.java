package com.foggyframework.dataset.jdbc.model.impl.dimension;

import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.QueryObject;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JdbcModelParentChildDimensionImpl extends JdbcDimensionSupport {

    @ApiModelProperty("closure表的parent_id")
    String parentKey;

    @ApiModelProperty("closure表的child_id，例如team_id")
    String childKey;

    String closureTableName;

    JdbcColumn parentKeyJdbcColumn;
    JdbcColumn childKeyJdbcColumn;

    QueryObject closureQueryObject;

    public JdbcModelParentChildDimensionImpl(String parentKey, String childKey, String closureTableName) {
        this.parentKey = parentKey;
        this.childKey = childKey;
        this.closureTableName = closureTableName;
    }

    @Override
    public void init() {
        super.init();
        parentKeyJdbcColumn = new DimensionJdbcColumn(closureQueryObject.getSqlColumn(parentKey, true));
        childKeyJdbcColumn = new DimensionJdbcColumn(closureQueryObject.getSqlColumn(childKey, true));
    }

    
}
