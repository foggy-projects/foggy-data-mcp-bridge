package com.foggyframework.dataset.db.model.def.property;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.DbDefSupport;
import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.Map;

@Data
public class DbPropertyDef extends DbDefSupport {

    String alias;

    String column;

    String type;

    String format;

    String aggregationFormula;

    @ApiModelProperty("公式描述")
    DbFormulaDef formulaDef;

    @ApiModelProperty(value = "方言专属公式", notes = "key 为方言标识（postgresql/mysql/sqlserver/sqlite/oracle），value 为该方言的公式定义。优先级高于 formulaDef")
    Map<String, DbFormulaDef> dialectFormulaDef;

    @ApiModelProperty(value = "字典引用", notes = "引用通过 registerDict 注册的字典ID，用于将数据库中的值转换为显示标签")
    String dictRef;

    /**
     * 向量维度（仅用于 VECTOR 类型字段）
     */
    @ApiModelProperty(value = "向量维度", notes = "仅用于 VECTOR 类型字段，指定向量的维度大小")
    Integer dimensions;

    /**
     * 向量相似度度量类型（仅用于 VECTOR 类型字段）
     * 可选值: cosine, euclidean, dotProduct
     */
    @ApiModelProperty(value = "相似度度量类型", notes = "仅用于 VECTOR 类型字段，可选值: cosine, euclidean, dotProduct")
    String metric;

    public void apply(DbPropertyImpl property) {
        super.apply(property);
        BeanUtils.copyProperties(this, property, "type"); // 排除 type，因为类型不同
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            property.setType(DbColumnType.fromCode(type));
        }
    }
}
