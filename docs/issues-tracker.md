# Issues Tracker

> 已知问题跟踪。完成后在 `[ ]` 改为 `[x]`。

---

## P0 — Bug（已标注 TODO bug）

### ~~001 ResultSetQuery 字符串搜索用 indexOf 替代 LIKE~~

- **文件**: `foggy-dataset/.../resultset/query/ResultSetQueryImpl.java`
- **修复**: 新增 `likeToRegex()` 方法，将 SQL LIKE 模式转为正则（`%`→`.*`、`_`→`.`、转义特殊字符），构造函数预编译 `Pattern.CASE_INSENSITIVE`
- **测试**: `SqlLikeExpTest.java` — 43 个测试用例（通配符、大小写、正则特殊字符、null、空值）
- [x] 已修复 — 2026-03-13

### ~~002 Boolean Number 转换精度问题~~

- **文件**: `foggy-core/.../trans/ObjectTransFormatter.java`
- **修复**: 按类型分派 — 整数用 `longValue()`、BigDecimal 用 `compareTo(ZERO)`、BigInteger 用 `signum()`、其他用 `doubleValue()`
- **测试**: `ObjectTransFormatterBooleanTest.java` — 30 个测试用例（8 种 Number 子类 + String/Boolean/null）
- [x] 已修复 — 2026-03-13

---

## P1 — PostgreSQL JSONB 支持

### ~~003 PostgresDialect 缺少 JSONB 文本提取方法~~

- **文件**: `foggy-dataset/.../dialect/PostgresDialect.java:42`
- **修复**: 采用模型级 `captionDef` 方案替代方言级 `wrapCaptionExpression()`。新增 `DbCaptionDef` 类支持 `column` + `formulaDef` + `dialectFormulaDef` 三级优先级，在 TM 维度定义中通过 `dialectFormulaDef.postgresql.builder` 指定 `->> 'en_US'` 提取逻辑
- **新增文件**: `DbCaptionDef.java`
- **修改文件**: `DbDimensionDef.java`（添加 `captionDef` 字段）、`DbDimensionSupport.java`（`DimensionCaptionDbColumn.getDeclare()` 支持公式）、`TableModelLoaderManagerImpl.java`（方言解析）
- **扩展**: `dialectFormulaDef` 同步加入 `DbPropertyDef` 和 `DbMeasureDef`，提取共享 `resolveDialectFormula()` 统一解析
- **测试**: `CaptionDefTest.java` — 13 个测试用例（维度 captionDef 7 + 属性 dialectFormulaDef 3 + 度量 dialectFormulaDef 3）
- **应用**: `OdooHrEmployeeModel.tm` 的 `job`（hr_job.name）和 `workLocation`（hr_work_location.name）维度已配置 PostgreSQL JSONB 提取
- [x] 已修复 — 2026-03-14

### ~~004 DimensionCaptionDbColumn 无 JSONB 类型感知~~

- **文件**: `foggy-dataset-model/.../dimension/DbDimensionSupport.java`
- **修复**: `DimensionCaptionDbColumn` 新增 `captionFormulaBuilder` 字段，覆写 `getDeclare(ApplicationContext, String)` 和 `getDeclare()`。当存在 formulaBuilder 时使用公式生成 SQL 片段（模式与 `DbPropertyImpl` 一致），否则回退原逻辑
- **依赖**: #003（同一方案统一解决）
- [x] 已修复 — 2026-03-14

---

## P1 — 预聚合引擎

### ~~005 PreAgg WHERE 条件转换不完整~~

- **文件**: `foggy-dataset-model/.../preagg/PreAggQueryRewriter.java`
- **修复**: 新增 `extractWhereClause()` + `extractListCond()` 递归遍历 `JdbcWhere` 条件树，按 `SimpleSqlJdbcQueryVisitor.acceptListCond()` 模式生成完整 SQL 片段和参数列表。支持 `ValueCond`、`ListValueCond`、`SqlFragmentCond`、`JdbcGroupCond`（嵌套），并通过 `FDialect.convertParameterValue()` 处理方言参数转换（如 SQLite Date→String）
- **同时修复**: `buildHybridParams()` 使用提取的 WHERE 参数替代错误的 `queryEngine.getValues()`，解决 SQL 占位符与参数数量不匹配问题
- **测试**: `PreAggregationIntegrationTest.java` — `testHybridQueryShouldIncludeOriginalWhereInSourcePart`（验证 WHERE 条件传递）+ `testHybridQueryWhereParamsOrderCorrect`（验证参数数量匹配）
- **依赖**: 与 #006 一起修复（同一代码路径）
- [x] 已修复 — 2026-03-14

### ~~006 PreAgg SQL 缺少维表 JOIN~~

- **文件**: `foggy-dataset-model/.../preagg/PreAggQueryRewriter.java`
- **修复**: 重写混合查询源表部分 SQL 生成。新增 `buildSourceFromClause()` 从 `JdbcQuery.JdbcFrom` 提取原始 FROM + 所有 JOIN（含 ON 条件），新增 `buildSourceSelectColumnsWithJoins()` 使用原始表别名（`t1`/`d1`/`d2`）引用列。替代原来错误使用 `src` 别名 + 无 JOIN 的单表查询
- **同时修复**: `parseWatermarkColumn()` 和新增 `resolveWatermarkSourceColumn()` — 通过 `queryModel.findDimension().getForeignKey()` 解析物理列名（如 `salesDate$id` → `date_key`），替代原来返回维度名的错误行为
- **测试**: `PreAggregationIntegrationTest.java` — 3 个混合查询测试全部通过（含原有 `testHybridQuerySqlGeneration`）
- **依赖**: 与 #005 一起修复（同一代码路径）
- [x] 已修复 — 2026-03-14

---

## P1 — 方言适配

### ~~007 SqlExpContext 方言函数转换未实现~~

- **文件**: `foggy-dataset-model/.../expression/SqlExpContext.java`
- **修复**: `translateFunction()` 委托给 `FDialect.translateFunction()`。4 个方言已实现函数映射：MySQL（`NVL`→`IFNULL`）、PostgreSQL（`IFNULL`→`COALESCE`、`ISNULL`→`COALESCE`）、SQL Server（`IFNULL`→`ISNULL`、`POW`→`POWER` 等 6 个映射）、SQLite（`NVL`→`IFNULL`、`ISNULL`→`IFNULL`）
- **测试**: `SqlExpContextDialectTest.java` — 方言函数转换验证
- [x] 已修复 — 2026-03-14

---

## P2 — GraphQL 模块

### 008 GraphQL Relay Connection 格式不完整

- **文件**: `addons/foggy-dataset-graphql/.../GraphqlEndpointController.java:87`
- **现状**: `// TODO: 完整实现 Relay Connection 格式`
- **影响**: 不完全支持 Relay 规范的游标分页
- [ ] 已修复

### 009 GraphQL 游标分页解码未实现

- **文件**: `addons/foggy-dataset-graphql/.../PaginationConverter.java:104-161`
- **现状**: `after`/`before` 游标解码未实现，缺少 CursorCodec
- **影响**: 客户端无法使用游标分页
- **依赖**: #008
- [ ] 已修复

---

## P2 — 微服务 / 远程访问

### 010 RemoteDatasetAccessor 三个方法未实现

- **文件**: `foggy-dataset-mcp/.../spi/impl/RemoteDatasetAccessor.java:32,39,47`
- **现状**: `loadTableModel()`, `loadQueryObject()`, `getTableColumnCount()` 均为空壳
- **影响**: 不支持远程数据集访问，只能使用 LocalDatasetAccessor
- [ ] 已修复

---

## P2 — Bean 工具

### 011 Map2BeanUtils 泛型类型参数解析异常

- **文件**: `foggy-bean-copy/.../utils/Map2BeanUtils.java:176-177`
- **现状**: `List<Double>` 等参数化类型的 `innerClsName` 包含尖括号，`Class.forName()` 失败
- **影响**: Map 转 Bean 遇到泛型集合属性时抛 `ClassNotFoundException`
- [ ] 已修复

---

## P2 — Odoo Bridge 边界情况

### 012 permission_bridge 多级字段遍历静默截断

- **文件**: `addons/foggy-odoo-bridge/.../services/permission_bridge.py:605-611`
- **现状**: `user_id.company_id.name` 静默截断为 `user_id`，仅 debug 日志
- **影响**: 多级字段的 ir.rule 域条件被错误简化，可能导致权限过宽
- **修复方向**: 返回 `None`（跳过该条件）或抛出明确错误
- [ ] 已修复

### 013 permission_bridge 不可否定操作符未处理

- **文件**: `addons/foggy-odoo-bridge/.../services/permission_bridge.py:636-641`
- **现状**: `like` 等不在 `NEGATE_OP_MAP` 中的操作符在 NOT 分支保留原值
- **影响**: `NOT ['field', 'like', 'val']` 被错误翻译为 `like` 而非 `not like`
- [ ] 已修复

---

## P3 — 模型加载 / MongoDB

### 014 维度自动加载未实现

- **文件**: `foggy-dataset-model/.../loader/TableModelLoaderManagerImpl.java:283,346`
- **现状**: `//TODO 自动加载维度` — 维度必须手工在 TM 中声明
- **影响**: 不影响功能，但增加模型配置工作量
- [ ] 已修复

### 015 MongoDB TM Loader 不支持 schema 切换

- **文件**: `addons/foggy-dataset-model-mongo/.../TmMongoModelLoaderImpl.java:113`
- **现状**: 自定义 def 时无法切换 schema
- **影响**: 多租户 MongoDB 场景受限
- [ ] 已修复

### 016 DimensionMemberLoader 数据加载逻辑未实现

- **文件**: `foggy-dataset-model/.../semantic/service/impl/DimensionMemberLoaderImpl.java:33`
- **现状**: 类级注释 `TODO: 实现具体的数据加载逻辑`
- **影响**: 维度成员查询功能不完整
- [ ] 已修复

---

## P3 — 项目清理

### ~~017 根目录临时测试文件 + .gitignore~~

- **文件**: 项目根目录
- **修复**: 删除 5 个临时文件 + `.gitignore` 加入 `test-*.json` / `test-*.sh` / `test-*.bat`
- [x] 已修复 — 2026-03-13

### 018 FactSalesModel.tm formulaDef 示例为 MySQL 专属

- **文件**: `foggy-dataset-demo/.../ecommerce/model/FactSalesModel.tm:219-222`
- **现状**: `builder: (alias) => { return \`${alias}.tax_amount+1\`; }` — 此例碰巧跨方言兼容（纯算术），但缺少注释说明 formulaDef 的方言注意事项
- **影响**: 低，当前示例可跨方言。仅文档规范问题
- **备注**: `dialectFormulaDef`（#003）已提供方言安全方案。方言相关公式应使用 `dialectFormulaDef` 而非单一 `formulaDef`
- [ ] 已修复

---

## 已完成（历史记录）

### ~~019 文档 formulaDef 示例 MySQL 专属 JSON 语法~~

- **文件**: `docs-site/zh/en/.../tm-syntax.md`, `downloads/tm-syntax.md`, `.claude/skills/.../calculated-fields.md`
- **修复**: 替换为跨方言安全示例 + 添加方言对照表 warning
- [x] 已修复 — 2026-03-13

### ~~020 SqlColumnRefExp.java 注释 MySQL 专属 SQL~~

- **文件**: `foggy-dataset-model/.../expression/sql/SqlColumnRefExp.java:57`
- **修复**: 注释改为标注方言相关性
- [x] 已修复 — 2026-03-13

### ~~021 Odoo hr_department JSONB captionColumn~~

- **修复**: `captionColumn` 从 `name`（JSONB）改为 `complete_name`（TEXT）
- [x] 已修复 — 前次会话

### ~~022 E2E 测试 slice 格式 + $name → $caption~~

- **修复**: 数组格式 + `$caption` 字段引用
- [x] 已修复 — 前次会话
