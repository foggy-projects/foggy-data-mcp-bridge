package com.foggyframework.dataset.db.model.def;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

@Data
public class AiDef {
    @ApiModelProperty("是否激活AI分析，默认null和true为激活，")
    Boolean enabled;

    @ApiModelProperty("提示词，如果填写了，会替代原来的description")
    String prompt ;

    @ApiModelProperty("激活等级列表,字段可以属于多个级别，如[1,2]表示该字段同时属于级别1和级别2")
    List<Integer> levels;

}
