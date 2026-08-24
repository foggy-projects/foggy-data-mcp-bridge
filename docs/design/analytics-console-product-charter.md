---
doc_role: product-charter
doc_type: requirement
intended_for: analytics-console product / runtime integrator / reviewer
purpose: 冻结独立 Analytics Console 的用户、权限、Runtime 与 FAP 产品边界。
status: current
last_reviewed: 2026-08-24
---

# Analytics Console 产品章程

## 产品定位

Analytics Console 是面向企业管理员和 Analytics Designer 的独立报表/Dashboard 设计与发布产品，
同时为普通业务用户提供只读消费入口。它不是 FAP Workbench，也不是面向 Runtime 运维和 TM/QM
开发者的 Runtime Console。

首版采用编辑部式工作流：Designer 在受控草稿中编辑定义、校验、预览并单向发布；Viewer 只能看到
对自己可见的已发布资产。LLM 通过 FAP 协助设计，但不能绕过同一套保存、校验、发布和权限判断。

## 权属与依赖

| 能力 | owner | 约束 |
|---|---|---|
| Report/Dashboard 文件合同与执行 | Java Analytics Runtime | 不保存产品 owner、目录、ACL 或用户身份 |
| 资产 owner、目录、展示 ACL、发布状态 | Analytics Console | 独立持久化，不同步到 TMS |
| Agent 编排与任务生命周期 | FAP | Console 只通过服务端 gateway 使用，不暴露凭据给浏览器 |
| Agent 的 Analytics 函数 | product-neutral Function SDK | Console/TMS 均可独立适配，不成为 FAP 专属 SDK |
| TMS Analytics | TMS | 只消费 TMS 自己登记并发布的 exact template |

## 首版角色

- `ADMIN`：管理目录、所有 Console 资产和展示 ACL。
- `DESIGNER`：创建资产，只修改自己拥有的草稿，执行校验、预览和发布。
- `VIEWER`：只读取被授权的已发布资产和渲染结果。

角色来自宿主提供的 `AnalyticsConsoleSubjectResolver`。默认不信任浏览器提交的 owner、role、authority
或 FAP credential；本地开发身份必须显式启用且不得宣称为生产认证。

## 生命周期

```text
registered runtime-owned bundle
  -> Console draft metadata
  -> definition save with exact revision CAS
  -> validate + preview
  -> Console PUBLISHED exact revision
  -> viewer render
```

首版发布是单向的。发布后 Console 不再修改对应 technical Bundle；新版本使用新的 runtime-owned Bundle
草稿，产品标题、目录和 audience 可重新绑定。此限制避免在尚无历史 revision store 时让旧发布物被原地
覆盖。完整 draft fork、immutable publication store 和跨环境 promotion 另立后续 workitem。

## FAP 边界

- Console 服务端冻结 Skill、Capability、model config 和 workspace 选择；浏览器只提交 prompt 与 Console
  asset/conversation 标识。
- FAP Subject credential 由服务端 resolver 提供，不能写入产品目录、定义文件、日志或 API 响应。
- 入站回调同时校验 Provider、exact Capability ID/revision、Ask request/invocation、Conversation、Execution、
  Task 与当前 Console Subject；浏览器 API 和 FAP callback 使用两套独立请求防护。
- Agent 生成的修改先成为建议或草稿，仍需通过 exact revision save、validate 和显式 publish。
- 首版不定义 MCP 工具，不把 FAP Conversation/Task 类型放入 Analytics Definition Core。

## 非目标

- 普通用户自由设计、任意 SQL/脚本/HTML/iframe 或网络请求。
- 向普通 Viewer 返回底层定义内容；Viewer 只消费受治理的 preview/render。
- 与 TMS 同步 owner、目录、ACL、菜单或发布状态。
- 在 Runtime Console 中增加 BI 页面，或让 Analytics Console 承担 TM/QM 运维。
- 自建 FAP Workbench、模型配置管理、Provider 管理或 Worker 运维。
- 多人实时协作、审批流、历史 revision registry、跨环境 promotion 和自动回填历史数据。
