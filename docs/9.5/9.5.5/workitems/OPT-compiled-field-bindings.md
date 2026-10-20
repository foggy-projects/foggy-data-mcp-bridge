---
doc_type: implementation-plan
status: DONE
date: 2026-10-21
intended_for: engine maintainers
purpose: Compile and reuse field projection bindings and permission dependency graphs without changing query contracts.
---

# 字段投影与依赖图复用优化

前置交付为 #147、Launcher 0.1.28 与私有 Maven。用户授权在该修复交付后直接规划并推进第五阶段。
目标迭代沿用 9.5.5，直接在 main 实施，不改变公共 Model SPI v2。

## 现状与目标

当前 QueryFieldBindings.logicalRows 对每行每列重复解析 AS 并查 QM 列；应在每次投影入口编译一次
不可变 Projection，随后只进行结果键映射。Binding 显式保留查询表达式、输出键和实际来源列；
schema、ORDER BY 权限解析、pivot 行映射与 SQL 包装使用同一绑定入口。

当前计算依赖在权限、元数据、物理列映射和公式注入中反复递归解析。增加不可变依赖图，
预编译直接边、基础依赖闭包与拓扑顺序，并将模型图放在 QueryModelSupport 实例内。
模型加载时构建图，刷新通过新模型实例自然隔离；不得建立按模型名称索引的全局缓存。
现有可变 DTO 通过语义快照比较识别变化，变化时构建新图再替换，旧图保持不可变。
请求定义在独立图中覆盖，预定义模型公式继续优先，不能让请求覆盖削弱权限依赖。

## 影响面与兼容性

- name/alias/ref、AS、pivot 对外逻辑键及 schema/queryName 合同沿用 #147；保留兼容静态辅助入口。
- 不缓存用户权限决定；只复用表达式依赖。fieldAccess、deniedColumns 每次请求仍独立校验。
- parser/窗口/后聚合语法不新增规则；非法表达式、循环和重名冲突保持明确拒绝。
- 不共享请求可变定义，不缓存数据行，不更改 catalog/generation/namespace 身份。
- 请求图在计算依赖发生变化时重新计算传递闭包；不得把模型基础依赖闭包直接贴到覆盖图。
- CI 新增 SQLite 专项、四库矩阵和实际 Launcher 制品冒烟；外部服务使用隔离夹具，
  检查非零测试数、必需测试类、失败/跳过与退出码，避免零测试假绿。

## 实施与验收

1. Projection/Binding 编译与共用，证明模型查找次数只与所选列数相关，不随行数增长。
2. 模型依赖图、请求覆盖、权限/元数据/物理映射/注入接线；覆盖图不可变、重复复用、
   可变定义失效、模型实例隔离、覆盖传递依赖、窗口依赖、循环与后聚合兼容。
3. 固化可运行的回归/矩阵与制品脚本、CI 工作流、文档。
4. 先专项，后完整 reactor 和四数据库；执行模型刷新/缓存/权限相关 IT、实际构建 JAR smoke。
   对已有功能使用真实 SQL/fixtures 比对，不用耗时阈值代替正确性或性能结构断言。

源码优化完成并验证后独立提交。0.1.28 保持为前置修复发行，不将后续代码冒充已包含在该制品中。


## 实现结果

- QueryFieldBindings 新增不可变 Binding/Projection，兼容静态入口保留；一万行投影回归证明每个选中列仅查找一次，别名交换不会覆盖源行。
- CalculatedDependencyGraph 统一直接边、基础依赖闭包与拓扑顺序，连接模型加载、物理列映射、元数据、字段权限、公式注入和 pivot。模型图按实例缓存；模型定义语义快照变化时重建，请求图独立且模型预定义优先。
- 保留 `SUM(amount) AS amount` 与预定义公式共存的处理阶段。请求注入允许收集暂态自引用边，实际依赖展开仍拒绝循环；模型公式在加载时验证。
- 新增可重复执行的五库专项入口、实际 JAR 检查和 GitHub Actions。发布脚本在任何外部 tag/release 操作前验证 13 项字段绑定/权限/刷新行为，核对 JAR 摘要及 JVM 退出状态；证据位于发行资产目录之外。

## 兼容与边界

不改变 name/alias/ref、schema/queryName 或 Model SPI v2；不引入全局模型名缓存，不缓存用户权限决定。旧静态方法和可变 Java DTO 调用保留。模型刷新通过新实例隔离，且真实 JAR 已验证修改公式依赖后权限随之收紧。

优化收益以解析/查找次数、不可变性和实例隔离断言证明；本轮未给出吞吐量或延迟百分比。前端源码未修改，沿用前置修复已通过的 571 项前端单测、26 项浏览器检查及同一份构建资产。

初轮专项暴露了 fail-closed 诊断包装和 Mockito 真实 getter 接线的兼容问题，修复后重跑通过；未以放宽断言或跳过失败替代修复。Linux 脚本单测首次缺少 jq，补齐后通过，CI 显式安装该依赖。原始失败与重跑记录均保留在隔离证据目录。

测试使用独立 SQLite 与容器数据库/外部服务，未连接业务实例。MCP 的直连工具与配置集成测试纳入验收；需要在线 LLM 凭据的付费模型调用不启用。GitHub Actions 的远端运行状态单独记录，不用本地通过结果冒充远端 CI 通过。


## 最终验收（2026-10-21）

| 验证 | 测试数 | 结果 |
| --- | ---: | --- |
| SQLite 专项 | 292 | 零失败/错误/跳过 |
| Windows 完整 reactor | 5921 | 零失败/错误，11 项平台跳过 |
| Linux 完整 reactor | 5921 | 零失败/错误/跳过，补齐 Windows 跳过 |
| Linux MySQL 5.7 / MySQL 8 / PostgreSQL / SQL Server | 110 / 140 / 140 / 110 | 全通过，无缺失测试类 |
| Windows 自动建库门禁，同四库 | 110 / 140 / 140 / 110 | 全通过，四个临时容器均自动清理 |
| 公共 IT | 410 | 全通过 |
| Redis / MongoDB / MCP / 向量 / MySQL 5.7 预聚合 | 3 / 31 / 23 / 20 / 3 | 全通过 |
| Python 发布脚本 / 门禁脚本 | 38 / 6 | 全通过 |
| 新构建 Launcher 实际制品检查 | 13 | 全通过，包含刷新后的权限依赖重建 |

仓库 47 个 IT 类全部覆盖，无缺失。4314 个源码文件与 Linux 测试副本摘要一致；41 个前端资产未变。
主分支合同、Black、Ruff 指定错误检查、工作流 YAML 和 git diff --check 均通过。
本轮新增 14 项 Java 测试；性能结构验证覆盖一万行投影仅解析一次/列与 64 个并发读者复用模型图。

完整本地证据位于 `target/field-binding-optimization/`，包括 final-summary.json、native-source-final.json、
各批次 summary.json、artifact-smoke.json、provisioned-cleanup.json、cleanup.json。
本次 10 个测试服务/runner 容器及专属网络已按归属标签核对后清理；无关容器保持运行。
原始日志及临时运行配置不提交。GitHub Actions 已配置，远端执行结果不属于上述本地验收结论。

本地优化 JAR SHA-256：`b7cca55f2850fa7d9f4d077f7027ca8c196b62bbb2ef787f52ed02b515b740d5`。
此 JAR 验证第五阶段源码，不是已发布的 0.1.28；0.1.28 与私有快照的发布摘要以独立发布记录为准。
