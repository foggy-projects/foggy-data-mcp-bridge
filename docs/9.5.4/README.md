---
doc_role: version-readme
version: 9.5.4
theme: runtime-release-package-production-promotion
status: ACTIVE
recorded_at: 2026-08-01
---

# 9.5.4 Runtime Release Package 与生产 Promotion

9.5.4 在 9.5.3 已验收的 workspace、candidate query 和开发 Runtime publish/recovery 基础上，建立
不依赖 Git、也不引入跨 Runtime 中央控制面的跨环境模型交付闭环：开发 Runtime 导出不可变 release
package，生产 Runtime 导入为隔离 workspace，使用生产依赖与权限重新 validate/query，再执行 exact apply
或受控的一步 rollback。

| Work item | 状态 | 目标 |
|---|---|---|
| [Release package / promotion 基础探针](workitems/SPIKE-runtime-release-package-promotion-foundations.md) | ACCEPTED | 冻结 portable identity、信任边界、生产 guard、apply/recovery/rollback 状态机与复用结论 |
| [Release package 与 production promotion API](workitems/FEATURE-runtime-release-package-production-promotion-api.md) | ACCEPTED | 交付 immutable JSON package、生产导入/revalidation、exact apply、一步 rollback 与失败恢复 |
| [Runtime Console production promotion](workitems/FEATURE-runtime-console-production-promotion.md) | ACCEPTED | 接入导出/导入、生产 evidence、apply/rollback confirmation 与状态恢复 |

设计与交付顺序见 [Release Package 与生产 Promotion 设计](runtime-release-package-promotion-design.md)。

## 已冻结边界

- 不建设跨 Runtime 控制面；package 由用户显式下载、传递并在目标 Runtime 导入。
- package v1 是 canonical JSON，不接受 ZIP、任意文件、绝对路径、secret、catalog 或 query result。
- 管理 auth-code 是 v1 操作者信任根；package hash 提供完整性与 provenance，不宣称签名身份。
- 生产 Runtime 必须显式启用 promotion mode；启用后普通 workspace `/publish` 不能绕过 production apply。
- 导入 candidate 不可在生产 Console 编辑；开发 validation 只作 provenance，生产必须重新 validate/query。
- rollback v1 只回到该 apply attempt 记录的直接前一 immutable/current base，不提供任意历史选择。
- Git adapter、签名/KMS、审批/RBAC、多 Runtime orchestration、artifact GC、JAR binding 继续后置。
