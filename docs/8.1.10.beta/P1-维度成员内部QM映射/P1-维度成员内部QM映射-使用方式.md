# P1-维度成员内部QM映射 使用方式

本文档说明 synthetic member-QM 的实际用法，覆盖：

- 如何确定 synthetic member-QM 名称
- 如何获取它的 schema
- 如何通过 direct DSL 查询它
- 如何通过 simple 入口查询维度成员
- 返回结果长什么样

## 1. 命名规则

synthetic member-QM 的命名固定为：

```text
${sourceQueryModel}#${dimFieldBase}
```

示例：

- `FactSalesNestedDimQueryModel#product`
- `FactTeamSalesQueryModel#team`

其中 `dimFieldBase` 必须来自 QM 中暴露出来的根维度基名，而不是 TM 原始维度名。

## 2. schema 长什么样

### 2.1 根维度 schema

以 `FactSalesQueryModel#product` 为例，schema 会把根维度映射成 canonical 字段：

```json
{
  "fields": [
    "id",
    "caption",
    "productId",
    "categoryId",
    "categoryName",
    "subCategoryId",
    "subCategoryName",
    "brand",
    "unitPrice",
    "unitCost"
  ]
}
```

- `id` 对应源字段 `product$id`
- `caption` 对应源字段 `product$caption`
- 其他字段来自 TM 中该维度的全部属性

### 2.2 嵌套子维 schema

以 `FactSalesNestedDimQueryModel#product` 为例，根维度 `product` 下有嵌套维 `productCategory`，继续往下还有 `categoryGroup`：

```json
{
  "fields": [
    "id",
    "caption",
    "productId",
    "brand",
    "unitPrice",
    "productCategory$id",
    "productCategory$caption",
    "productCategory$categoryId",
    "productCategory$categoryLevel",
    "productCategory$categoryGroup$id",
    "productCategory$categoryGroup$caption",
    "productCategory$categoryGroup$groupId",
    "productCategory$categoryGroup$groupType"
  ]
}
```

规则是：

- 根维度自身去前缀，统一暴露成 `id`、`caption` 和属性名
- 内嵌维度保留相对路径前缀，例如 `productCategory$caption`
- 更深层路径继续递归展开，例如 `productCategory$categoryGroup$groupType`

### 2.3 父子维 schema

以 `FactTeamSalesQueryModel#team` 为例，除了普通属性外，还会附加 hierarchy 保留字段：

```json
{
  "fields": [
    "id",
    "caption",
    "teamId",
    "teamName",
    "parentId",
    "teamLevel",
    "managerName",
    "status",
    "depth",
    "hasChildren"
  ]
}
```

## 3. 如何获取 schema

### 3.1 推荐：通过语义 metadata 接口获取

接口：

```text
POST /semantic/v1/metadata?format=json
```

请求体：

```json
{
  "qmModels": ["FactSalesNestedDimQueryModel#product"],
  "levels": [1, 2, 3],
  "includeExamples": false
}
```

响应示例：

```json
{
  "code": 0,
  "data": {
    "format": "json",
    "data": {
      "models": [
        {
          "name": "FactSalesNestedDimQueryModel#product",
          "caption": "商品成员",
          "columns": [
            { "name": "id", "title": "商品ID", "dataType": "STRING" },
            { "name": "caption", "title": "商品", "dataType": "STRING" },
            { "name": "brand", "title": "品牌", "dataType": "STRING" },
            { "name": "unitPrice", "title": "商品售价", "dataType": "MONEY" },
            { "name": "productCategory$caption", "title": "品类", "dataType": "STRING" },
            { "name": "productCategory$categoryGroup$groupType", "title": "组类型", "dataType": "STRING" }
          ]
        }
      ]
    }
  },
  "msg": "success"
}
```

适用场景：

- 前端加载筛选器 schema
- 网关或插件在注入列权限前先拿到允许字段全集
- 调试 synthetic member-QM 是否按预期展开了维度子树

### 3.2 内部 Java 方式

如果是在服务端内部直接使用，可以通过 `QueryModelLoader` 获取运行时 synthetic QM：

```java
QueryModel qm = queryModelLoader.getJdbcQueryModel(
        "FactSalesNestedDimQueryModel#product",
        null
);

List<String> columns = qm.getJdbcQueryColumns().stream()
        .map(DbQueryColumn::getName)
        .toList();
```

返回的列名将类似：

```json
[
  "id",
  "caption",
  "brand",
  "unitPrice",
  "productCategory$id",
  "productCategory$caption",
  "productCategory$categoryGroup$groupType"
]
```

## 4. direct DSL 查询

### 4.1 直接查询 synthetic member-QM

接口：

```text
POST /jdbc-model/query-model/v2/FactSalesNestedDimQueryModel#product
```

请求体：

```json
{
  "start": 0,
  "limit": 20,
  "param": {
    "columns": [
      "id",
      "caption",
      "brand",
      "productCategory$caption",
      "productCategory$categoryGroup$groupType"
    ],
    "slice": [
      {
        "field": "productCategory$categoryGroup$groupType",
        "op": "=",
        "value": "高价值"
      }
    ],
    "orderBy": [
      {
        "field": "productCategory$caption",
        "dir": "ASC"
      },
      {
        "field": "caption",
        "dir": "ASC"
      }
    ],
    "returnTotal": true
  }
}
```

返回示例：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "P001",
        "caption": "iPhone 15 Pro",
        "brand": "Apple",
        "productCategory$caption": "手机",
        "productCategory$categoryGroup$groupType": "高价值"
      },
      {
        "id": "P002",
        "caption": "Mate 60 Pro",
        "brand": "Huawei",
        "productCategory$caption": "手机",
        "productCategory$categoryGroup$groupType": "高价值"
      }
    ],
    "total": 2,
    "totalData": {
      "total": 2
    }
  },
  "msg": "success"
}
```

关键点：

- 这里查询根已经切到维度表，不再回到事实表
- 只能查 `product` 根维度及其内嵌维度子树字段
- 原业务字段如 `salesAmount`、`store$caption` 不允许出现在 `columns/slice/orderBy`

### 4.2 父子维 direct DSL 查询

接口：

```text
POST /jdbc-model/query-model/v2/FactTeamSalesQueryModel#team
```

请求体：

```json
{
  "start": 0,
  "limit": 20,
  "param": {
    "columns": ["id", "caption", "parentId", "teamLevel"],
    "slice": [
      {
        "field": "id",
        "op": "descendantsOf",
        "value": "T002"
      }
    ],
    "orderBy": [
      {
        "field": "caption",
        "dir": "ASC"
      }
    ]
  }
}
```

返回示例：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "T003",
        "caption": "华东一区",
        "parentId": "T002",
        "teamLevel": 3
      },
      {
        "id": "T004",
        "caption": "华东二区",
        "parentId": "T002",
        "teamLevel": 3
      }
    ]
  },
  "msg": "success"
}
```

说明：

- hierarchy operator 固定作用于 synthetic member-QM 的 canonical `id` 字段
- 当前支持 `childrenOf`、`descendantsOf`、`selfAndDescendantsOf`、`ancestorsOf`、`selfAndAncestorsOf`

### 4.3 external patch 注入示例

接口：

```text
POST /jdbc-model/query-model/v2/FactSalesNestedDimQueryModel#product
```

请求体：

```json
{
  "start": 0,
  "limit": 20,
  "param": {
    "columns": ["id", "caption", "brand", "productCategory$caption"],
    "slice": [
      { "field": "brand", "op": "=", "value": "Apple" }
    ],
    "orderBy": [
      { "field": "brand", "dir": "DESC" }
    ],
    "extData": {
      "syntheticMemberPatch": {
        "visibleColumns": ["id", "caption"],
        "forcedSlice": [
          {
            "field": "productCategory$categoryGroup$groupType",
            "op": "=",
            "value": "高价值"
          }
        ],
        "forcedOrderBy": [
          {
            "field": "brand",
            "dir": "ASC"
          }
        ]
      }
    }
  }
}
```

最终效果：

- 返回列只保留 `id`、`caption`
- 过滤条件合并为：

```text
brand = 'Apple' AND productCategory$categoryGroup$groupType = '高价值'
```

- `forcedOrderBy` 覆盖同字段请求排序，因此最终 `brand` 按 `ASC`

## 5. simple 入口查询

### 5.1 最简入口

接口：

```text
POST /jdbc-model/dimension/v2/FactSalesNestedDimQueryModel/brand
```

说明：

- 这个入口适合最简单的下拉加载
- 内部会自动把 `FactSalesNestedDimQueryModel + brand` 归一成 `FactSalesNestedDimQueryModel#product`
- 默认只返回 canonical `id`、`caption`

返回示例：

```json
{
  "code": 0,
  "data": {
    "items": [
      { "id": "P001", "caption": "iPhone 15 Pro" },
      { "id": "P002", "caption": "Mate 60 Pro" }
    ],
    "total": 2
  },
  "msg": "success"
}
```

### 5.2 带 hierarchy 和 external patch 的 simple 入口

如果需要传 `hierarchy` 或 `extData`，应使用 body 版入口：

```text
POST /jdbc-model/dimension/queryDimensionData
```

请求体：

```json
{
  "start": 0,
  "limit": 20,
  "param": {
    "queryModel": "FactTeamSalesQueryModel",
    "dimension": "team$teamLevel",
    "hierarchy": "childrenOf:T002",
    "extData": {
      "syntheticMemberPatch": {
        "forcedOrderBy": [
          {
            "field": "caption",
            "dir": "ASC"
          }
        ]
      }
    }
  }
}
```

返回示例：

```json
{
  "code": 0,
  "data": {
    "items": [
      { "id": "T003", "caption": "华东一区" },
      { "id": "T004", "caption": "华东二区" }
    ],
    "total": 2
  },
  "msg": "success"
}
```

这里的内部归一流程是：

```text
FactTeamSalesQueryModel + team$teamLevel
-> FactTeamSalesQueryModel#team
-> hierarchy 字符串转成 slice(field=id, op=childrenOf, value=T002)
-> 统一走 QueryFacade 主链
```

## 6. 结果字段约定

### 6.1 simple 入口结果

simple 入口永远收敛成：

```json
{
  "id": "...",
  "caption": "..."
}
```

适合：

- 前端下拉
- 搜索联想
- 只需要成员值和显示名的枚举场景

### 6.2 direct DSL 结果

direct DSL 返回你显式请求的字段集合，例如：

```json
{
  "id": "P001",
  "caption": "iPhone 15 Pro",
  "brand": "Apple",
  "productCategory$caption": "手机",
  "productCategory$categoryGroup$groupType": "高价值"
}
```

适合：

- 网关或插件注入权限条件
- 需要列权限裁剪
- 需要按嵌套维度字段排序或筛选
- 需要父子维 hierarchy 查询

## 7. 使用建议

- 前端筛选器优先走 simple 入口
- 需要列控制、过滤条件注入、排序控制时走 direct DSL
- 获取字段全集优先走 `/semantic/v1/metadata?format=json`
- 对父子维，统一在 canonical `id` 上写 hierarchy operator，不要对 `caption` 或属性字段写层级操作
