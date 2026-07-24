package com.foggyframework.dataset.model.def.dimension;

import com.foggyframework.dataset.model.def.measure.DbFormulaDef;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 维度 caption 定义对象。
 * <p>
 * 支持三种优先级：
 * <ol>
 *     <li>{@code dialectFormulaDef[dbType]} — 方言专属公式（最高优先级）</li>
 *     <li>{@code formulaDef} — 通用公式</li>
 *     <li>{@code column} — 直接列引用（最低优先级 / 默认）</li>
 * </ol>
 * <p>
 * TM 用法示例：
 * <pre>
 * // 简单列引用（等价于旧 captionColumn: 'name'）
 * captionDef: { column: 'name' }
 *
 * // 通用公式（跨方言安全）
 * captionDef: {
 *     column: 'name',
 *     formulaDef: {
 *         builder: (alias) => `COALESCE(${alias}.display_name, ${alias}.name)`,
 *         description: '优先显示 display_name'
 *     }
 * }
 *
 * // 方言专属公式（JSONB 提取）
 * captionDef: {
 *     column: 'name',
 *     dialectFormulaDef: {
 *         postgresql: {
 *             builder: (alias) => `${alias}.name ->> 'en_US'`,
 *             description: '从 JSONB 提取英文名称'
 *         },
 *         mysql: {
 *             builder: (alias) => `${alias}.name ->> '$.en_US'`,
 *             description: '从 JSON 提取英文名称'
 *         }
 *     }
 * }
 * </pre>
 */
@Data
public class DbCaptionDef {

    @ApiModelProperty(value = "caption 列名", notes = "回退列名，当无公式匹配时使用")
    String column;

    @ApiModelProperty(value = "通用公式", notes = "跨方言的 SQL 构建函数，优先级低于 dialectFormulaDef")
    DbFormulaDef formulaDef;

    @ApiModelProperty(value = "方言专属公式", notes = "key 为方言标识（postgresql/mysql/sqlserver/sqlite/oracle），value 为该方言的公式定义")
    Map<String, DbFormulaDef> dialectFormulaDef;
}
