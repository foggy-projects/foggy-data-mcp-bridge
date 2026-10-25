# Compose 派生入口与参数合同（#163、#164）

## 方案与实施顺序

先修复受限脚本宿主的 `model: QueryPlan` 类型分流，再统一派生参数校验与
Runtime 诊断，补齐真实 SQL、HTTP 和权限回归，最后执行全量及多数据库测试。
复用现有 `DerivedQueryPlan`、字段血缘、权限准备和 SQL 编译流程。

## 支持矩阵

| 输入 | 合同 |
| --- | --- |
| `dsl({model: 'QM', calculatedFields: [...]})` | 基础模型计算字段，保持原行为 |
| `dsl({model: prevPlan, ...})` | 对前一计划做派生查询，接受 Base、Derived、JOIN、UNION |
| `prevPlan.query({...})` | 同一派生查询合同 |
| `dsl({source: prevPlan, ...})` | 保留旧入口兼容，新用法优先使用上述公开入口 |
| 派生 `columns: ['amount * 2 as doubled']` | 支持，表达式只能引用来源已投影的列 |
| 派生 `calculatedFields: [...]`（非空） | 未支持，立即返回参数能力错误，不解释为来源缺字段 |
| 派生 `having: [...]`（非空） | 未支持，用下一层 `.query({slice: [...]})` 过滤聚合结果 |
| 派生 `calculatedFields/having: null` 或 `[]` | 无操作，保持兼容 |
| 派生 `calculatedFields/having` 其他类型 | 参数类型错误，不能静默忽略 |

新增别名作为下一层的输入字段；对它筛选时增加一层 `.query()`。已有同层
`slice` 不可引用新别名的合同不变。基础计算列的窗口、聚合与依赖规则不
自动扩展到派生层。

```javascript
const doubled = bills.query({
  columns: ['customer_id', 'bill_amount', 'bill_amount * 2 as double_amount']
});
return {plans: dsl({
  model: doubled,
  columns: ['customer_id', 'bill_amount', 'double_amount'],
  slice: [{field: 'double_amount', op: '>', value: 10}],
  orderBy: ['-double_amount']
})};
```

## 输入与诊断

脚本宿主将 `model` 中的 QM 名称送入现有基础入口，将 QueryPlan 送入现有
`source` 内核。两个非空来源参数互斥；错误类型不能依赖 Java 强制转换报错。
Java `FromOptions.model(String)`、`source(QueryPlan)` 和 `QueryOptions` 的签名保持。
脚本入口共用排序归一化，兼容字符串和 `{field, dir}` 对象；`offset` 保持为
`start` 的兼容别名，显式 `start` 优先，避免改写入口后丢失排序或分页。

参数错误保留 Runtime 的顶层 `COMPOSE_SCRIPT_INVALID` 等模式相关错误码，
在诊断中追加具体原因码、`plan-build` 阶段及 `dsl.model`、
`query.calculatedFields` 等路径，并提供操作建议。不会回显参数原值、
内部 Java 类加载器或物理字段信息。能力拒绝不能通过放宽权限修复，
不声明它可以不经语义核对自动改写。

## 影响面与兼容性

变化集中在受限 Compose 宿主、派生参数入口和 Runtime 错误翻译。字符串 QM、
已有 `.query()`、旧 `source`、JSON envelope、name/alias、目录与 namespace
绑定保持。新增计算列仍按来源输出列和原有权限血缘校验，不能读取被省略、
拒绝或隐藏的基础字段。validate 和 preview 只规划，不执行数据查询。

此前 `.query()` 静默忽略的非空 `calculatedFields/having` 改为明确拒绝；
这是有意收紧错误输入。空值与空数组保持无操作。未知的其他扩展参数不在
本轮统一拒绝，避免无关兼容性变化。

若后续增加派生 `calculatedFields`，需单独定义依赖 DAG、重名与循环、聚合/
窗口阶段、过滤时点、plan hash 及权限血缘，不能简单复用基础 QM 的处理器。
本轮不改变 SQL 方言或新增此能力。

上一轮未提交的 Pivot 修复逐文件保留；测试不执行仓库根目录 clean，日志、
请求响应、XML、源码与构建快照保存到仓库外的独立证据目录。
