# Stage3 测试记录：hierarchy 与路径字段

## 测试命令

```powershell
mvn -pl foggy-dataset-model -P !multi-db "-Dtest=SyntheticMemberQueryModelRuntimeTest,SyntheticMemberQueryModelResolverTest" test
```

## 测试范围

- synthetic member-QM 查询根维度 canonical 字段结果
- synthetic member-QM 查询一级、二级嵌套子维度路径字段结果
- synthetic member-QM 在子维度路径字段上的过滤与排序
- synthetic member-QM 在父子维 `$id` 字段上的 hierarchy operator 查询结果
- simple 入口在父子维字段上的归一结果

## 关键请求样例

### 1. 嵌套路径字段查询

```json
{
  "model": "FactProductSalesQueryModel#product",
  "columns": [
    "id",
    "caption",
    "brand",
    "productCategory$id",
    "productCategory$caption",
    "productCategory$categoryGroup$id",
    "productCategory$categoryGroup$caption",
    "productCategory$categoryGroup$groupType"
  ],
  "orderBy": [
    {
      "field": "id",
      "order": "ASC"
    }
  ]
}
```

### 2. 子维度路径字段过滤与排序

```json
{
  "model": "FactProductSalesQueryModel#product",
  "columns": [
    "id",
    "caption",
    "productCategory$caption"
  ],
  "slice": [
    {
      "field": "productCategory$caption",
      "op": "=",
      "value": "Electronics"
    }
  ],
  "orderBy": [
    {
      "field": "productCategory$caption",
      "order": "DESC"
    },
    {
      "field": "caption",
      "order": "ASC"
    }
  ]
}
```

### 3. 父子维 hierarchy operator

```json
{
  "model": "FactTeamSalesQueryModel#team",
  "columns": [
    "id",
    "caption"
  ],
  "slice": [
    {
      "field": "id",
      "op": "childrenOf",
      "value": "T002"
    }
  ],
  "orderBy": [
    {
      "field": "id",
      "order": "ASC"
    }
  ]
}
```

### 4. simple 入口 hierarchy 归一

```json
{
  "model": "FactTeamSalesQueryModel",
  "fieldName": "team",
  "hierarchy": "childrenOf:T002"
}
```

归一后应等价于：

```json
{
  "model": "FactTeamSalesQueryModel#team",
  "slice": [
    {
      "field": "id",
      "op": "childrenOf",
      "value": "T002"
    }
  ]
}
```

## 关键执行特征

- synthetic member-QM 的根表已切到维表，而不是 fact 表
- nested path 字段通过维度子树 join graph 执行
- parent-child hierarchy 通过 closure / ancestorClosure join 执行
- simple 入口不再依赖旧成员查询旁路

## 测试结果

- `SyntheticMemberQueryModelRuntimeTest`: 12 个测试，全部通过
- `SyntheticMemberQueryModelResolverTest`: 4 个测试，全部通过
- 合计：16 个测试，0 failure，0 error

## 覆盖率结论

仓库当前没有现成 JaCoCo 配置，本阶段以目标测试集覆盖 Stage1 + Stage3 的关键主链回归。

建议命令：

```powershell
mvn -pl foggy-dataset-model -P !multi-db `
  org.jacoco:jacoco-maven-plugin:prepare-agent `
  "-Dtest=SyntheticMemberQueryModelResolverTest,SyntheticMemberQueryModelRuntimeTest" `
  test `
  org.jacoco:jacoco-maven-plugin:report
```

在当前阶段目标下，测试已经覆盖：

- synthetic model 解析
- synthetic runtime model 构建
- nested path 字段查询
- hierarchy operator 执行
- simple 入口归一

结论：Stage3 达到文档定义的验收标准。
