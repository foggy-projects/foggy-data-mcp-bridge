---
doc_role: version-readme
doc_type: progress
intended_for: root-controller / execution-agent / reviewer / signoff-owner
purpose: 定义 9.5.5 Runtime 模型交付稳定化与可运维性的范围、进入条件和非目标。
version: 9.5.5
theme: runtime-model-delivery-operability
status: ACTIVE
recorded_at: 2026-08-01
---

# 9.5.5 Runtime 模型交付稳定化与可运维性

## Work items

| Work item | 状态 | 目标 |
|---|---|---|
| [Artifact/store 生命周期基础探针](workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md) | APPROVED | 盘点双 store、引用图、中断遗留与安全 retention/diagnostics 边界；不修改生产实现 |

## 目标

9.5.5 不继续扩张模型创作功能面，而是收口 9.5.3～9.5.4 已接受的 workspace、publish、release
package 和 production promotion：确认磁盘 artifact/store 生命周期、重启遗留物、容量与诊断边界，形成
可安全实施和验收的运维契约。

## 当前范围

1. 只读盘点 workspace、published artifact、release package 的非持久化边界、promotion attempt 和
   published root 临时文件的 owner、引用关系、状态转换、保留条件与现有失败模式。
2. 定义 fail-closed cleanup/retention 策略，证明不会删除 live、rollback、recovery 或 foreign data
   仍需要的内容。
3. 识别应暴露的容量、状态和人工处置诊断；不把日志或目录猜测当作稳定 API。
4. 技术探针签收后，再决定是否分别冻结 cleanup/retention API 与 Console 运维入口。
5. 保持单 Runtime 进程、非 shared-NFS writer 的当前一致性边界，除非后续独立 workitem 明确扩展。

## 非目标

- Git adapter、JAR 多 Namespace binding、JAR 升级或 fork。
- package signing/KMS、审批/RBAC、用户审计或跨 Runtime orchestration。
- 任意历史 rollback、package registry、共享存储协调或后台自动升级。
- VS Code 插件、Console Agent、通用 Web IDE 或与运维无关的大型 Console 改版。
- 在技术探针前直接实现删除、GC、自动清理或迁移现有 store。

## 进入与完成条件

- 首先使用 delivery-spec 流程冻结一个只读技术探针，覆盖真实源码、磁盘格式、引用图和重启路径。
- 实现 workitem 必须等待探针独立验收；cleanup 的 ownership、reference safety 和 failure recovery
  属于不可豁免项。
- 9.5.1 与 9.5.2 的历史 signoff backlog 独立处理，不把补签收伪装成 9.5.5 产品能力。
- 本迭代完成时更新 [9.5 主版本索引](../README.md)，历史平铺目录迁移仍由独立治理任务处理。
