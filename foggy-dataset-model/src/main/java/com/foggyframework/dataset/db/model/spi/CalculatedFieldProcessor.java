package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 计算字段处理器接口
 * <p>
 * 定义处理计算字段的能力，由不同的 QueryModel 实现提供。
 * JDBC 使用 SQL 表达式，MongoDB 使用聚合管道表达式。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public interface CalculatedFieldProcessor {

    /**
     * 处理计算字段定义列表，生成可用于查询的计算列
     *
     * @param calculatedFields 计算字段定义列表
     * @param appCtx           Spring 应用上下文
     * @return 计算字段列列表
     */
    List<CalculatedDbColumn> processCalculatedFields(
            List<CalculatedFieldDef> calculatedFields,
            ApplicationContext appCtx
    );

    /**
     * 处理单个计算字段
     *
     * @param fieldDef 计算字段定义
     * @param appCtx   Spring 应用上下文
     * @return 计算字段列
     */
    CalculatedDbColumn processCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx
    );
}
