---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-EMBEDDING-SERVICE-DISABLED-UNIT
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: addons/foggy-dataset-model-vector
---

# EmbeddingService 单元报告包含永久 Disabled 节点

## Background

9.3.4 Step 2 的 r5 Surefire authority 中，Maven 测试本体返回成功，但严格报告门在
`EmbeddingServiceTest` 发现一个 `@Disabled` 节点并以 `E_REPORT_OUTCOME` 拒绝签发证据。
该节点声称需要真实 OpenAI API Key，长期作为 unit report 中的 skip 存在，属于 Step 4
治理要消除的伪绿色。

失败运行：

```bash
scripts/verify-v934-unit.sh step2-unit-r5-20260715
```

## Reproduction

fresh XML：

```text
report: addons/foggy-dataset-model-vector/target/surefire-reports/
        TEST-com.foggyframework.dataset.db.model.vector.EmbeddingServiceTest.xml
Tests run: 15, Failures: 0, Errors: 0, Skipped: 1
skipped testcase: testRealApiCall
reason: 需要配置真实的 API Key 才能运行此测试
report SHA-256: 20c7c5d5769df6e8733b318024fe9ee58a59231521a8e649ea0f8b573b87c65b
```

report verifier 不接受“Maven 绿色但节点未执行”，稳定返回：

```text
E_REPORT_OUTCOME: report is not green failures=0 errors=0 skipped=1
```

## Expected vs Actual

- 期望：Step 2 unit ownership 的 677 个 positive reports 全部实际执行，零 skip；
  embedding 请求/响应路径由本地、确定性的 OpenAI-compatible HTTP fixture 覆盖。
- 实际：真实 API 节点被永久禁用，既不验证 HTTP 请求与响应解析，也阻断 unit authority。

## Impact Scope

- 只修正既有 `EmbeddingServiceTest#testRealApiCall` 的测试夹具，不访问公网、不读取真实
  API Key、不改生产 `EmbeddingService`。
- 保持 source、report、method/discovery node 与 execution identity 不变；源码与
  test-classes hash 变化必须由新 successor amendment 封存。

## Test Strategy

1. 保留 r5 authority 的 `15/0/0/1` 与 `E_REPORT_OUTCOME` 作为 RED。
2. 以 JDK loopback `HttpServer` 提供 OpenAI-compatible `/embeddings` JSON 响应，捕获
   Authorization、method、path 与 request body。
3. 复用同一 test method，断言请求边界与解析后的向量值，要求完整 class
   `15/0/0/0`。
4. 生成并双审 successor amendment，确认 15 个 discovery nodes 与 unit ownership
   零变化；最终重跑 Step 2 Surefire authority。

## Code Inventory

- `addons/foggy-dataset-model-vector/src/test/java/com/foggyframework/dataset/db/model/vector/EmbeddingServiceTest.java`
  - 以 loopback HTTP fixture 替换 `@Disabled` 真实 API 调用。

## Fix Checklist

- [x] 完整 Surefire authority 捕获 skip 并 fail-closed。
- [x] 锁定唯一 skipped testcase 与 fresh XML hash。
- [x] 同一 test node 改为本地确定性 HTTP 契约。
- [x] focused class `15/0/0/0` GREEN。
- [x] successor amendment 完成 hash-sealed 双审。
- [x] Step 2 Surefire authority exact-set GREEN。

## Verification

loopback fixture 覆盖 `POST /embeddings`、Bearer header、model/input JSON 与固定三维
响应，未读取环境变量或访问公网。完整 class 结果为 `15/0/0/0`，fresh XML SHA-256：

```text
39e0f86b04bbb1299278cced6acd4d1cf726b8ffc8974922ed086441812837be
```

## References

- `scripts/v934/step2_report_tool.py`
- `target/v934-step2-unit/runs/step2-unit-r5-20260715/run.marker`
- `scripts/v934/successor/step2/execution-inventory.tsv`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
