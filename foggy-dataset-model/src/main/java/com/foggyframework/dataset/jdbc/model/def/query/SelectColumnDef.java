package com.foggyframework.dataset.jdbc.model.def.query;

import com.foggyframework.dataset.jdbc.model.def.AiDef;
import lombok.Data;

import java.util.Map;

@Data
public class SelectColumnDef {

    AiDef ai;

    String name;

    String ref;

    String alias;

    String field;
    String caption;

//    String type;
//    Object value;
    /**
     * UI配置
     */
    Map<String,Object> ui;

}
