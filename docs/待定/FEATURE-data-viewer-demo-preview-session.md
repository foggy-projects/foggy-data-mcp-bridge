---
doc_type: workitem
intended_for: normal-analysis / implementation / verification
purpose: Track the independent Harness logistics demo's expiring, one-time viewer handoff, user session, Runtime permissions, trust context, and evidence.
status: VERIFIED_LOCAL_DEMO
target_version: undecided
---

# DataViewer 演示预览会话与可信探查闭环

## Goal

在独立的合成物流演示中，把 Harness 查询上下文交接给受认证的浏览器用户，并让 Runtime 按同一可信身份和 QM 权限执行只读探查；展示用户能复核的查询口径、来源和执行状态。

## Scope

- 过期/缺失的 Viewer 查询上下文统一返回明确的过期响应，链接标识使用 128 位随机值。
- MCP 长期凭据不得写入查询上下文、URL 或浏览器存储；只保存不可逆凭据标识。Viewer 交接码两小时内仅一次兑换、不授予数据权限。
- 演示 BFF 以服务端会话 cookie 认证用户，兑换时续期查询上下文，会话绝对有效期不超过两小时且可撤销；本机演示的 idle TTL 与 absolute TTL 相同。会话固定 namespace、QM、只读动作及用户行权限。
- 受保护的合成 QM 使用 Runtime 的 `modelPermissions` resolver 执行 fail-closed 的模型/行权限；Harness 用户凭据只作受控的演示主体映射。
- Viewer 显示经过 QM/Runtime 提供的指标定义、日期与筛选范围、数据来源、更新时间或未提供状态。
- 使用固定合成 fixture、黄金查询和只读 SQL oracle 验证 Harness 与 Viewer；记录不含秘密的本地录制证据与脚本草案。

## Non-goals

- 不读取或复用 TMS 生产数据、QM、报表、namespace、员工会话或 ACL。
- 不把演示登录页/用户映射当作企业 SSO 或生产 IAM；生产部署仍须接入宿主可信认证和凭据存储。
- 不承诺任意原始表浏览、自由 Pivot 或未实现的 SQL 可视化。

## Acceptance

- [x] 失效链接显示可操作的重新打开提示，当前上下文和服务端缓存均不暴露长期凭据。
- [x] 交接码两小时内一次兑换，重复/过期兑换拒绝；兑换时续期查询上下文，用户会话 cookie 为 HttpOnly，最长两小时，idle 不早于该 absolute timeout。
- [x] 无凭据和跨 query context 请求被拒绝；Coordinator 与 North 两种合成用户按站点获得不同且与 Harness 查询一致的行集。
- [x] 数据结果和 QM 口径/范围/来源信息可在页面复核；引擎未提供刷新时间时明确标注。
- [x] Java/前端测试、实际 Launcher HTTP 页面与查询、合成 SQL 对照和录制脚本证据已完成。

## Progress

- [x] Viewer 过期上下文响应、随机链接长度与前端状态处理；焦点测试通过。
- [x] Loopback 演示 BFF 交接码与用户会话；cookie HttpOnly/SameSite Strict，demo HTTP 配置仅用于 loopback，因此关闭 Secure。
- [x] Runtime 权限接入和行级验证；服务端 MCP 凭据指纹绑定用户，QM resolver 返回允许的站点范围。
- [x] 可信探查上下文 UI；来源、QM 说明、过滤条件和查询时间可见，刷新时间未知时显示未提供。
- [x] 端到端复核与录制准备证据；最终视频录制不属于此本机闭环验收。

## Version decision

此 workitem 位于 `docs/待定/`，因为仓库当前没有用户指定的活跃目标版本。版本确认后按仓库交付规范迁入对应子迭代。

## Local verification record (2026-09-27)

- Demo Launcher: `http://127.0.0.1:18172`, `demo-preview` profile, loopback-only with a synthetic business SQLite database and a separate SQLite query-context file. Do not expose this HTTP profile on a network interface.
- Session contract: two-hour one-time code in URL fragment; the browser redeems it after matching the server-mapped demo principal. Redemption extends the live query context for two hours from login. The cookie is opaque, HttpOnly and SameSite Strict; absolute and idle limits are both two hours in this local demo. The current local HTTP profile sets `Secure=false`; production use requires HTTPS, Secure cookies, and a host-owned identity provider.
- Access evidence: Coordinator sees four stations (10 tickets, 23 pieces, 106.15 kg); North sees HZ-01 and NB-01 only (6 tickets, 14 pieces, 63.75 kg). Anonymous Viewer API and a different query context both return 401.
- Detail evidence: a Viewer session bound to the Coordinator MCP identity loaded the fixed demo detail query with 10 rows, two cancelled records, 23 pieces and 106.15 kg, matching the read-only SQL fixture.
- Filter contract evidence: the demo date remains in `slice`; an explicit `having waybillCount [] [1,2]` returns HF-01 and NB-01 (3 tickets, 5 pieces, 23.35 kg), matching SQLite.
- Test evidence: Viewer module baseline 112 tests, Runtime `HavingClauseIT` 12, focused launcher session/filter/controller tests 9, frontend suite 515, plus Java metadata, API/cache and filter-routing tests passed. Production frontend build, `vue-tsc`, and Maven `-Pruntime-api` Launcher build passed.
- See the demo repository plan and `docs/logistics-trust-demo/recording-evidence-20260927.md` for SQL oracle values and the fact-checked storyboard. No production TMS data, namespace, ACL, session, or credential is used.

## Pre-recording follow-up after independent review

The local prototype now preserves explicit `having` and `extData` through MCP parsing, cached context, Viewer re-query and truncated-result links. The opened page was compared with the originating MCP result. `slice` remains row-level WHERE input; the engine does not infer HAVING from field metadata.

The MCP `tools/list` description/schema now describes grouped exploration and the login-bound, one-time link; query-context and launch-link expiry are separate. The CLI needs no new command for this Harness path. Coordinator/North, anonymous/cross-query and late-open checks passed. On 2026-09-29, local Launcher source and the 18172 demo instance switched query-context storage to SQLite by default; the managed 18166 Launcher is an older binary and remains outside that change. The remaining video gate is browser footage of login, grid filtering, detail and identity switching.
