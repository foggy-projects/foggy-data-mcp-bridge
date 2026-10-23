# Compose 投影与日期差函数合同（#156 / #157）

## Compose 输入输出

Base、Derived、JOIN 和 UNION 的编译结果必须携带实际投影的有序输出列名。
外层 JOIN 使用输入计划的输出名绑定连接键和投影，不能重新扩展为底层模型字段。
Derived 显式 columns 使用其输出别名；省略 columns 时继承输入输出。
UNION 按位置合并，列名来自左分支；UNION ALL 保留重复行。
JOIN 保持现有左侧同名列优先规则，G10 的逻辑歧义与来源校验独立保留。
SQL Server 派生查询的 GROUP BY 在输入没有该字段时，将同层投影别名还原为其表达式；
若输入已有同名列则保留输入优先，其他方言的绑定规则不变。

物理输出名通过 ComposedSql 与 CteUnit 传递。它不替代 OutputSchema 的类型、
lineage 和权限语义，也不加入 Runtime 的 JSON 响应。原 ComposedSql 两参数构造器继续可用。

G10 的权限上下文登记整个实际计划树。复合计划的输出由生产该输出的依赖授权；
所有中间生产计划都必须校验，而不是只校验最终投影。
底层模型 fieldAccess、物理列权限与行权限仍通过现有语义查询链执行。
复合别名不能直接套用模型字段白名单，也不能通过别名获得未投影或未授权的字段。

## 日期差函数

| 函数 | 参数与结果合同 |
| --- | --- |
| `DATEDIFF(end, start)` | 忽略时分秒，返回 end 日期减 start 日期的日历日差；跨午夜一小时可返回 1。 |
| `TIMESTAMPDIFF(unit, start, end)` | 返回完整经过单位数，负数向零截断。SECOND/MINUTE/HOUR/DAY/WEEK 是固定时长，DAY 为 24 小时。 |
| `HOURS_BETWEEN(start, end)` | 保留既有小数小时行为。 |
| `DATE_DIFF(end, start)` | 保留历史方言行为：SQLite 为经过天数向零截断，MySQL/PostgreSQL/SQL Server 为日历日差；本轮不改变其语义。 |

TIMESTAMPDIFF 支持 SECOND、MINUTE、HOUR、DAY、WEEK、MONTH、QUARTER、YEAR。
MONTH 为完整日历月：末尾日号与时刻未达到起点则不算完整月；Jan31 到 Feb29 为 0。
QUARTER/YEAR 分别为完整月数除以 3/12，向零截断。反向区间符号相反。
MICROSECOND 支持 MySQL、PostgreSQL 和 SQL Server；SQLite 日期函数只有毫秒精度，明确拒绝该单位。
`'HOUR'` 与裸 `HOUR` 等价，大小写不敏感。单位必须是常量，不作为模型字段或权限依赖。
两个日期表达式仍保留完整字段绑定、依赖和权限校验；NULL 日期返回 NULL。
日期值沿用数据源现有时区/类型解释，本轮不增加隐式时区转换。

日期差转换由 DateDifferenceSql 统一实现，普通 SQL 计算与 totalData 重建共用，
避免重新聚合时退回数据库不支持的原始函数。SQLite 整单位计算先舍入到毫秒精度，
再截取完整单位，防止 julianday 的浮点误差把恰好两小时变成一小时。

参数数量、非法单位、方言不支持或单位精度不支持，在 SQL 构建阶段拒绝。
计算字段返回 DATE_FUNCTION_ARITY_INVALID、DATE_FUNCTION_UNIT_INVALID、
DATE_FUNCTION_DIALECT_UNSUPPORTED 或 DATE_FUNCTION_UNIT_UNSUPPORTED，
附原始 calculatedFields 索引的 expression 路径和修改建议。

## 影响与兼容性

- Compose 的影响面是组合计划在左右侧嵌套、Derived 传递投影、UNION 输出、连接键绑定和 G10 权限来源。
  不调整分页、去重、行权限或 UNION ALL 的行数语义。
- 日期差影响计算字段、内联表达式及其汇总重建。已有合法 MySQL 调用继续保持其单位和截断语义。
  过去白名单接受但数据库不能执行的调用，改为方言转换或提前拒绝。
- 保留模型 dialectFormulaDef 与 DATE_DIFF/HOURS_BETWEEN 的既有行为。
  DATEDIFF 的公开合同为两参数；SQL Server 原生三参数形式不是该跨库表达式合同。
- Runtime validate、preview、execute 应共享编译诊断；验证成功后仍需真实执行检查投影及数值，不能以 SQL 文本断言替代。

## 验证

ComposeBindingPaginationRegressionTest 覆盖嵌套 JOIN/UNION 的右侧输出、`$` 别名、
省略投影、多层 Derived、LEFT JOIN 空值行、UNION ALL 重复行、G10 开关、非法连接键和权限拒绝。
DateDifferenceRegressionIT 在真实数据库执行日期/时间差，覆盖跨午夜、正负区间、毫秒边界、
月末、闰年、NULL、别名、裸单位、字段权限、错误路径与旧函数对照。
DateDifferenceContractTest 覆盖汇总表达式重新绑定及不支持的方言/单位。
完整测试日志、XML、原 issue Runtime 回归和源文件散列保存在仓库外发布证据目录。

## 后续审计修复：表达式边界与引用实例

本轮沿用现有查询计划、语义权限链和数据库执行端口，不迁移全引擎编译架构。

- 派生字符串表达式使用仓库已有的 SQL AST 解析器。允许的标量节点递归生成 SQL，
  字段依赖取自同一棵树；不支持的节点直接报错，不回退为原始 SQL。
  标量子查询、物理表限定引用、额外语句及未允许函数不能通过别名伪装获得权限。
  合法子查询仍通过已有 QueryPlan/subquery API 进入授权计划编译。
- 字符串常量、函数名和 TIMESTAMPDIFF 单位不计入字段依赖。普通查询与派生查询的
  DATEDIFF/TIMESTAMPDIFF 共用 DateDifferenceSql，不扩大其他函数的方言支持承诺。
- 计划编译缓存与 JOIN 输入实例分离。同一计划可只做一次底层语义编译，但必须生成
  两个连接引用；同一个句柄无法表达要读取哪一个实例时，限定字段引用明确报歧义。
- JOIN 仍保持裸输出同名列左侧优先。显式右侧句柄引用按需携带内部投影，限定字段及其
  聚合读取同一生产者，最终显式输出不泄漏内部列。嵌套包装后的引用使用当前 SQL 作用域。
- `name` 是底层模型授权和查询绑定的名称，`alias` 是已编译关系的输出符号。
  底层字段、物理列和行权限通过后，外层访问其输出别名不再误套模型字段白名单。
  G10 默认值不变，两种开关状态都必须阻止原始 SQL 绕过。
- GROUP BY 可以引用输入字段或合法同层输出别名；过滤条件两侧的字段都必须存在。
  重复输出名提前拒绝。显式派生 `*` 提前拒绝，省略 columns 继续继承输入投影。
- CompiledRelation 从本次编译绑定的 schema 获取输出名，保留时间窗口语义元数据；
  UNION 仍按位置对齐、沿用左分支输出名，UNION ALL 继续保留重复行。

SQL Server 固定时长差在微秒边界计数上补偿亚微秒部分，再向零截断；不对整个区间
使用纳秒计数，避免缩小可计算日期跨度。月份差比较前把 date/datetime 输入归一到
datetime2(7)，避免字符串长度造成整月误判。
PostgreSQL 固定时长差保留输入类型：timestamptz 按时间点计算，timestamp 按无时区值
计算；未指定类型的日期字符串沿用 timestamp 解释，不改变会话时区。
数字日期参数的隐式转换政策不在本轮收紧。

兼容性影响集中在之前能绕过权限、引用不存在字段、重复输出名或依赖不支持原始 SQL
的请求：它们会更早失败。合法别名、位置 UNION、普通算术、CASE 和既有窗口表达式
需通过回归验证。证据目录为仓库外 `releases/compose-date-fix-20261023`，保留各次失败
与最终通过日志，避免构建 clean 删除证据。
