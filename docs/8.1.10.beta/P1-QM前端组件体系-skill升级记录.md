# P1-QM前端组件体系 skill 升级记录

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`已完成`
- 完成日期：2026-04-05
- 责任项目：`foggy-data-mcp-bridge`

## 完成记录

### 开发进度：已完成

本轮已完成以下 skill 收口：

| Skill | 结果 | 关键调整 |
|------|------|------|
| `frontend-component-generator` | 已升级 | 切到 `frontend-meta v1`、支持 `generated → modules → pages`、明确 `members/query` / `query/create` 交付口径 |
| `foggy-frontend-init` | 已升级 | 改成新前端组件体系初始化，不再默认引导老 `dslQuery.ts` |
| `qm-generate` | 已升级 | 增加前端组件兼容要求、默认排序/member lookup/层级维度要求、补 `qm-validate/frontend-meta` 后置验证 |
| `tm-generate` | 已升级 | 去除对 `mysql-docker-client` 的隐含依赖，补维度、字典、层级、主时间字段要求 |
| `foggy-plan-execution-docs` | 已升级 | 将 `execution-prompt/progress-template` 改为推荐项，新增 `acceptance-evidence` 交付模式 |

同步策略：

- 工作区副本：`D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/.claude/skills/`
- 用户目录副本：`C:/Users/oldse/.claude/skills/`

本轮已把同名 skill 的两套副本统一，避免后续命中同名 skill 时出现指令漂移。

### 测试进度：已完成

- 技术规范签收段已复核，签收结论仍保持有效
- 双副本 skill 已做文本级 diff 复核
- `qm-generate`、`tm-generate` 的哈希差异确认为换行差异，文本内容一致

### 体验进度：已完成

- 现在让 agent 执行前端组件接入时，不再回退到旧接口族
- 现在让 agent 生成 TM/QM 时，会显式考虑前端组件消费链路
- 现在让 agent 生成执行文档时，可以显式产出验收证据材料，而不是只停留在 requirement/plan

## 背景

`P1-QM 前端组件体系` 已经完成并通过验收，但相关 skill 仍保留旧时代假设，主要问题有：

- `frontend-component-generator` 仍以老 schema/单组件思维描述生成链路
- `foggy-frontend-init` 仍指向旧的 `/jdbc-model/query-model/v2/*`
- `qm-generate` / `tm-generate` 没把前端组件消费约束纳入默认产出标准
- `foggy-plan-execution-docs` 对 `execution-prompt` 的定位前后矛盾，也未覆盖“验收证据包”模式
- 工作区与用户目录存在同名 skill 双副本，易发生内容漂移

## 问题定义

如果不升级 skill，会持续带来三个问题：

1. 新能力已交付，但 agent 仍按旧流程生成代码或给出旧指导。
2. TM/QM 虽能生成，但不保证天然适配 `frontend-meta`、代码生成器和 DataViewer 页面。
3. 同名 skill 双副本长期分叉，导致同一请求在不同入口下得到不同指令。

## 目标

- 让 skill 默认匹配当前 `P1-QM 前端组件体系` 的真实交付口径
- 让 TM/QM 生成阶段就显式考虑前端组件消费链路
- 让执行文档技能能够覆盖“验收材料 + 签收”型交付包
- 让双副本 skill 内容一致，降低后续维护成本

## 任务拆分 / 责任

### 1. 前端接入类 skill
- `frontend-component-generator`
- `foggy-frontend-init`

责任：对齐 `frontend-meta v1`、标准组件、业务接入、代码生成器规范。

### 2. 模型生成类 skill
- `qm-generate`
- `tm-generate`

责任：对齐前端组件消费要求，把字段 caption、dict、hierarchy、默认排序前置到建模阶段。

### 3. 文档编排类 skill
- `foggy-plan-execution-docs`

责任：对齐版本化交付包，补充 `acceptance-evidence` 输出模式。

### 4. 重复副本治理

责任：同步 `.claude/skills` 与 `C:/Users/oldse/.claude/skills` 中的同名副本，避免内容长期漂移。

## 验收标准

- `frontend-component-generator` 明确使用 `frontend-meta v1`
- `foggy-frontend-init` 不再默认引导到旧接口族
- `qm-generate` 明确前端组件兼容要求和后置验证
- `tm-generate` 不再依赖不存在的 `mysql-docker-client` 假设
- `foggy-plan-execution-docs` 支持验收证据型交付
- 工作区副本与用户目录副本文本一致

## 当前风险与后续建议

当前已收口，但还有一个治理点值得继续做：

1. 现在是“双副本同步”，不是“单一事实源”。
2. 后续若继续高频迭代 skill，仍建议抽一层共享参考文档或建立同步脚本。
3. 如果后续要发布到公司 skill 市场，建议在发布前补一份变更摘要和版本号策略。

## 非目标

- 本条不处理所有历史 skill 的统一重构
- 本条不自动发布 skill 到外部市场或公司 marketplace
- 本条不替代具体业务项目的接入验证
