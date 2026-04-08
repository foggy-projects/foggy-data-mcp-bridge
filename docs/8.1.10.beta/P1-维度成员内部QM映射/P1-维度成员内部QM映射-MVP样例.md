# P1-维度成员内部QM映射 MVP 样例

## 1. simple 入口：普通维度

```json
{
  "queryModel": "FactSalesNestedDimQueryModel",
  "dimension": "brand"
}
```

- 归一结果：`FactSalesNestedDimQueryModel#product`
- 实际查询列：`id`, `caption`
- 适用场景：筛选器下拉、搜索联想

## 2. DSL 入口：嵌套维度

```json
{
  "queryModel": "FactSalesNestedDimQueryModel#product",
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
  "start": 0,
  "limit": 20
}
```

- 允许访问的字段空间：`product` 根维度及其内嵌维度子树
- 不允许访问：原业务 QM 的其他维度、度量、业务字段

## 3. simple 入口：父子维

```json
{
  "queryModel": "FactTeamSalesQueryModel",
  "dimension": "team$teamLevel",
  "hierarchy": "childrenOf:T002"
}
```

- 归一结果：`FactTeamSalesQueryModel#team`
- hierarchy 参数被转换成 synthetic member-QM 上的 `id childrenOf T002`

## 4. external patch：DSL 入口

```json
{
  "queryModel": "FactSalesNestedDimQueryModel#product",
  "columns": ["id", "caption", "brand", "productCategory$caption"],
  "slice": [
    {
      "field": "brand",
      "op": "=",
      "value": "Apple"
    }
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
          "field": "caption",
          "dir": "ASC"
        }
      ]
    }
  }
}
```

- `visibleColumns` 只裁返回列
- `forcedSlice` 与请求 `slice` 以 AND 合并
- `forcedOrderBy` 会覆盖同字段请求排序，并追加到最终排序列表

## 5. external patch：simple 入口

```json
{
  "queryModel": "FactSalesNestedDimQueryModel",
  "dimension": "brand",
  "extData": {
    "syntheticMemberPatch": {
      "forcedSlice": [
        {
          "field": "brand",
          "op": "=",
          "value": "Apple"
        }
      ],
      "forcedOrderBy": [
        {
          "field": "caption",
          "dir": "ASC"
        }
      ]
    }
  }
}
```

- simple 入口会先归一为 synthetic member-QM，再进入统一的 patch 合并逻辑

## 6. 当前 MVP 边界

- 只支持 `REFERENCE_QM`
- 不做 context permission
- 不按当前事实数据裁剪成员
- 不新增单独的成员查询旁路
- 不要求 Foggy 解释 Odoo 等外部系统的角色模型
