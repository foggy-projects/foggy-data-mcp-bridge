---
acceptance_scope: feature
version: next-unassigned
target: mcp-chart-export-phases-1-to-4
status: signed-off
decision: accepted
signed_off_by: codex-signoff-reviewer
signed_off_at: 2026-07-26
blocking_items: []
follow_up_required: no
assurance_level: standard
---

# MCP 图表导出第一至第四阶段最终签收

## 签收结论

第一至第四阶段已按设计完成并通过验收。默认 XChart 路径可在 JVM 内直接生成图片，
不要求浏览器或额外渲染服务；ECharts 保留为显式外部渲染实现。普通 DSL、DSL_CTE、
timeWindow 与 flat Pivot 均只将最终查询结果字段交给原生图表配置，不要求图表配置理解
查询内部结构。

本轮没有阻断项，也不需要用户补充 LLM Key。

## 交付范围

- 第一阶段：冻结 `dataset.export_with_xchart` 与
  `dataset.export_with_echarts` 双工具合同。
- 第二阶段：实现 XChart、ECharts renderer、共享导出执行器、图片存储和下载路由。
- 第三阶段：完成原生 XChart Adapter Config、ECharts Option 的 Schema、工具描述、
  示例和回归测试。
- 第四阶段：补齐 Pivot 请求透传、Lite 配置模型目录按需物化，并完成真实 launcher、
  图片下载与 LLM tool-calling 验证。

## 核心验收结果

| 验收项 | 结果 |
|---|---|
| 默认 XChart JVM 内渲染，无 sidecar | pass |
| 显式 ECharts 外部渲染，无隐式回退 | pass |
| 直接数据生成图表 | pass |
| 普通 DSL + XChart | pass |
| DSL_CTE + XChart | pass |
| timeWindow + XChart | pass |
| flat Pivot + XChart | pass |
| 非 flat Pivot 拒绝与 totals 过滤 | pass |
| Lite 配置模型目录仅物化配置子集 | pass |
| 模型子集刷新经过 catalog authority | pass |
| 图片 URL 下载与 PNG 文件校验 | pass |
| 真实 LLM 模型发现、查询及错误恢复 | pass |
| ECharts 服务不可用时保留查询数据并返回 `chartError` | pass |

## 验证证据

- model engine 全量测试：3,181 passed，0 failures，0 errors，2 skipped。
- MCP 全量测试：522 passed，0 failures，0 errors，0 skipped。
- launcher 定向 smoke tests：2 passed，0 failures，0 errors。
- launcher clean fat jar package：BUILD SUCCESS。
- 28 模块 package reactor：BUILD SUCCESS。
- 真实 launcher：健康检查通过，Lite 目录只返回配置的 4 个模型。
- 真实 Pivot：返回 4 行 flat 数据，生成 900 × 540 PNG，22,686 bytes。
- 真实 LLM：`gpt-5.4-mini` 在 19.745 秒内完成 4 次成功工具调用，并返回正确的订单
  状态聚合结果。
- `git diff --check`：通过，仅存在工作区历史 CRLF 转换提示。

详细运行数据见
`docs/design/mcp-chart-export-runtime-validation-20260726.md`。

## 架构审查

- `SemanticModelCatalogService.modelCatalogView` 通过
  `CatalogRefreshCoordinator.refresh(CatalogRefreshRequest.models(...))`
  请求模型级刷新，没有直接调用 loader 或绕过目录发布权威。
- 子集 view 的名称、别名、模型对象与 provenance 绑定同一 catalog identity。
- `ModelCatalogService` 先确定显式或配置模型列表，再请求相应子集；动态发现和显式
  namespace 的既有语义保持不变。
- Pivot 解析集中在 `SemanticQueryPayloadMapper`，本地 MCP accessor 不重复实现协议转换。

## 非阻断风险

- 本机未部署真实 ECharts renderer，因此 ECharts 成功路径使用 HTTP/WireMock 测试覆盖；
  真实运行验证覆盖了服务不可用时的显式失败语义。
- 历史脚本 `scripts/verify-v933-batch6-catalog-authority.sh` 仍断言一个已经删除的 dead
  watcher 方法，静态断言已过时；本次没有为了通过旧脚本恢复死代码。
- XChart Adapter Config 只承诺当前 Schema 声明的能力，不等同于暴露完整 XChart Java
  API。

## 最终决定

- decision: accepted
- blocking_items: none
- follow_up_required: no
- acceptance_status: signed-off

