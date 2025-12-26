package com.foggyframework.dataset.jdbc.model.def;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.dimension.JdbcDimensionDef;
import com.foggyframework.dataset.jdbc.model.def.measure.JdbcMeasureDef;
import com.foggyframework.dataset.jdbc.model.def.property.JdbcPropertyDef;
import com.foggyframework.dataset.jdbc.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.jdbc.model.spi.DbModelType;
import lombok.Data;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;
import java.util.List;

@Data
public class JdbcModelDef extends JdbcDefSupport{

    String idColumn;

    DataSource dataSource;

    boolean autoLoadDimensions;

    boolean autoLoadMeasures;

    Object importDimensions;

    Object importMeasures;

    List<JdbcDimensionDef> dimensions;

    List<JdbcPropertyDef> properties;

    List<JdbcMeasureDef> measures;

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
