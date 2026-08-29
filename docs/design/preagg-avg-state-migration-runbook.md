---
doc_role: migration-runbook
status: accepted
implementation_status: implemented
last_reviewed: 2026-08-29
applies_to_commits: 562e469e+
affected_modules: foggy-dataset-model-engine, foggy-dataset-model-preagg
---

# AVG 预聚合 SUM/COUNT 状态迁移手册

## 1. 目的与结论

从 `562e469e` 起，AVG 预聚合不再物化单一平均值，而是物化两个可合并状态：

```text
<measureColumnName>__sum
<measureColumnName>__count
```

其中 `__count` 必须对应 `COUNT(source_expr)`，不是 `COUNT(*)`。该变化修正不同分组样本量下的
AVG rollup 和 `totalData`，但属于物理 schema 不兼容变更。当前版本不提供自动在线迁移，也不允许
新旧应用节点或新旧物化表混用。

## 2. 兼容性矩阵

| 应用版本 | AVG 物化表 | 结果 |
| --- | --- | --- |
| 旧版 | 单 AVG 列 | 仅旧基线可用 |
| 新版 | `__sum/__count` 双状态 | 唯一受支持组合 |
| 新版 | 单 AVG 列 | 不兼容；会缺少状态列，禁止放量 |
| 旧版 | `__sum/__count` 双状态 | 不兼容；旧版仍按单值列读取 |
| 新旧节点并行 | 任一共享物化表 | 禁止；没有 mixed-version 协议 |

FULL 预聚合允许 predefined AVG measure 等粒度读取和粗粒度 rollup。HYBRID AVG 仍必须
fail closed；若 explain/debug 显示 AVG 进入 HYBRID 路由，应视为发布阻断。

## 3. 迁移前盘点

在变更窗口前形成可审计清单：

1. 列出所有 `measureAggregations` 含 AVG 的预聚合定义、物理表、数据源和刷新任务；
2. 对每个 AVG 记录 `measureColumnNames[measure]` 前缀及预期的 `__sum/__count` 列名；
3. 记录当前应用版本、表 DDL、行数、刷新水位、调度状态和最近一次成功 FULL refresh；
4. 备份旧表 schema 和数据，或保留可原子切回的旧版本表；
5. 预留一次完整 FULL refresh 的时间与容量。INCREMENTAL refresh 不能从历史单 AVG 值反推出
   历史 SUM/COUNT，因此不能替代首次 FULL refresh；
6. 选定代表性事实查询，至少包含不等样本量、NULL measure、过滤、多维分组和无 groupBy。

若无法完整枚举 AVG 预聚合或无法保留可验证的事实范围，停止迁移。

## 4. 推荐切换流程

### 4.1 默认：协调停机重建

1. 停止查询流量、预聚合调度器和 refresh worker，确认没有旧节点继续读写目标表；
2. 保存旧应用制品、配置、表 DDL、数据备份和刷新元数据；
3. 部署新制品，但保持查询 rewrite、调度和对外流量关闭；不得滚动形成新旧节点并行；
4. 使用新版本 DDL builder 删除并重建目标 AVG 预聚合表，确认每个 AVG 只有
   `<prefix>__sum` 与 `<prefix>__count` 两个状态列，没有旧单 AVG 物理列；
5. 对所有受影响表执行 FULL refresh；
6. 完成第 5 节验证；任一检查失败都不得恢复流量；
7. 先启用新节点的只读验证流量，再启用查询 rewrite；最后恢复 refresh 调度和正常流量；
8. 观察一个完整调度周期，确认后续 INCREMENTAL refresh 继续写入 SUM/COUNT states。

### 4.2 可选：影子表构建后切换

若部署系统支持版本化物理表名、配置原子切换和可回退元数据，可以用新名称创建影子表并完成 FULL
refresh，再在所有旧节点停止后一次性切换配置。影子表降低停机时长，但不改变兼容性规则：切换点
前后仍不能让新旧节点同时访问同一个逻辑预聚合，新运行时也不能读取旧单 AVG 表。

## 5. 上线验证

每张表至少验证：

1. schema 同时存在 `<prefix>__sum` 和 `<prefix>__count`，count 类型可安全承载累计行数；
2. `__count >= 0`，且全部 source expression 为 NULL 的桶其 count 为 0；
3. 对相同事实过滤和 grain，比较：

   ```sql
   SUM(preagg.<prefix>__sum)
   / NULLIF(SUM(preagg.<prefix>__count), 0)
   ```

   与事实表 `AVG(source_expr)`，结果必须在既定 decimal 容差内一致；
4. MAIN 分组结果、SUM、COUNT、MIN、MAX 保持不变；
5. `returnTotal=true` 不包含最终 `ORDER BY/LIMIT/OFFSET`，分页前后 totalData 相同；
6. `HistoricalFullTruckWaybillQuery` 年度分组保持 2026/2025 原值，total AVG 约为
   `8888.45308896`，不得为 `8696.80124621`；
7. explain/debug 对 FULL AVG 显示状态 rollup；AVG HYBRID 和无法证明 lineage 的 advanced AVG 必须
   回退事实计划，不能静默使用单值平均。

## 6. 回滚

1. 再次停止流量、调度器和 refresh worker；
2. 停止所有新节点，恢复旧应用制品和与其匹配的旧配置；
3. 原子切回保留的旧表，或从备份恢复旧 schema/数据；若没有可恢复数据，只能用旧版定义重新建表并
   执行旧版 FULL refresh；
4. 验证旧节点只读取旧单 AVG 表后再恢复流量和调度；
5. 保留失败的新表与日志用于分析，不得把新版双状态表直接交给旧节点。

回滚是“应用版本 + 物理 schema + 配置”整体回滚，不能只回滚其中一项。

## 7. 发布阻断条件

出现任一情况立即停止：

- 仍有旧节点或旧 refresh worker 在线；
- 任一 AVG 缺少 `__sum` 或 `__count`；
- 首次只完成 INCREMENTAL、未完成 FULL refresh；
- state ratio 与事实 AVG 不一致；
- AVG 被路由到 HYBRID；
- 无法证明可恢复到旧应用与旧物化表的配对状态。

