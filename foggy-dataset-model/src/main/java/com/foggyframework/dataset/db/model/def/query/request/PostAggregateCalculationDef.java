package com.foggyframework.dataset.db.model.def.query.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Post-aggregate calculated alias definition.
 *
 * <p>These calculations run after grouped aggregate aliases have been selected.
 * They intentionally do not share the same runtime bucket as CalculatedDbColumn,
 * whose expressions are compiled into the inner SELECT stage.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostAggregateCalculationDef {

    @ApiModelProperty(value = "Output alias", example = "salesShare")
    private String name;

    @ApiModelProperty(value = "Calculation kind", example = "ratioToTotal")
    private String kind;

    @ApiModelProperty(value = "Selected aggregate alias", example = "teamSales")
    private String measure;

    @ApiModelProperty(value = "Calculation scope. v1.6 supports grandTotal only", example = "grandTotal")
    private String scope = "grandTotal";

    @ApiModelProperty(value = "Output format. v1.6 supports ratio or percent", example = "ratio")
    private String format = "ratio";
}
