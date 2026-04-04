# P1-QM 前端组件体系 - 技术规范

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`已锁定`
- 本文档替代：`P1-QM前端生成与业务接入/` 目录下全部讨论稿（已归档至 `archive/`）

## 一句话目标
为业务系统提供一套标准前端组件体系，让业务开发者只需要 QM 模型名，就能获得可直接运行的表格、查询、下拉组件——减少重复劳动、杜绝各业务系统各自为战、为后续数据分析组件打好基础。

## 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                      业务系统页面层                           │
│  pages/order/OrderListPage.vue                              │
└──────────────────────┬──────────────────────────────────────┘
                       │ 使用
┌──────────────────────▼──────────────────────────────────────┐
│                    业务包装层 (手工)                          │
│  modules/order/OrderListModule.vue                          │
│  modules/order/order-module.config.ts                       │
│  modules/order/order-query-hooks.ts                         │
└──────────────────────┬──────────────────────────────────────┘
                       │ 引用
┌──────────────────────▼──────────────────────────────────────┐
│                    生成层 (自动)                              │
│  generated/qm/order/FactOrder.types.ts                      │
│  generated/qm/order/FactOrder.table.schema.ts               │
│  generated/qm/order/FactOrder.query.schema.ts               │
│  generated/qm/order/FactOrder.api.ts                        │
│  generated/qm/order/FactOrderTable.vue                      │
│  generated/qm/order/index.ts                                │
└──────────────────────┬──────────────────────────────────────┘
                       │ 依赖
┌──────────────────────▼──────────────────────────────────────┐
│               foggy-data-viewer 标准组件                     │
│  DataTableWithSearch / QueryPanel / SelectFilter / ...       │
│  useTableQuery / useQueryState / globalQueryHooks            │
│  MemberQueryRequest / MemberQueryResponse / ...              │
└──────────────────────┬──────────────────────────────────────┘
                       │ 调用
┌──────────────────────▼──────────────────────────────────────┐
│                 后端服务 (Java)                               │
│  GET  /data-viewer/api/schema/{qmModel}     → 元数据        │
│  POST /data-viewer/api/members/query         → 维度成员      │
│  POST /data-viewer/api/query/{model}/{qid}/data → 查询数据   │
└─────────────────────────────────────────────────────────────┘
```

## 子规范索引

| 编号 | 文档 | 优先级 | 核心交付 |
|------|------|--------|---------|
| S1 | [前端元数据契约](P1-QM前端组件体系/S1-前端元数据契约.md) | **P0** | `frontend-meta v1` JSON 契约、V3→v1 映射规则、后端接口 |
| S2 | [标准组件规范](P1-QM前端组件体系/S2-标准组件规范.md) | **P0** | 维度成员过滤接口、QueryPanel、SelectFilter 升级、下拉组件契约 |
| S3 | [业务接入规范](P1-QM前端组件体系/S3-业务接入规范.md) | **P1** | 三层目录、参数合并、列覆盖、query 覆盖、扩展点清单 |
| S4 | [代码生成器规范](P1-QM前端组件体系/S4-代码生成器规范.md) | **P2** | 生成器输入输出、模板职责、CLI 流程、防覆盖 |

**端到端示例**：[examples/](P1-QM前端组件体系/examples/) — 基于 `FactOrderQueryModel` 的完整代码示例

## 实施顺序与依赖

```
Phase 1 (P0): 基石
  S1 前端元数据契约     ← 所有后续工作的前置
  S2 标准组件规范       ← 维度成员过滤可与 S1 并行（已有内部 API）

Phase 2 (P1): 接入
  S3 业务接入规范       ← 依赖 S1 的字段结构、S2 的组件接口

Phase 3 (P2): 自动化
  S4 代码生成器规范     ← 依赖 S1+S2+S3 全部稳定
```

## 核心设计决策

### 1. 前端专用元数据，不直接复用 V3
V3 是面向 LLM 的语义元数据，包含 `prompt`、`meta` 拼接字符串等 LLM 友好但前端不友好的结构。前端需要的是**稳定、类型安全、面向渲染**的契约。

结论：后端新增 `frontend-meta v1` 接口，内部复用 V3 生成逻辑，但对外输出收敛后的前端专用结构。详见 [S1](P1-QM前端组件体系/S1-前端元数据契约.md)。

### 2. 封装优先，不直接暴露底层
- 前端组件不直接调用 `/jdbc-model/...`
- 前端组件不直接依赖 synthetic member-QM 名称
- `data-viewer` 作为前端消费封装层，负责 schema 映射和成员查询适配

### 3. 查询条件是一等模型
查询条件不再只从列定义派生。新增独立 `QueryFieldSchema` / `QuerySchema`，支持传统查询区和列筛选并存。详见 [S2](P1-QM前端组件体系/S2-标准组件规范.md)。

### 4. 生成产物必须薄
生成的 Vue 组件只做 schema 组装和标准组件调用，不承载业务逻辑。业务动作、权限、工具栏按钮全部在包装层处理。

### 5. 统一 DSL 出口
无论条件来自查询区、列筛选、hidden 默认条件，最终全部编译为 `SliceRequestDef[]` 进入后端。

## 全局验收标准

- [ ] 后端 `GET /data-viewer/api/frontend-meta/{qmModel}` 返回 `frontend-meta v1` JSON
- [ ] 前端通过 `fetchFrontendMeta(qmModel)` 可获取标准元数据
- [ ] `dimension` 类型字段的 DSL slice 始终使用 `selectionFieldName`（如 `team$id`）而非显示字段
- [ ] `POST /data-viewer/api/members/query` 支持 keyword / 分页 / selectedValues 回填 / childrenOf
- [ ] QueryPanel + ColumnFilters 可并存，共享同一份 QueryState
- [ ] 生成器可基于一个 QM 元数据 JSON 生成 types + schema + api + Vue 组件
- [ ] 生成产物不会被业务代码修改、不会被生成器覆盖业务代码
- [ ] 至少一个业务模块完成三层接入验证（generated → modules → pages）

## 后续版本预留（不在 8.1.10.beta 范围内）
- 查询字段分组
- 高级查询（组合表达式编辑器）
- `groupBy` 交互与透视表
- 字典 / 维度 / 属性 distinct 的统一 lookup 契约
- 完整无限层懒加载树 UI 体验打磨
