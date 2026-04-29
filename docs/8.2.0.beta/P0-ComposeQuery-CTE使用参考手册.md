# Compose Query CTE (公共表表达式) 与高级编排参考手册

**版本**: 8.2.0.beta M2 (Draft)
**面向对象**: 业务分析师、AI 助手、研发工程师
**核心目标**: 介绍如何在 Compose Query 中使用派生查询、多表 JOIN、UNION 等关系复用能力，构建复杂的业务分析。

> [!NOTE]
> **脚本语言说明**：Compose Script 使用 **FSScript** 编写，其语法与 JavaScript (ES5) 基本一致——支持 `const`/`var`、对象字面量、属性访问、链式调用等标准特性。底层由 Java 端的 Rhino 引擎解释执行，运行在安全沙箱中（无文件 I/O、无网络、无 `eval`）。如果你熟悉 JavaScript，可以直接上手；唯一区别是沙箱内仅暴露 `Query`、`dsl`、`JSON`、`console.log` 等白名单 API。

> [!NOTE]
> **语法版本说明**：本手册展示的是 8.2.0.beta 规范目标语法（`Query.from(...).where(...).select(...)` 链式风格）。如果你在早期代码或 v1.6 文档中见到 `base.query({ model: '...', columns: [...], ... })` 或 `dsl({ model: '...', ... })`，它们是同一入口的旧写法，语义等价。新代码统一使用本手册的链式语法。

---

## 1. 核心概念：`QueryPlan`

**定位声明**：`Compose Query 不是实体关系 ORM（如 Hibernate），而是面向分析场景的关系编排 DSL（类似于 SQLAlchemy Core 或 jOOQ）。` 它不支持对象图导航，而是通过显式投影和显式 JOIN 构建关系代数树。

在 Compose Script 中，所有的关系操作（查询、过滤、JOIN、UNION）返回的都不是具体的数据集，而是一个 **`QueryPlan`（逻辑执行计划）**。

`QueryPlan` 具有以下特征：
- **惰性执行**：所有的动作只是在描述"我要什么数据"，直到最后调用 `.execute()` 才会真正发往数据库执行。
- **阶段性 Schema**：每次通过 `.select()` 后，输出的字段集合都会发生改变。后续的派生查询**只能看到前一步输出的字段**，看不到底层表的隐藏字段。
- **阶段切断**：每次调用 `.select()` 都会产生一个新的关系阶段（`DerivedQueryPlan`）。后续操作只能看到 `.select()` 输出的列，无法回溯到上一阶段的任何字段。`.select()` 不仅仅是"选列"，更是"创建新的关系边界"。
- **可复用**：一个 `QueryPlan` 可以被多次复用。例如 `planA.join(planB)` 然后 `planA.join(planC)`，编译器会自动将其转换为 SQL 的 `WITH` CTE。

**语义不变量与语法规则**：
- **字段引用对象 (Field References)**：`Query.from()` 返回的 `QueryPlan` 对象本身也是属性访问器。例如 `const sales = Query.from("...")` 后，`sales.partnerId` 返回一个 `FieldRef`。字段引用**绑定到它所属的具体阶段**；`.select()` 切断后，旧阶段字段不能穿透到新阶段，但新阶段会基于投影结果**生成新的字段引用集合**（例如 `orderSummary.totalAmount` 是 `select` 后新暴露的引用）。`.select()`、`.groupBy()`、`.on()` 和 `.and()` 均推荐使用字段引用对象。
- **特殊字段名**：QM 字段名如包含 `$` 分隔符（如 `category_id$caption`），在 JavaScript 中可通过属性访问器直接使用（`$` 是合法的 JS 标识符字符）。若字段名包含其他特殊字符（如 `-`），需使用索引访问：`plan["field-name"]`。
- **过滤条件使用 JSON**：`.where()` 使用结构化 JSON 对象（如 `{ field: "name", op: "=", value: ... }`），采用结构化格式可保证安全性和可序列化。**注意**：`field` 字符串引用的是**当前阶段的输出列名（即别名）**，而非底层物理字段名。例如在 `.select(...as("totalAmount"))` 后调用 `.where([{ field: "totalAmount", ... }])`，这里的 `"totalAmount"` 是上一步 select 产生的别名。
- **派生 `where` 的语义**：在 `.select()` 切断产生的新阶段后调用 `.where()`，其实现上是对派生表（CTE/subquery）结果施加**外层 `WHERE` 过滤**。在常见的聚合后筛选场景中，其效果与 SQL `HAVING` 等价，但引擎并不直接生成 `HAVING` 子句——它始终表现为"对子查询输出再过滤"。
- **排序的可见性**：`.orderBy()` 接受**字符串**参数（而非 `FieldRef`），因为排序不涉及跨表消歧——它始终作用于当前单一阶段的输出列。引用的必须是**当前阶段已经存在的字段名或别名**。若别名在 `.select()` 阶段才产生，`.orderBy()` 必须在 `.select()` **之后**调用。

## 2. 构建基础查询

基础查询通过 `Query.from(...)` 构造，它指向一个真实的底层物理模型（QueryModel, QM）。

```javascript
const premiumCustomers = Query.from("OdooResPartnerModel");

const result = premiumCustomers
    .where([
        { field: "category_id$caption", op: "contains", value: "A级" }
    ])
    .select(
        premiumCustomers.id, 
        premiumCustomers.name, 
        premiumCustomers.category_id$caption.as("categoryName")
    );
```

---

## 3. 派生查询 (Derived Query)

当我们需要在聚合后的结果上**再次进行计算或筛选**时，就可以直接在已有的 `QueryPlan` 上继续链式调用进行派生查询。

### 3.1 示例：二段聚合（先按客户汇总，再统计汇总结果的分布）

```javascript
// 第一段：计算每个客户的订单总金额
const sales = Query.from("OdooSaleOrderModel");

const orderSummary = sales
    .groupBy(sales.partnerId)
    .select(
        sales.partnerId, 
        sales.amountTotal.sum().as("totalAmount")
    );

// 第二段：在第一段的基础上，统计"总金额超过10万的客户有多少个"
// 注意 1：orderSummary 阶段只存在 partnerId 和 totalAmount 两个字段！
// 注意 2：这里的 .where() 作用于派生表输出（外层 WHERE），在此场景下效果等价于 SQL HAVING
const result = orderSummary
    .where([
        { field: "totalAmount", op: ">", value: 100000 }
    ])
    .select(orderSummary.partnerId.count().as("premiumCustomerCount"))
    .execute();
```

### 3.2 示例：TopN 后继续分析

```javascript
// 第一段：取消费最高的前 20 名客户
const sales = Query.from("OdooSaleOrderModel");

const top20 = sales
    .groupBy(sales.partnerId)
    .select(
        sales.partnerId, 
        sales.amountTotal.sum().as("totalSpent")
    )
    .orderBy("-totalSpent") // orderBy 必须跟在 select 之后，因为 totalSpent 此时才存在！
    .limit(20)
    .offset(0); 

// 第二段：在 Top 20 上继续汇总（例如：这 20 人的消费总额占比基数）
const result = top20
    .select(
        top20.partnerId.count().as("customerCount"), 
        top20.totalSpent.sum().as("top20TotalSpent")
    )
    .execute();
```

> [!NOTE]
> **`orderBy` 与 `limit` 语义**：
> - `.orderBy()` 在最终查询上始终生效。在中间阶段，仅在与 `.limit()` 共同出现时才有保留意义；如果中间阶段只有 `orderBy` 但无分页，编译器可忽略或下沉优化。
> - `.limit(n)` 和 `.offset(n)` 作用于当前关系阶段，后续派生查询看到的就是已截断后的结果。编译器不会把 `limit/offset` 随意上提或消除。

---

## 4. 关系连接 (JOIN) 

针对跨模型的业务场景，引擎支持多表联合归因。
**核心原则**：
1. **二元操作**：所有的 Join 都是两两进行的。`A.leftJoin(B)` 生成的结果再去 `join(C)`。
2. **复合条件**：使用 `.on(A.f1, B.f1).and(A.f2, B.f2)` 来支持多条件连接。
3. **强制消歧**：如果 Join 的两边有同名字段（例如 `A.name` 和 `B.name`），必须在后续的投影中**显式改名**，系统不会帮你自动覆盖。

> [!IMPORTANT]
> **BI 黄金法则：先聚合，后 Join (Aggregate First, Join Later)**
> 
> 为避免 Join 带来的笛卡尔积导致数据膨胀（例如一个客户有10个订单，直接 Join 会导致客户信息重复 10 次），请**务必先在单表完成 `groupBy` 聚合，然后再将聚合后的结果进行 Join**。除非你需要输出明细流水，否则极度不建议在多表 Join 后再去写原生的聚合表达式（如 `"SUM(orders.amount)"`），这在语义和性能上都容易埋坑。

### 4.1 Join 与 Select 投影语法

```javascript
const customers = Query.from("OdooResPartnerModel");
const orders = Query.from("OdooSaleOrderModel");

// 第一段：先对多端（事实表）进行分组聚合，打破 1:N 膨胀
const groupedOrders = orders
    .groupBy(orders.partnerId, orders.companyId)
    .select(
        orders.partnerId, 
        orders.companyId,
        orders.amountTotal.sum().as("partnerTotalSales")
    );

// 第二步：安全的 1:1 Join
const joined = customers.leftJoin(groupedOrders)
    .on(customers.id, groupedOrders.partnerId)
    .and(customers.companyId, groupedOrders.companyId);

// 第三步：强类型 Select 投影 (解决重名冲突并重命名)
const result = joined
    .select(
        customers.id,                                   // 不改名，直接继承
        customers.name.as("customerName", "客户名称"),   // 改名，并赋予显示别名(Caption)
        groupedOrders.partnerTotalSales.as("amount", "订单总金额")
    )
    .orderBy("-amount") // 排序必须跟在 select 之后调用！
    .execute();
```

### 4.2 应对三个及以上的 JOIN

对于多表 Join，通过链式调用依次执行。**所有事实表均必须先聚合再参与 Join。**

```javascript
// 延续 §4.1 中的 customers 定义。下面显式定义 groupedOrders 和 groupedInvoices（均为预聚合结果）。
const customers = Query.from("OdooResPartnerModel");

const orders = Query.from("OdooSaleOrderModel");
const groupedOrders = orders
    .groupBy(orders.partnerId)
    .select(orders.partnerId, orders.amountTotal.sum().as("partnerTotalSales"));

const invoices = Query.from("OdooAccountInvoiceModel");
const groupedInvoices = invoices
    .groupBy(invoices.partnerId)
    .select(invoices.partnerId, invoices.amountTotal.sum().as("totalBilled"));

// 注意：customers / groupedOrders / groupedInvoices 是各自阶段的变量引用（类似 SQL 表别名），
// 即使经过 Join 产生了新的复合 Plan，原始变量仍可用于在 .on() 和 .select() 中定位字段来源。

// 第一步：Customers Join groupedOrders (已聚合)
const custOrder = customers.leftJoin(groupedOrders)
    .on(customers.id, groupedOrders.partnerId);

// 第二步：在上一步的基础上，再 Join groupedInvoices (已聚合)
const fullJoin = custOrder.innerJoin(groupedInvoices)
    .on(groupedOrders.partnerId, groupedInvoices.partnerId);

// 第三步：一次性 Select 所有需要的字段
const result = fullJoin.select(
    customers.name.as("customerName", "客户名称"),
    groupedOrders.partnerTotalSales.as("orderAmount", "订单金额"),
    groupedInvoices.totalBilled.as("invoiceAmount", "开票金额")
).execute();
```

> [!WARNING]
> **重名冲突拦截机制 (Fail-fast)**
> 如果在 `select` 时遇到重名（如 `customers.name` 和 `orders.name` 都试图作为 `name` 输出），引擎将在编译期抛出错误：`Column 'name' is ambiguous. Please use .as('new_name') to disambiguate.`。这保证了数据结构的确定性。

> [!CAUTION]
> **避免 BI 数据陷阱 (Anti-Patterns)**
> 1. **笛卡尔积膨胀陷阱（维表指标失真）**：如果不先对 `orders` 聚合就直接 Join 到 `customers` 上，那么 `customers.creditLimit.sum()` 将是完全错误的！因为每一个客户由于拥有 N 个订单，会在关联后被复制 N 遍，导致客户的授信额度被错误地放大了 N 倍。**再次重申：事实表必须先聚合，再与维表 Join！**
> 2. **订单头/订单行重复计数陷阱**：假设你需要同时统计"订单总金额"和"订单行数"。如果将 `SaleOrder`（头表）直接 Join `SaleOrderLine`（行表）后再执行 `saleOrder.amountTotal.sum()`，那么每笔订单的总金额会被其行数 M 重复累加 M 遍（总金额 = 真实总金额 × M）。正确做法：分别对头表和行表**独立聚合**后，再 Join 汇总结果。

## 5. 数据集纵向合并 (UNION)

当需要将同一数据源的多个相同结构的数据集合并时，使用 `.union()`。

**约束**：
- 两侧的输出 Schema（列的数量和类型）必须一致。
- 默认执行 `UNION`（去重），如果需要不去重合并，请使用 `{ all: true }`。
- **字段引用继承规则**：`union` 后产生的 `QueryPlan` 暴露的字段引用**以左侧 Schema 为准**（列名、别名均继承左侧）。右侧按位置对齐但不单独暴露引用。因此 `merged.salespersonId` 合法，因为 `salespersonId` 来自左侧 `q1` 的输出。

```javascript
const currentReceivable = Query.from("CurrentReceivableQM");
const archivedReceivable = Query.from("ArchivedReceivableQM");

const q1 = currentReceivable.select(currentReceivable.salespersonId, currentReceivable.amount);
const q2 = archivedReceivable.select(archivedReceivable.salespersonId, archivedReceivable.amount);

// 执行 UNION ALL
const merged = q1.union(q2, { all: true });

// 对合并后的结果进行再计算
return merged
    .groupBy(merged.salespersonId)
    .select(
        merged.salespersonId, 
        merged.amount.sum().as("totalAmount")
    )
    .execute();
```

> [!CAUTION]
> **避免 UNION 指标口径撕裂陷阱**
> 并非列名与类型一致就可以随意 UNION。必须确保两张表的业务粒度与**口径一致**！
> 例如，切勿将 `含税总额(amountTotal)` 与 `未税总额(amountUntaxed)` UNION 到同一个 `amount` 字段中，这会直接导致业务报表上的总计金额失去现实意义。合并前应使用 `.select(sales.amountUntaxed.as("amount"))` 强制对齐口径。

---

## 6. 底层权限隔离 (Layer C Sandbox)

无论是基础查询还是复杂的 JOIN / UNION 派生查询，**数据权限始终生效，且由系统底层自动托底**。

1. 当执行包含多个模型的脚本（例如 `ResPartner` JOIN `SaleOrder`）时，系统会自动提取操作用户对这两个模型的**所有细粒度权限**（列级权限 `fieldAccess`，行级权限 `systemSlice` 等）。
2. **Fail-closed 熔断**：只要当前用户对其中**任何一个模型没有访问权限**，整个脚本的执行将被拒绝并熔断，绝不会发生提权或跨域数据泄露。

分析师在使用 Compose Script 编写数据分析逻辑时，**无需也不能**在脚本内显式附加数据权限规则，只需专注于业务数据加工即可。

---

## 7. 本期能力边界

8.2.0.beta 聚焦于**关系节点编排**（派生查询、JOIN、UNION），以下能力**不在本期范围内**。如果你的分析需求涉及下列特性，请留意当前版本不支持：

| 不支持项 | 说明 |
|---|---|
| **`WHERE EXISTS` / 相关子查询** | 不支持 `semiJoin`, `antiJoin`, 标量子查询 |
| **递归 CTE** | 不支持 `WITH RECURSIVE`，树形遍历请使用 QM 层的 `$hierarchy$` 操作符 |
| **`INTERSECT` / `EXCEPT`** | 仅支持 `UNION` / `UNION ALL` |
| **`GROUPING SETS` / `ROLLUP` / `CUBE`** | 高级聚合结构不在本期 |
| **`DISTINCT` 关键字** | `.distinct()` 作用于当前阶段输出列，进入下一阶段后不保留额外标记（本期可用但未在示例中展示） |
| **内存二次加工** | 不支持 `DataSetResult.memoryQuery(...)` 或类似的内存再查询 |
| **跨数据源组合** | `UNION` / `JOIN` 两侧必须来自同一数据源 |

> [!TIP]
> 窗口函数与时间分析语义计划在 8.3.0 中纳入 `QueryPlan` 主语义层。`WHERE EXISTS` / 关系谓词也在后续路线图中。

---

## 8. 未来路线图预演 (Roadmap)

### 8.1 窗口函数支持 (Window Functions)
在未来的 8.3.0 版本中，借助于全对象化引用设计，系统将优雅地支持窗口函数。开发者可以直接在聚合列后链式调用 `.over()`：

```javascript
const sales = Query.from("OdooSaleOrderModel");

const result = sales
    .select(
        sales.partnerId,
        sales.amountTotal.sum()
             .over({
                 partitionBy: sales.partnerId,
                 orderBy: "-createDate"
             })
             .as("runningTotal", "累计消费额")
    )
    .execute();
```

### 8.2 时间偏移 / 同比环比
未来将引入时间维度的内置 `timeShift` 操作符，不再需要手工拼装复杂的日历 Join。

---

## 附录：API 快速参考

| 方法 | 参数类型 | 返回类型 | 说明 |
|---|---|---|---|
| `Query.from("ModelName")` | `string` | `QueryPlan` | 构造基础查询，指向物理 QM |
| `.where([...conditions])` | `Array<SliceObject>` | `QueryPlan` | 追加过滤条件（JSON 对象数组，**唯一使用字符串字段名的方法**） |
| `.groupBy(ref1, ref2, ...)` | `FieldRef...` | `QueryPlan` | 追加分组字段（推荐使用字段引用） |
| `.orderBy("alias", "-alias")` | `string...` | `QueryPlan` | 追加排序（`-` 前缀表示降序，引用**当前阶段**已存在的字段名/别名）。使用字符串而非 FieldRef，因为排序不涉及跨表消歧 |
| `.limit(n)` | `number` | `QueryPlan` | 限制结果行数 |
| `.offset(n)` | `number` | `QueryPlan` | 跳过结果行数（分页偏移量） |
| `.select(ref1, ref2, ...)` | `FieldRef / ProjectedColumn...` | `QueryPlan` | 投影并**创建新的关系阶段**（推荐使用字段引用 + `.as()`） |
| `.leftJoin(otherPlan)` | `QueryPlan` | `JoinBuilder` | 发起左连接 |
| `.innerJoin(otherPlan)` | `QueryPlan` | `JoinBuilder` | 发起内连接 |
| `.rightJoin(otherPlan)` | `QueryPlan` | `JoinBuilder` | 发起右连接 |
| `.fullJoin(otherPlan)` | `QueryPlan` | `JoinBuilder` | 发起全外连接 |
| `.on(leftRef, rightRef)` | `FieldRef, FieldRef` | `JoinPlan` | 指定 Join 条件（字段引用对象） |
| `.and(leftRef, rightRef)` | `FieldRef, FieldRef` | `JoinPlan` | 追加复合 Join 条件（字段引用对象） |
| `.union(otherPlan, {all?})` | `QueryPlan, options` | `QueryPlan` | 纵向合并数据集 |
| `.execute()` | — | `DataSetResult` | 触发 SQL 编译与执行，返回结果 |
| `.toSql()` | — | `SqlPreview` | 输出调试用 SQL（不触发执行） |
| `fieldRef.sum()` / `.count()` / `.avg()` / `.max()` / `.min()` | — | `AggregateColumn` | 对字段施加聚合函数 |
| `fieldRef.as("name", "caption?")` | `string, string?` | `ProjectedColumn` | 重命名字段并可选设置显示别名(Caption) |
