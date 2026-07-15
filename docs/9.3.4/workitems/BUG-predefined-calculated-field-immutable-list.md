---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-PREDEFINED-CALCULATED-FIELD-IMMUTABLE-LIST
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
---

# 预定义计算字段注入器直接修改调用方不可变 List

## Background

消除 `JavaQueryModelAggregateJoinSnapshotTest` 的默认 assumption 后，原先从未执行的
`predefinedCalculatedFieldAllowedExecCase` 稳定抛出 `UnsupportedOperationException`。
请求通过公开 setter 传入 `List.of(...)`，而生产
`PredefinedCalculatedFieldInjector` 直接对调用方列表执行 `removeIf`/`addAll`。

## Reproduction

```bash
mvn -q -pl foggy-dataset-model -P\!multi-db,\!model-lifecycle \
  -DskipUnitTests=false -DskipITs=true \
  -Dtest=com.foggyframework.dataset.db.model.parity.JavaQueryModelAggregateJoinSnapshotTest \
  test
```

稳定结果：

```text
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
java.lang.UnsupportedOperationException
  at java.util.ImmutableCollections$AbstractImmutableCollection.removeIf
  at PredefinedCalculatedFieldInjector.inject(PredefinedCalculatedFieldInjector.java:50)
XML SHA-256: dec75bb66920141d51bb04a42cf3dd8c1a66cbb4137f11a570ce3970a83c5bfb
```

## Expected vs Actual

- 期望：query request 接受任意合法 `List<CalculatedFieldDef>` 实现；注入器需要修改时先
  建立自己的 mutable copy，不改变调用方集合。
- 实际：`List.of`、unmodifiable list 等合法输入在预定义字段替换阶段直接抛出 JDK
  collection 异常，查询无法执行。

## Impact Scope

- 影响 Java API、测试/嵌入式调用方以及任何提供不可变 calculatedFields list 的适配器。
- JSON 请求通常反序列化为 mutable list，因此此前主路径容易保持伪绿。
- 最小生产修复只在确有预定义字段、request list 非 null 时 defensive-copy；注入与 warning
  语义保持不变。

## Test Strategy

1. 使用已存在、原来被 assumption 遮住的 aggregate-join snapshot node 保留稳定 RED。
2. 注入器在 `removeIf` 前复制 request list 并回设到 request。
3. 默认 focused snapshot 要求 `1/0/0/0` 且 29 个 case 全部完成。
4. 显式 snapshot export 仍要求 `1/0/0/0`；最终由全量 Surefire authority 收口。

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/PredefinedCalculatedFieldInjector.java`
  - defensive-copy request calculated fields before replacement/injection。
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/parity/JavaQueryModelAggregateJoinSnapshotTest.java`
  - 现有 immutable-list case 作为生产回归测试。

## Fix Checklist

- [x] 去除 skip 后取得稳定生产路径 RED。
- [x] 定位 `removeIf` 直接修改 `List.of`。
- [x] 最小 defensive-copy 生产修复。
- [x] 默认/显式 export focused GREEN。
- [x] successor 对生产 classpath 与测试源码完成 hash-sealed 双审。
- [x] Step 2 Surefire authority GREEN。

## Verification

注入器现在在替换/注入前复制 request calculated-fields collection，并把 owned mutable
list 回设到 request；调用方 `List.of` 不再被直接修改。原同一 snapshot node 从
`1/0/1/0` 转为默认与显式 export 均 `1/0/0/0`，29-case 契约全部执行。

## References

- `docs/9.3.4/workitems/BUG-aggregate-join-snapshot-default-skip.md`
- `foggy-dataset-model/target/surefire-reports/`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
