/**
 * 查询模板向量存储 - 示例 TM 文件
 *
 * 用于存储和检索查询模板及指导文档
 */

// 导出向量存储实例（可选，默认使用系统的 vectorStore bean）
// export const vectorStore = ...;

// 构建查询函数
export function buildQuery(params) {
    // 从 DSL 查询参数中提取查询文本
    const sliceConditions = params.slice || [];
    const queryCondition = sliceConditions.find(s => s.name === 'query' || s.name === 'content');

    if (queryCondition) {
        return queryCondition.value;
    }

    // 默认查询
    return params.defaultQuery || '';
}

// 配置 topK（可选，默认 10）
export const topK = 10;

// 配置相似度阈值（可选，默认 0.7）
export const threshold = 0.7;
