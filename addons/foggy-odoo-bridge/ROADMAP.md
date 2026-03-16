# Foggy Odoo Bridge — Roadmap

## v1.0 (Current)

- [x] MCP Gateway (JSON-RPC endpoint with API Key auth)
- [x] Permission Bridge (ir.model.access → tool filtering, ir.rule → slice injection)
- [x] 9 Odoo models (sale.order, sale.order.line, purchase.order, account.move, stock.picking, hr.employee, res.partner, res.company, crm.lead)
- [x] Closure table hierarchy (child_of/parent_of → selfAndDescendantsOf/selfAndAncestorsOf)
- [x] AI Chat (embedded LLM chat with tool calling, litellm multi-provider)
- [x] Business Context Prompt (admin-defined custom prompt in Settings)
- [x] My API Key (non-admin user self-service)
- [x] Health endpoint with diagnostics
- [ ] Setup Wizard (automated first-run configuration)

## v1.1 — Enhanced AI Chat

### User Prompt Memory (方案 B)
AI 记住用户定义的业务术语和计算规则，对话中说"记住：大客户 = 年销售额 > 50万"自动存储。

**设计思路**：
- 新模型 `foggy.chat.memory`：user_id, key (术语), definition (定义), scope (global/personal)
- 新 MCP 工具 `chat.save_memory` / `chat.list_memories`：LLM 可调用
- System prompt 自动注入用户的 memory 列表
- Admin 可设全局 memory（所有用户共享），用户可设个人 memory

### Prompt Template Library
预置常用分析模板，用户一键触发：
- 销售日报/周报/月报
- CRM 漏斗分析
- 库存预警报告
- 客户活跃度分析
- 部门人效分析

**实现**：`foggy.chat.template` 模型，Chat UI 增加模板选择器。

## v1.2 — Chart Rendering

启用 `dataset.export_with_chart` 工具，AI 对话返回可视化图表。

**依赖**：
- Foggy chart-render-service 部署
- Chat UI 支持图片渲染（`<img>` in message content）
- 图表 URL 通过 Foggy 返回（云存储或本地 static）

## v2.0 — Ecosystem

- Odoo Apps Marketplace 发布
- 独立仓库（从 foggy-data-mcp-bridge monorepo 拆出）
- Plugin marketplace for additional model packs (manufacturing, project, fleet...)
- Webhook / scheduled report delivery (email/DingTalk/Slack)
- Multi-tenant SaaS mode
