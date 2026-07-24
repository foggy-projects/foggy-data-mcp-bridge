package com.foggyframework.dataset.model.def.query.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 窗口函数排序定义
 *
 * @author Foggy
 * @since 8.4.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("窗口排序定义")
public class WindowOrderDef {

    @ApiModelProperty(value = "排序字段名", required = true, example = "salesAmount")
    private String field;

    @ApiModelProperty(value = "排序方向", notes = "asc 或 desc，默认 asc", example = "desc")
    private String dir;

    /**
     * 获取标准化的排序方向
     */
    public String getNormalizedDir() {
        if (dir == null || dir.isEmpty()) {
            return "ASC";
        }
        return dir.toUpperCase();
    }
}
