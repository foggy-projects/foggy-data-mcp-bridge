/**
 * 查询模板向量存储 - 表模型
 *
 * @description 用于存储和检索查询模板及指导文档，支持语义相似度搜索
 */

export const model = {
    name: 'QueryTemplateVectorModel',
    caption: '查询模板向量库',
    tableName: 'query_templates',  // 向量集合名称
    idColumn: 'id',

    // 属性定义
    properties: [
        {
            column: 'id',
            caption: '文档ID',
            type: 'STRING',
            description: '唯一标识符'
        },
        {
            column: 'content',
            caption: '文档内容',
            type: 'TEXT',
            description: '查询模板或指导文档的文本内容（用于向量化）'
        },
        {
            column: 'template_type',
            caption: '模板类型',
            type: 'STRING',
            description: 'dsl=DSL查询模板, guide=查询指导文档'
        },
        {
            column: 'model_name',
            caption: '关联模型',
            type: 'STRING',
            description: '适用的数据模型名称'
        },
        {
            column: 'tags',
            caption: '标签',
            type: 'STRING',
            description: '分类标签，如：销售、库存、财务'
        },
        {
            column: 'usage_count',
            caption: '使用次数',
            type: 'INTEGER',
            description: '模板被使用的次数'
        }
    ],

    // 度量定义
    measures: [
        {
            column: 'similarity',
            name: 'similarity',
            caption: '相似度',
            type: 'NUMBER',
            description: '检索时的相似度分数（0-1）'
        }
    ]
};

// 向量查询配置
export const vectorConfig = {
    topK: 10,
    threshold: 0.7
};

// 构建查询函数 - 从 DSL 参数中提取查询文本
export function buildQuery(params) {
    const sliceConditions = params.slice || [];

    // 查找使用 similar 操作符的条件
    const similarCondition = sliceConditions.find(s => s.type === 'similar');
    if (similarCondition) {
        return similarCondition.value;
    }

    // 兼容：查找 query 或 content 字段
    const queryCondition = sliceConditions.find(s =>
        s.name === 'query' || s.name === 'content'
    );

    if (queryCondition) {
        return queryCondition.value;
    }

    return params.defaultQuery || '';
}
