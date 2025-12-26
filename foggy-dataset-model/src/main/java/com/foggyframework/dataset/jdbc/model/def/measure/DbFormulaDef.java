package com.foggyframework.dataset.jdbc.model.def.measure;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class DbFormulaDef {

    @ApiModelProperty("公式，例如loadingValue + nodeTotalUnloadingValue，基于度量名称定义")
    String value;


    @ApiModelProperty("公式，例如loadingValue + nodeTotalUnloadingValue，基于度量名称定义")
    FsscriptFunction builder;

    @ApiModelProperty("如：发站装车费 + 所有途经点的到站卸车费，展示给客户看的")
    String description;

}
