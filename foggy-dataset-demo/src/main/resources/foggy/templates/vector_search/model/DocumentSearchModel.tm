/**
 * 文档搜索向量模型定义
 *
 * @description 向量数据库演示 - 文档语义搜索
 *              支持基于自然语言的相似度检索
 */

export const model = {
    name: 'DocumentSearchModel',
    caption: '文档搜索',
    tableName: 'documents',  // Milvus collection 名称
    type: 'vector',          // 模型类型为向量

    // 属性定义
    properties: [
        {
            column: 'doc_id',
            name: 'docId',
            caption: '文档ID',
            type: 'TEXT',
            description: '文档唯一标识'
        },
        {
            column: 'title',
            name: 'title',
            caption: '标题',
            type: 'TEXT',
            description: '文档标题'
        },
        {
            column: 'content',
            name: 'content',
            caption: '内容',
            type: 'TEXT',
            description: '文档内容摘要'
        },
        {
            column: 'category',
            name: 'category',
            caption: '分类',
            type: 'TEXT',
            description: '文档分类：report/manual/faq/article'
        },
        {
            column: 'author',
            name: 'author',
            caption: '作者',
            type: 'TEXT',
            description: '文档作者'
        },
        {
            column: 'created_at',
            name: 'createdAt',
            caption: '创建时间',
            type: 'DATETIME',
            description: '文档创建时间'
        },
        {
            column: 'updated_at',
            name: 'updatedAt',
            caption: '更新时间',
            type: 'DATETIME',
            description: '文档最后更新时间'
        },
        // 向量字段
        {
            column: 'embedding',
            name: 'embedding',
            caption: '文档向量',
            type: 'VECTOR',
            dimensions: 1536,     // OpenAI text-embedding-3-small 维度
            metric: 'cosine',     // 相似度度量：余弦相似度
            description: '文档内容的向量表示，用于语义相似度检索'
        }
    ],

    // 向量模型不支持度量（聚合），但可以定义用于返回的评分字段
    measures: []
};
