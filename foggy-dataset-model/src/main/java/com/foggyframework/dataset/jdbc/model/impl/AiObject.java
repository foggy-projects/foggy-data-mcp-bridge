package com.foggyframework.dataset.jdbc.model.impl;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.utils.NumberUtils;
import com.foggyframework.dataset.jdbc.model.def.AiDef;
import com.foggyframework.dataset.jdbc.model.spi.JdbcObject;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@Data
public  class AiObject implements Serializable {

    private static final long serialVersionUID = 1L;


    @ApiModelProperty("提示词，如果填写了，会替代原来的description")
    String prompt ;

    @ApiModelProperty("激活等级列表,字段可以属于多个级别，如[1,2]表示该字段同时属于级别1和级别2")
    List<Integer> levels;

    @ApiModelProperty("是否激活AI分析")
    protected boolean enabled;


    public static AiObject of(AiDef ai) {
        AiObject aiObject = new AiObject();
        if(ai.getEnabled() == null){
            aiObject.setEnabled(true);
        }else{
            aiObject.setEnabled(ai.getEnabled());
        }

        aiObject.setPrompt(ai.getPrompt());
        
        // 处理levels
        if (ai.getLevels() != null && !ai.getLevels().isEmpty()) {
            aiObject.setLevels(ai.getLevels());
        } else {
            // 默认为level 1
            aiObject.setLevels(Arrays.asList(1));
        }

        return aiObject;
    }

}
