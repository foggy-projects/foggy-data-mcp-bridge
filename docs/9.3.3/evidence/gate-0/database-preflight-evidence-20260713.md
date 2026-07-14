---
doc_role: database-preflight-evidence
doc_purpose: Record fail-closed product, version, physical identity and sentinel checks for the 9.3.3 entry gate.
version: 9.3.3
status: passed
recorded_at: 2026-07-13
experience: N/A
---

# Gate 0 数据库 Preflight 证据

## 文档作用

- doc_type: test-evidence
- intended_for: execution agent / coverage auditor / signoff owner
- purpose: 证明 SQLite、MySQL 5.7、PostgreSQL 15 的真实身份和 fixture，且错误产品会在 lifecycle 测试前失败。

## Positive Matrix

| Lane | Actual product/version | Sanitized physical identity | Catalog/schema | Tests/Failures/Errors/Skipped | Result |
|---|---|---|---|---|---|
| SQLite | SQLite 3.42 | `sqlite:<shared-memory>` | `<none>/<none>` | 1/0/0/0 | passed |
| MySQL 5.7 | MySQL 5.7 | `mysql://127.0.0.1:13306/foggy_test` | `foggy_test/<none>` | 1/0/0/0 | passed |
| PostgreSQL 15 | PostgreSQL 15.17 | `postgresql://localhost:15432/foggy_test` | `foggy_test/public` | 1/0/0/0 | passed |

三条 lane 均精确验证 `ORDER_STATUS` 五条 sentinel：`PENDING/1`、`PAID/2`、`SHIPPED/3`、`COMPLETED/4`、`CANCELLED/5`。报告均由 `scripts/assert-v933-test-report.sh` 验证 owning Failsafe XML 恰好一份且 tests=1。

## Negative Evidence

- operation: SQLite profile + expected `postgres15`
- exit: 1
- observed failure: `unexpected database product: sqlite`
- result: expected-negative-pass
- final log SHA-256: `a55df30a7c6528d29699550a015df589be64a8dc2da2c0f7dcf58a238f3b4ef9`

## Generated Evidence

| Lane | Maven log | SHA-256 |
|---|---|---|
| wrong DB | `target/v933-entry-gate/runs/20260713T104955Z-959834/negative-wrong-db/maven.log` | `a55df30a7c6528d29699550a015df589be64a8dc2da2c0f7dcf58a238f3b4ef9` |
| SQLite | `target/v933-entry-gate/runs/20260713T104955Z-959834/sqlite-preflight/maven.log` | `367003f17aabfa54161e2aa32464aaf5ff7e010089081a18d2cb8ff2326a827f` |
| MySQL 5.7 | `target/v933-entry-gate/runs/20260713T104955Z-959834/mysql57-preflight/maven.log` | `d9711a8bf331dfd39266606c673d3de5b8860acb28f89cadb55d2431a7447db2` |
| PostgreSQL 15 | `target/v933-entry-gate/runs/20260713T104955Z-959834/postgres15-preflight/maven.log` | `4032df79d8e57d763225dfedb4087617fba8d3c748d2be4b27e73ea1a5745c81` |

Failsafe XML 位于 `target/v933-entry-gate/runs/20260713T104955Z-959834/<lane>/failsafe-reports/`。日志和报告不记录用户名、密码、URL 参数或容器环境变量。

完整统一入口证据见 `gate-0-run-20260713.md`。

## Decision

- required DB preflight: passed
- wrong product/version/identity fail closed: passed
- database unavailable negative: expressed by the same connection/preflight contract；not destructively simulated because required healthy shared fixtures were already in use
- full 9.3.4 five-database matrix: not in scope
