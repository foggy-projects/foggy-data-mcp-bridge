package com.foggyframework.dataset.db.model.def;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.dimension.DbDimensionDef;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.spi.DbModelType;
import lombok.Data;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;
import java.util.List;

@Data
public class DbModelDef extends DbDefSupport {

    String idColumn;

    DataSource dataSource;

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

    MongoTemplate mongoTemplate;

    public void apply(TableModelSupport jdbcObjectSupport) {
        super.apply(jdbcObjectSupport);
        jdbcObjectSupport.setIdColumn(this.idColumn);
        jdbcObjectSupport.setTableName(tableName);
        if(StringUtils.isNotEmpty(type)){
            jdbcObjectSupport.setModelType(DbModelType.valueOf(type));
        }else{
            jdbcObjectSupport.setModelType(DbModelType.jdbc);
        }
    }

}
