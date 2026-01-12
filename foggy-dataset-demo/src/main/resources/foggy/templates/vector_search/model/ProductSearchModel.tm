/**
 * 商品语义搜索向量模型定义
 *
 * @description 向量数据库演示 - 商品语义搜索
 *              支持基于自然语言描述查找相似商品
 */

export const model = {
    name: 'ProductSearchModel',
    caption: '商品搜索',
    tableName: 'product_embeddings',  // Milvus collection 名称
    type: 'vector',                    // 模型类型为向量

    // 属性定义
    properties: [
        {
            column: 'product_id',
            name: 'productId',
            caption: '商品ID',
            type: 'TEXT',
            description: '商品唯一标识'
        },
        {
            column: 'product_name',
            name: 'productName',
            caption: '商品名称',
            type: 'TEXT',
            description: '商品名称'
        },
        {
            column: 'description',
            name: 'description',
            caption: '商品描述',
            type: 'TEXT',
            description: '商品详细描述'
        },
        {
            column: 'category',
            name: 'category',
            caption: '品类',
            type: 'TEXT',
            description: '商品品类'
        },
        {
            column: 'brand',
            name: 'brand',
            caption: '品牌',
            type: 'TEXT',
            description: '商品品牌'
        },
        {
            column: 'price',
            name: 'price',
            caption: '价格',
            type: 'MONEY',
            description: '商品售价'
        },
        {
            column: 'tags',
            name: 'tags',
            caption: '标签',
            type: 'TEXT',
            description: '商品标签，逗号分隔'
        },
        // 向量字段
        {
            column: 'embedding',
            name: 'embedding',
            caption: '商品向量',
            type: 'VECTOR',
            dimensions: 1536,
            metric: 'cosine',
            description: '商品描述的向量表示，用于语义相似度检索'
        }
    ],

    measures: []
};
