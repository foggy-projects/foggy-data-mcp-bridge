package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

/**
 * 语义查询金额处理步骤
 *
 * <p>将 MONEY 类型字段从分转换为元（仅在语义查询模式下）。
 *
 * @author foggy-framework
 * @since 8.0.0
 */
public class SemanticMoneyStep implements DataSetResultStep {

    @Override
    public int process(ModelResultContext ctx) {
        // 暂时注释，保留原逻辑供参考
//        if (ctx.getQueryType() == ModelResultContext.QueryType.SEMANTIC) {
//            // 需要将money从分转换成元
//            for (JdbcColumn column : ctx.getJdbcQuery().getSelect().getColumns()) {
//                if (column.isMeasure() && StringUtils.equals(column.getType(), JdbcColumnType.MONEY)) {
//                    String name = column.getName();
//                    for (Object item : ctx.getPagingResult().getItems()) {
//                        if (item instanceof Map) {
//                            Map mm = (Map) item;
//                            Number v = (Number) mm.get(name);
//                            if (v != null) {
//                                mm.put(name, NumberUtils.toFixed(v.doubleValue() / 100.0, 2));
//                            }
//                        }
//                    }
//                }
//            }
//        }
        return CONTINUE;
    }

    @Override
    public int order() {
        // 金额转换应该在较后执行
        return -100;
    }
}
