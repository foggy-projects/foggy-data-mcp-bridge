---
doc_role: execution-prompt
doc_purpose: Start and govern the single-root 9.3.4 implementation session.
version: 9.3.4
status: ready
created_at: 2026-07-14
updated_at: 2026-07-14
---

# 9.3.4 项目级执行提示

## 使用方式

在仓库根目录启动一个项目级执行会话，完整读取以下资料后，从 Step 1 开始：

1. `CLAUDE.md`
2. `docs/9.3.4/README.md`
3. `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
4. `docs/9.3.4/contract/test-lane-evidence-contract.md`
5. `docs/9.3.4/module-responsibility.md`
6. `docs/9.3.4/code-inventory.md`
7. `docs/9.3.4/implementation-plan.md`
8. `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
9. `docs/9.3.4/acceptance-evidence-plan.md`
10. `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`

修改任何模块前还必须查找并读取该路径向上的最近模块级 `CLAUDE.md`；当前已知
`foggy-dataset-mcp/CLAUDE.md` 对 MCP 模块改动生效，且与 root guidance 共同遵守。

## 执行任务

按 `implementation-plan.md` 的 Step 1→7 严格推进测试与 CI 证据链治理：

- Step 1 冻结 workspace source + reactor execution-key 两层 inventory、cardinality-
  aware predecessor migration、runner/lane、数据库、coverage 和 evidence contract；
- Step 2 完成 Surefire unit / Failsafe IT/E2E 唯一分层，实跑 unit/hermetic IT，
  精确 defer external suites；
- Step 3 建立 SQLite、MySQL 5.7、MySQL 8、PostgreSQL 15、SQL Server 2022
  及 Redis/其他 required external matrix；
- Step 4 接好 agent 后重跑全部 required lanes，聚合 unit+IT JaCoCo，并以 XML
  verifier + owning-module check 建立门禁；
- Step 5 演练单一 fail-closed authority runner，只产 immutable candidate；
- Step 6 接入 PR/main/release required CI，以 exact five-cell artifacts 聚合，并让
  GitHub release 与 Docker image 使用同一已测 JAR；
- Step 7 在 clean commit 做权威回放和 ordered post-gates。

每一步必须先把实际实现、命令、测试数量、报告、失败尝试、偏差、风险和 exit
decision 回写唯一 progress，再进入下一步。不能用多个 diagnostic/superseded run
拼成绿色 authority。

## 强制约束

- 保护当前 dirty worktree；禁止 `reset --hard`、`checkout --`、`clean`、覆盖式
  还原或删除用户/前序版本成果。
- Step 1 contract 未 confirmed 前，不批量改测试名、POM、workflow、镜像或门槛。
- 9.3.1–9.3.3 historical FQCN/count/raw evidence 和 exact runner 只读；重命名后
  通过 reviewed migration manifest + v934 successor lanes 回归，不篡改/强求旧
  runner 在新 source 原样通过。
- 每个 executable source 只归一个 runner family，每个 nested/variant execution key
  只有一个 owner/lane；required key 的 missing、zero、duplicate、orphan、stale 或
  skipped 必须 fail closed。
- source file 不等于 report execution：`@Nested` 的 `Outer$Nested` 和五库/provider
  variants 必须逐 execution key 入表；Step 2/3 分别 exact-match 自己的 subset，
  合并后覆盖全部 required keys且无交集。
- 五库必须真实执行并核对 product/version/physical identity/sentinel；不支持能力
  用 expected refusal assertion，不能 assumption skip。
- SQLite broad integration 与 five-DB parity 的 SQLite 子 lane 必须使用 Step 1
  冻结的互斥 FQCN manifest；同一 `(FQCN, db_kind)` 不得在两个 lane 重复执行。
- Step 2 只允许 external required suites 以 exact owner/preflight/Step 3 manifest
  defer；Step 3 必须清零 deferred set。Step 3 不声称 coverage，Step 4 带 agent
  重跑全部 required lanes 后才生成 exec。
- coverage 初次结果只能是 diagnostic candidate；人工审查 provenance 后冻结。
  aggregate reporter 只出 XML/HTML，versioned verifier 必须检查 expected classes/
  counters/thresholds；不得靠空 `jacoco:check`、降阈值、扩大 exclusion 或丢失 exec
  过门。
- CI aggregator 必须拒绝 `failure`、`skipped`、`cancelled`，并要求五库 lane
  artifacts exact set/cardinality=5；release 不允许 `--skip-external-db`、
  `-DskipTests` 或重新构建未经同一 gate 验证的 JAR。
- Step 5 dirty-safe rehearsal 只能写 candidate root/pointer；authority 只接受 Step 7
  exact clean commit。evidence/JAR/image/archive/digest 不可覆盖，上传后必须下载
  复验，image `/app/app.jar` SHA 必须等于 tested JAR；不得写入 credential、token、
  password 或含密钥 JDBC URL。
- 只维护一个项目级 progress，不创建模块级 requirement/prompt/progress 副本。
- 不提前实施 9.3.5 typed execution/QueryFacade/API 拆解，也不实施 9.4.0 SPI v2/
  production module split。

## 缺陷处理与停线

测试治理发现生产缺陷时，先按 BUG workflow 建 9.3.4 workitem，建立最小 RED，
再做最小修复和 GREEN 回归；不要把失败测试弱化为通过。若修复需要改变公共 API、
执行阶段或生产模块边界，记录 blocker 并转交 9.3.5/9.4.0，不越界实施。

任一 Step exit 未满足、外部 branch protection 无法核验、required DB/CI 权限缺失，
都在 progress 中明确标记 blocked/pending；不假设绿色，不跳到后续 Step。

## 收口顺序

```text
implementation check-in + progress
  -> implementation self-check
  -> formal implementation quality gate
  -> test coverage evidence audit
  -> version acceptance signoff
  -> README / requirement / contract / progress / roadmap sync
```

只有所有 critical acceptance item 都有同一 clean-commit local/CI authority 后，
才能把 9.3.4 标记为 signed-off 并将 9.3.5 标记为 ready。
