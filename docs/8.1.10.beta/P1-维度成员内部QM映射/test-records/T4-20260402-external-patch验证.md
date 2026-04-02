# T4 测试记录：external patch 验证

## 基本信息
- 日期：`2026-04-02`
- 阶段：`阶段4：外部权限 patch 合并`
- 测试人：`Codex`

## 测试范围
- `visibleColumns` 列裁剪
- `forcedSlice` 与请求 `slice` 合并
- `forcedOrderBy` 与请求排序的覆盖关系
- simple 入口透传 `extData`
- synthetic member-QM 下 patch 与 schema 求交

## 测试命令
- 运行时测试：
```powershell
mvn -pl foggy-dataset-model -P !multi-db -Dtest=SyntheticMemberQueryModelRuntimeTest test
```

- 覆盖率报告：
```powershell
mvn -pl foggy-dataset-model -P !multi-db org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent "-Dtest=SyntheticMemberQueryModelResolverTest,SyntheticMemberQueryModelRuntimeTest" test org.jacoco:jacoco-maven-plugin:0.8.12:report
```

## 关键用例
- `externalPatchCanTrimVisibleColumns`
- `externalPatchForcedSliceCanMergeWithRequestSlice`
- `externalPatchForcedOrderByCanOverrideRequestOrder`
- `simpleEntryCanForwardExternalPatch`

## 输入样例
- DSL 入口 patch：
```json
{
  "model": "FactSalesNestedDimQueryModel#product",
  "columns": ["id", "caption", "brand", "productCategory$caption"],
  "orderBy": [{"field": "brand", "desc": true}],
  "extData": {
    "syntheticMemberPatch": {
      "visibleColumns": ["id", "caption", "brand"],
      "forcedSlice": {
        "field": "brand",
        "operator": "=",
        "value": "Apple"
      },
      "forcedOrderBy": [
        {"field": "brand", "desc": false}
      ]
    }
  }
}
```

- simple 入口 patch：
```json
{
  "model": "FactSalesNestedDimQueryModel",
  "fieldName": "product",
  "columns": ["id", "caption", "brand"],
  "extData": {
    "syntheticMemberPatch": {
      "visibleColumns": ["id", "caption"]
    }
  }
}
```

## 关键 SQL / 执行结果
- forcedSlice 合并后的 SQL 片段：
```sql
where p.brand = ?
  and p.product_name like ?
```

- forcedOrderBy 覆盖后的 SQL 片段：
```sql
order by p.brand ASC
```

## 测试结果
- `SyntheticMemberQueryModelRuntimeTest`：
```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

- `SyntheticMemberQueryModelResolverTest + SyntheticMemberQueryModelRuntimeTest`：
```text
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
```

## 覆盖率结果
- 报告位置：
  - `foggy-dataset-model/target/site/jacoco/index.html`
  - `foggy-dataset-model/target/site/jacoco/jacoco.csv`
- 关键类覆盖率：
  - `SyntheticMemberExternalPatchStep`：行覆盖 `85.8%`，分支覆盖 `67.7%`
  - `SyntheticMemberExternalPatch`：行覆盖 `100.0%`，分支覆盖 `66.7%`
  - `SyntheticMemberQueryModelResolver`：行覆盖 `90.9%`，分支覆盖 `71.8%`
  - `SyntheticMemberQueryModelFactory`：行覆盖 `90.0%`，分支覆盖 `62.9%`

## 验收对照
- external patch 可在不暴露角色模型前提下完成注入：通过
- `visibleColumns` 与 schema 求交后结果稳定：通过
- `forcedSlice` 与请求 `slice` 合并后执行结果可预测：通过
- `forcedOrderBy` 可稳定追加或覆盖：通过
- simple 入口与 DSL 入口命中同一 patch 合并逻辑：通过
- 不可见字段不会通过 `columns/orderBy` 绕过：通过

## 结论
- Stage4 验收通过，可进入 Stage5。
