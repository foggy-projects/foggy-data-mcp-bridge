package com.foggyframework.dataset.db.model.def.query;

import com.foggyframework.dataset.db.model.def.AiDef;
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
