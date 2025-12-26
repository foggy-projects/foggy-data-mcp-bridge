package com.foggyframework.dataset.db.model.def.query.request;

import com.foggyframework.dataset.db.model.spi.DbQueryRequest;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Optional;

@Data
public class DbQueryRequestDef implements DbQueryRequest {

    @ApiModelProperty(value = "查询列", notes = "为未传，或为空，则查出当前操作人有权限的所有列")
    List<String> columns;

    @ApiModelProperty(value = "排除列", notes = "注意，排除后，将不会将该列的数据在columns返回")
    List<String> exColumns;

    @ApiModelProperty(value = "查询模型", notes = "需要查询的【查询模型】名称")
    String queryModel;

    @ApiModelProperty(value = "过滤条件", notes = "过滤条件，使用方式见https://sdsh.yuque.com/xl6fiq/mbuyho/pq81w1gpz4k3mysk?singleDoc# 《后端Api》")
    List<SliceRequestDef> slice;

    @ApiModelProperty(value = "", notes = "排序，使用方式见https://sdsh.yuque.com/xl6fiq/mbuyho/pq81w1gpz4k3mysk?singleDoc# 《后端Api》")
    List<OrderRequestDef> orderBy;

    @ApiModelProperty(value = "", notes = "分组")
    List<GroupRequestDef> groupBy;

    @ApiModelProperty(value = "动态计算字段", notes = "在查询时定义的计算字段，可在 columns/groupBy 中引用")
    List<CalculatedFieldDef> calculatedFields;

    @ApiModelProperty("是否返回总数及合计")
    boolean returnTotal;

    /**
     * @deprecated 已废弃，系统始终自动处理 groupBy。此字段保留仅为 API 兼容性。
     */
    @ApiModelProperty(value = "自动补充groupBy（已废弃）", notes = "此参数已废弃，系统始终自动处理 groupBy")
    @Deprecated
    boolean autoGroupBy;

    /**
     * 是否启用自动 GroupBy 处理
     * <p>
     * 始终返回 true。系统会自动分析 columns 中的聚合表达式，
     * 并将非聚合列自动加入 groupBy。
     * </p>
     *
     * @return 始终为 true
     */
    public boolean isAutoGroupBy() {
        return true;
    }

    @ApiModelProperty("查询扩展数据，前后端约定后，由前端传入")
    Object extData;

    String queryId;

    @ApiModelProperty("严格按columns中的列返回")
    @Deprecated
    boolean strictColumns;

    @ApiModelProperty("当查询中的维度不带$caption或$id时，自动加入$caption")
    @Deprecated
    boolean autoFixDimCaption;

    public boolean hasGroupBy() {
        return groupBy != null && !groupBy.isEmpty();
    }

    public Object getSliceValueByName(String name, boolean errorIfNotFound) {
        Optional<SliceRequestDef> opt = slice.stream().filter(s -> s.field.equals(name)).findFirst();

        if (errorIfNotFound) {
            return opt.orElseThrow(() -> new RuntimeException("slice not found: " + name)).getValue();
        } else {
            return opt.orElse(null);
        }
    }
}
