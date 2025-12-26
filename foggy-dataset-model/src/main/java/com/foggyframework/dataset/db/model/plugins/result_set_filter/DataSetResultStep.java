package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.filter.FoggyStep;

/**
 * 数据集结果处理步骤
 *
 * <p>通过返回值控制执行流程，可分别处理查询前和查询后的逻辑。
 *
 * <h3>返回值说明：</h3>
 * <ul>
 *   <li>{@link #CONTINUE} (0): 继续执行下一个步骤</li>
 *   <li>{@link #ABORT} (1): 中止执行链（非错误情况）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @Component
 * public class AuthorizationStep implements DataSetResultStep {
 *
 *     @Override
 *     public int beforeQuery(ModelResultContext ctx) {
 *         // 根据权限添加过滤条件
 *         SecurityContext security = ctx.getSecurityContext();
 *         if (security != null) {
 *             // 添加数据权限条件...
 *         }
 *         return CONTINUE;
 *     }
 *
 *     @Override
 *     public int process(ModelResultContext ctx) {
 *         // 结果处理（如脱敏）
 *         return CONTINUE;
 *     }
 * }
 * }</pre>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
public interface DataSetResultStep extends FoggyStep<ModelResultContext> {

    /**
     * 查询执行前处理
     *
     * <p>在此方法中可以：
     * <ul>
     *   <li>根据权限添加过滤条件</li>
     *   <li>修改查询参数</li>
     *   <li>验证请求合法性</li>
     * </ul>
     *
     * @param ctx 上下文，包含请求信息和安全上下文
     * @return CONTINUE 继续，ABORT 中止
     */
    default int beforeQuery(ModelResultContext ctx) {
        return CONTINUE;
    }

    /**
     * 查询结果处理
     *
     * <p>在此方法中可以：
     * <ul>
     *   <li>数据格式转换（如金额分转元）</li>
     *   <li>敏感数据脱敏</li>
     *   <li>结果集过滤</li>
     * </ul>
     *
     * @param ctx 上下文，包含查询结果
     * @return CONTINUE 继续，ABORT 中止
     */
    @Override
    default int process(ModelResultContext ctx) {
        return CONTINUE;
    }
}
