---
type: bug
bug_source: diagnostic-found
version: 9.3.4
ticket: BUG-934-STEP4-SENSITIVE-SCAN-AUTHORIZATION-CONTEXT-FALSE-POSITIVE
severity: blocker
status: closed
closed_at: 2026-07-18
closure_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: sensitive-scan-authorization-context-regression
automation_decision: required
owner: step4-coverage-tooling
---

# Demo 身份日志复用 authorization 凭据标签导致 Step 4 扫描命中

## Background

fresh diagnostic `step4-coverage-20260717-diagnostic-r10` 从 clean/pushed
`HEAD == origin/main == 47e0c027cd205a49d40db400ba26b99e6f97d60e` 启动。Unit、
Integration、Step 3 required、Step 4 report inventory、`23 exec / 48 sessions`、
aggregate exact union、coverage observation、source-after seal 与 run-owned resource cleanup
均已完成，随后在 `sensitive-scan` 阶段 fail closed。

Immutable evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md`。

r10 的 `summary.env` 与 `sensitive-scan.env` 均未生成，因此该 run 为
`failed / excluded-from-step4-exit / non-reusable`；它不能冻结阈值，也不能与后续 fresh run
拼接。

## Expected vs Actual

- Expected：扫描器继续拒绝凭据环境变量、任意非空
  secret/token/password/authorization 字段、Bearer header、带 userinfo 的连接串和命令行
  密码参数。
- Expected：不含凭据的业务诊断日志不得复用 `authorization:` 这种保留的 credential
  key/value 标签；身份结果应使用不会伪装成 header 或 secret field 的描述。
- Expected：修复不得靠修改五条扫描规则、跳过 `run.log`、跳过具体 class、白名单具体原值
  或扩大证据排除目录；全 run-owned evidence 仍必须被扫描。
- Actual：`DemoSecurityIdentityResolver` 用 credential-shaped `authorization:` 标签描述
  已解析的 demo identity；其后紧邻非空 identity 字段，因而被原规则正确按保守策略命中。

## Sensitive-safe Reproduction

只记录脱敏形态，不保存或复述原始匹配文本：

- matched file：`run.log`；
- match count：`1`；
- producer：`DemoSecurityIdentityResolver` 的 identity-source 诊断日志；
- redacted shape：`authorization=<NON_CREDENTIAL_CONTEXT>`；
- exact matched substring SHA-256（不含换行）：
  `d375d94172c0dbded90d08b61f8425e5bb8ed28d8b43141ef3e7cacc80d06c59`；
- whole `run.log` SHA-256：
  `9407de21dd8e421d0772495d34ede0cc122d5cf650324e8fcc219c44a763d2c0`。

扫描器只输出 matched file path 后调用 outer `fail`，没有把原始匹配内容写入新增 durable
artifact；`sensitive-scan.env` 正确保持 absent。

## Root Cause

Step 4 outer runner 对任意非 null `authorization` key/value 保守 fail closed，这是防止未知
scheme、opaque token 或 header payload 落盘的既有契约。问题位于日志生产端：
`DemoSecurityIdentityResolver` 把普通 identity resolution result 写成 credential-shaped
label，导致扫描器无法、也不应根据当前值猜测它是否安全。

因此不能通过放宽 pattern、接受 r10、人工删除命中或后补 `sensitive-scan.env` 绕过；日志
必须改成明确的 demo identity 语义，不再复用 credential label。

## Fix Strategy

1. 五条敏感扫描规则逐字保持不变；不对 `authorization` 降级或增加 allowlist/exclusion。
2. 将 producer 日志改为明确的 demo identity result 措辞，继续记录 user/dept/tenant identity，
   但不再使用 credential/header 标签。
3. 在 outer bootstrap-negative 最前加入内存 probe，并让它和最终目录扫描复用同一个 pattern
   数组：旧 credential-shaped 日志 fixture 必须命中，修复后的日志 fixture 必须不命中。
4. probe 同时覆盖 env、Bearer、password、API key、credential URI、CLI password 与 null
   安全字段；`rg` 返回码大于 1 时双向 fail closed。fixture 不写入 `RUN_ROOT`、不回显日志。
5. 对完整 run root 保持原有 extension set 与全目录扫描；任何真实命中仍须在 summary 发布前
   fail closed。

## Regression Test Decision

`automation_decision=required`。该缺陷位于 Step 4 mandatory evidence gate：过宽会让 fresh
diagnostic 永远无法发布，过窄会允许凭据进入持久证据。自动化至少覆盖：

- 旧 credential-shaped identity 日志：拒绝；
- 修复后的 demo identity 日志：允许，且不是 value/path/class whitelist；
- `authorization=<ANY_NON_NULL_VALUE>` 与 Bearer payload：继续拒绝；
- `password|secret|credential|token=<NON_EMPTY_VALUE>`：继续拒绝；
- credential-bearing URI、环境变量与 CLI password shape：继续拒绝；
- 多文件扫描中任一真实命中：outer fail closed，`summary.env` 与 `sensitive-scan.env` absent；
- 无命中：生成 exact `sensitive-scan.env`，后续 summary verifier 绑定其 SHA-256；
- focused negative contract 与 fresh all-lane run 均必须通过，禁止复用 r10 的 lane/exec/XML。

## Fix Checklist

- [x] r10 immutable failure、单一脱敏命中、absence boundary 与 cleanup/restoration 已封存。
- [x] 登记本 test-governance blocker，确认不是产品功能回归。
- [x] producer 改用非 credential-shaped demo identity 日志；五条扫描规则保持不变。
- [x] 补齐 `7` 个危险 fixture、`3` 个安全 fixture 与 `rg rc>1` fail-closed 回归；fixture
      不写入 run root。
- [x] 完成 focused regression、contract mutation 与实现质量复核；首轮 Medium cardinality
      伪绿已由 pre/post exact `7/3` 断言关闭，最终 B/H/M/L=`0/0/0/0`。
- [x] commit/push 并证明 clean `HEAD == origin/main`。
- [x] 使用全新 run ID 完成 fresh all-lane replacement。
- [x] 仅在 fresh diagnostic 成功后冻结 exact-observed threshold，再执行 fresh formal、最终质量、
      coverage evidence audit 与 acceptance。

该 r10 时点 `status=in-progress`，且 r10 不可复用；后续 replacement diagnostic、formal-r4、
coverage audit 与 feature acceptance 已按序通过，本 workitem 现已关闭。

## References

- `scripts/verify-v934-step4-coverage.sh`
- `scripts/v934/step4/coverage-contract.json`
- `scripts/v934/step4/coverage_xml_tool.py`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r10/`
