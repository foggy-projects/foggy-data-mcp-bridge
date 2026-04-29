# P2 calculatedFields alias projection contract progress

## 基本信息

- version: 8.5.0.beta
- priority: P2
- status: complete
- completed: 2026-04-28
- stage: Stage 5 (cross-engine alignment)

## 修改内容

### Runtime 改动

1. **`TimeWindowInterceptor.java`**
   - 从 `originalColumns`（进入 BaseModelPlan 的列集合）中剥离 `calculatedFields.name` 条目
   - 后计算别名只通过外层 `DerivedQueryPlan` wrapper 的 `RawExpr` 投影

2. **`SemanticQueryServiceV3Impl.java`**
   - `generateSql()` 与 `queryModel(..., "execute", ...)` 中将 `request.getCalculatedFields()` 放入 `extData`
   - 使 `TimeWindowInterceptor` 可读取并构建 post-calc wrapper

### 不需要改动

- `SchemaAwareFieldValidationStep`: `collectSchemaFields()` 已在 85-91 行将 request-level `calculatedFields.name` 加入 `schemaFields`

### 新增测试

- `SchemaAwareCalcFieldAliasTest` (7 tests)
  - 非 TW: calc alias in columns, calc alias in orderBy
  - TW: growthPercent + YoY, rollingGap + rolling_7d
  - TW execute-mode: growthPercent survives real query execution
  - Negative: unknown column, calc alias without definition

### 更新测试

- `TimeWindowParitySnapshotTest`: `growthPercent`/`rollingGap` 加入 request columns

## 测试证据

- `mvn test -Dtest=TimeWindowParitySnapshotTest`: 1 passed
- `mvn test -Dtest=TimeWindowValidatorTest`: 19 passed
- `mvn test -Dtest=SchemaAwareCalcFieldAliasTest`: 7 passed
