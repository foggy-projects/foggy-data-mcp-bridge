---
doc_type: design
version: 9.5.4
status: APPROVED
approved_by: user
approved_at: 2026-08-01
---

# Runtime Release Package 与生产 Promotion 设计

## 1. 目标

把一个 exact、已验证的 workspace candidate 作为不可变文本制品从开发 Runtime 搬运到生产 Runtime，
但不搬运 candidate catalog、query result、数据源、权限或 Namespace 状态。生产 Runtime 以自己的依赖、
数据源和权限重新 validate/query，再对当前 target Bundle 执行原子 apply。

## 2. 技术探针结论

- `reusable-now`：workspace immutable revisions、TM/QM/FSScript quota/path/UTF-8 校验、detached validation、
  candidate query、source overlay guard、publication lock、published artifact ownership、Bundle registry switch、
  full-Namespace atomic refresh、failed-attempt recovery 和 restart reconciliation。
- `small-extension`：从 validated/PUBLISHED workspace 读取 exact snapshot；创建“target base + imported candidate”
  的单次原子 workspace；workspace metadata 增加 release import provenance。
- `new-runtime-primitive`：portable package identity、production promotion enablement、独立 apply route，以及成功
  apply 后 rollback/forward-recovery 状态机。
- `not-required`：Engine、Model SPI、临时 Namespace、持久 candidate catalog、数据库 schema、Git、launcher。

## 3. Release Package v1

package 使用 `foggy-authoring-release/v1` JSON contract：

- `packageId`：对 format、source Namespace/Bundle、candidate revision、validation evidence、dependency
  provenance 和排序后的 resource path/type/hash/content 做 length-delimited SHA-256。
- `candidateRevision`：继续使用生产代码 `CandidateContentRevision` 对 TM/QM/FSScript snapshot 计算。
- resources：只含严格 UTF-8 `.tm`、`.qm`、`.fsscript`，路径、大小、数量、case collision 与总量继续受
  workspace limits 约束。
- validation：记录开发 Runtime exact validation evidence，仅用于 provenance；导入后不继承为生产通过。
- dependencies：记录源 Namespace 当时可见 Bundle 的安全 identity/source type/artifact revision；生产导入
  不要求逐字相同，而由生产 validate/query 证明目标 currentness。
- 不包含绝对 path、auth、Authorization、数据源配置、catalog、cache、query rows、内部 store/attempt identity。

同一 source identity 与相同内容重复导出得到相同 `packageId`；`exportedAt` 不进入 identity。

## 4. 信任与生产 guard

- v1 不内置签名、证书或 KMS。持有 configured management auth-code 并显式导入的操作者是信任根；hash
  只证明传输后字节与 manifest 自洽，不证明外部发布者身份。
- `production-promotion-enabled=false` 为默认。false 时 export 可用，import/apply/rollback 均 fail closed。
- true 表示该 Runtime 进入 production promotion mode：package import/apply/rollback 可用；既有普通
  workspace `/publish` 被拒绝，防止绕过 package provenance 与 production confirmation。
- 所有 release routes 在任何 Runtime auth scope 下都强制 configured auth-code；业务 Authorization 只在
  imported workspace candidate query 时沿用现有数据面语义。

## 5. Import、Revalidation 与 Apply

1. 操作者选择生产 target Namespace 与当前 `workspaceEligible` target Bundle，并提交完整 package。
2. Runtime 在任何 workspace mutation 前验证 schema、packageId、candidate revision、resource hash/limits、
   target capability 和 overlay ownership。
3. Runtime 原子创建一个 base 指向生产 current Bundle、candidate 等于 package content 的 imported workspace；
   metadata 保存 package/source provenance。candidate 不可 save/delete，但可 diff、validate/query、discard。
4. 开发 validation 不改变生产状态。只有生产 Runtime 对 exact imported candidate 完成 validate 后才可 query/apply。
5. apply request 固定 package、candidate、target base Bundle、target base Namespace source revision；使用既有
   publication coordinator 生成 target Runtime-owned immutable artifact、切换 source 并 refresh。

## 6. 一步 Rollback

- rollback 只允许刚由 production apply 成功且 live/registry/catalog 仍精确指向该 candidate 的 workspace。
- request 固定 package、candidate 与 apply attempt；目标仅是 attempt 记录的直接前一 base。
- 首次 live mutation 前持久化 `ROLLING_BACK`。成功后 workspace 为 terminal `ROLLED_BACK`，evidence 记录
  rolled-back source/catalog generation。
- rollback 失败优先 forward-recover 到刚才已发布的 candidate；证明成功则回到 `PUBLISHED` 并返回失败，
  用户可重新确认 rollback。不能证明时进入 `ROLLBACK_REQUIRED`，只允许 exact rollback recovery。
- 不提供 revision list、任意历史 selector、跨 Bundle rollback 或删除 immutable artifacts。

## 7. Console

- 开发 Runtime：在 exact validated/PUBLISHED workspace 下载 JSON package，显示 package/candidate/source
  identity 和“无内置签名”边界。
- 生产 Runtime：从本地文件显式导入，选择 target Bundle/Namespace，展示 package provenance 与生产 base。
- imported workspace 使用既有 diff/validate/query；编辑入口关闭。apply、rollback、rollback recovery 都有
  exact facts confirmation，零自动 mutation retry。
- Console 只调用当前同源 Runtime，不保存 package 到 localStorage，不把 management token 当用户身份。

## 8. 后续非目标

签名/KMS、审批与用户审计、完整 revision history、任意 rollback、package registry/GC、Git adapter、跨
Runtime orchestration、JAR upgrade/binding、VS Code 和 Agent 均需后续独立 workitem。
