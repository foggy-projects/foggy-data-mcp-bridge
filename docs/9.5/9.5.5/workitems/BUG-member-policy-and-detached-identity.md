# 成员权限、Viewer 绑定与 detached 资源身份

状态：LOCALLY VERIFIED。基线：main `0a9cd273`；对应 Launcher 0.1.30 的定向审计。

## 范围和合同

修复八类问题：成员无效可见列放行、嵌套/后置 patch 层级权限绕过、Viewer 路径和改名字段映射、
Viewer 身份丢失、父子维 SQL 绑定、detached TM 冲突漏检、同名 Bundle 隐式覆盖、子目录重复发现。
同时移除 Viewer 脱离 catalog generation 的层级缓存，补充刷新验证。

子维度成员权限采用请求依赖合同：父级 synthetic 仅使用自身字段时不新增子表过滤；选择、过滤、
排序、分组或表达式依赖子维度字段时，必须叠加该子维度的成员限制。引用更深后代同时受沿途
子维度限制。同一维度的 QM/TM 覆盖规则保持原样；不同维度之间的限制取交集。
子维度静态谓词映射为 synthetic 相对路径并与其他约束 AND；无法安全重定位的自定义 SQL
queryBuilder 拒绝该父级路径，可直接从该子维度入口查询。父维度约束不自动向独立子表反向传播。

## 实施方案及影响面

1. 成员白名单声明必须可绑定；错误字段、声明为空或有效集合为空时 fail closed。
   在 external patch 完成后递归校验 slice/having 的层级操作，避免只检查顶层。
   子树约束使用统一依赖解析，保持普通 QM fieldAccess、deniedColumns 与模型权限流程。
2. Engine 提供成员字段绑定描述，Viewer 使用当前源模型的查询名、完整维度路径与层级能力；
   主查询、回填、描述使用同一授权。取消无 generation/身份的 Viewer 元数据缓存。
   继续经 QueryFacade 执行，旧 Java 调用入口保留匿名兼容重载。
3. 追踪父子维普通/成员查询的物理列和 JOIN 渲染，覆盖五类层级操作，避免扩大到无关 SQL 路径。
4. detached 显式区分普通校验和指定 Bundle 替换；通用 validate 不凭名称遮蔽已有 Bundle。
   TM/QM 资源按 namespace + canonical name 校验冲突，同一个真实资源只发现一次；
   子目录覆盖只屏蔽重复资源，保留父目录其余依赖。authoring 指定替换语义保留。

兼容性：name 仍是查询标识，alias 仍是输出键；不扩展输出 alias 为输入别名。
原有效成员查询和普通目录重验不变；依赖错误字段、权限绕过、隐式同名替换的请求将被拒绝。
Viewer 对改名字段返回真实查询名，修正以前不可执行的 selection/display 字段。
公共 Model SPI v2、RuntimeEnvelope 和 RX 保持不变，不自动登记、刷新或发布 live 模型。

## 影响与兼容边界明细

| 类别 | 修改边界 | 行为和兼容性 |
| --- | --- | --- |
| 无效 visibleColumns | 内部/外部成员 patch、权限 DTO | null 保持不限；显式空列表或任一未知字段拒绝，避免错误声明扩大可见范围 |
| 层级限制绕过 | 最终成员权限步骤（external 后、请求规划前） | 递归检查 AND/OR、slice/having/postSlice；子树依赖激活子策略；外部 patch 单独校验字段依赖 |
| Viewer 字段绑定 | Engine MemberBindingService + Viewer adapter | 完整 owner 路径、实际 QM name；修正嵌套与改名字段，alias 仍仅输出 |
| Viewer 身份丢失 | members 与 legacy filter-options adapter | authorization/namespace 传到主查询和 selected 回填；旧匿名服务重载保留；签名链接作用域不变 |
| 父子维 SQL | TableModel JOIN graph、synthetic graph、层级条件 JOIN | 普通查询按外键真实 owner，成员表用主键接闭包；保持操作符与结果合同 |
| detached TM 漏检 | 请求局部资源发现 | TM/QM 同名不同物理资源都拒绝，不把相同文件的重验当冲突 |
| 同名 Bundle 隐式覆盖 | Detached factory、authoring/candidate 调用方 | 普通 open 不覆盖；显式 openReplacement 保留已选 Bundle 的草稿校验/查询 |
| 子目录重复发现 | detached 依赖 Bundle 视图 | 按真实资源去重，保留父目录中未覆盖的 TM/脚本，namespace 隔离不变 |

安全复查同时补齐普通字段权限条件中的 `$expr` / `$field` 依赖检查；影响受字段白名单或物理拒绝约束的查询，未配置权限时保持原行为。可信系统行谓词仍在用户字段检查之后合并，不受外部 patch 的新增限制。

额外验证 Viewer 去缓存：相同服务实例在真实 source/catalog 更新后返回新的 name 与层级权限；
失败的 detached candidate 不发布；主查询与回填使用当前调用者身份。未修改公共 Model SPI v2、
RuntimeEnvelope/RX 的形状，未修改数据库表结构、CLI 协议或前端组件接口。

迁移注意：曾将空白名单当作“未配置”的模型应改为省略/null；含未知字段的权限声明须修正；
通过父级访问带自定义 SQL queryBuilder 的子维度，应改用独立子维度成员入口。
外部 patch 不再具有隐式读取隐藏字段的能力；确有需要的可信租户/行过滤应放入模型行权限或系统 slice。
`openReplacement` 是内部端口的新显式能力；第三方内部 factory 实现必须明确支持它，默认拒绝，
避免无意恢复按名称覆盖的旧行为。

## 验证顺序

先补成员权限/依赖、Viewer 身份及刷新、层级 SQL、detached 冲突与隔离回归；专项通过后执行
Windows/Linux 全量，以及 MySQL 5.7/8、PostgreSQL、SQL Server 门禁和外部服务 IT。
实际 Launcher 制品复跑审计中的请求及 Viewer 场景。根 target 保留证据，不运行根 clean。
本轮只实施与测试；不包含提交推送、issue 变更或发行。

## 结果

证据目录：`target/member-policy-fix/`。专项回归 `regression-9`：289 项，0 失败、0 错误，
2 项 Windows 平台条件跳过。Windows 全量 `full-windows-verified`：5,983 项，0 失败、0 错误，
11 项平台条件跳过。Linux 全量 `full-linux-verified`：5,983 项，0 失败、0 错误、0 跳过；
已按 classname + testcase name 逐项核对上述 11 项全部通过。

实际 Launcher JAR 重放已通过：既有 `artifact-smoke` 16 组，加上 `member-policy-artifact` 11 组。
后者包含隐藏字段条件/外部 patch 拒绝、五类层级操作、完整路径与改名字段回传、
主查询和 selected 身份一致性、真实 Bundle replacement 后 Viewer 名称和层级能力刷新，
以及 TM 冲突、同名 Bundle、子目录重复校验。两个进程均已停止。
制品 SHA-256：`9ce128f57f7d5976eadc62159768c07c899d78530c6257bca12d98f76ea1d1e4`。

补充检查：门禁脚本 6 项、Linux 发布脚本 38 项、CLI 完整测试 148 项通过；Analytics Console 前端 12 项和
Runtime Console 前端 38 项通过，两者类型检查、生产构建均通过。
Linux Maven 全量复用此次构建的静态资源，跳过重复 npm 构建。

保留失败尝试及原因：旧 authoring 测试按隐式名称覆盖的假设已迁移至显式 replacement；
Viewer 仅配置 QueryFacade 的启动兼容性已恢复，缺少绑定能力时仅成员请求明确失败；
隔离 Linux 源码时间戳和遗漏前端静态资源的问题已修正，重新执行完整门禁。
Windows 发布脚本测试中的 Bash/编码差异由 Linux 同套 38 项成功结果覆盖。

| 专项数据库门禁 | 用例数 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| SQLite | 388 | 0 / 0 / 0 |
| MySQL 5.7 | 197 | 0 / 0 / 0 |
| MySQL 8 | 227 | 0 / 0 / 0 |
| PostgreSQL | 227 | 0 / 0 / 0 |
| SQL Server | 197 | 0 / 0 / 0 |

每条数据库门禁均执行新增 `MemberPolicyRegressionTest` 的 26 项，且 requiredClasses 无缺失；
MySQL 8/PostgreSQL 另含 Pivot SQL parity。

| 服务集成门禁 | 用例数 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| 通用集成（数据库、生命周期、权限、缓存、预聚合等） | 410 | 0 / 0 / 0 |
| Redis 跨 JVM / 缓存生命周期 | 3 | 0 / 0 / 0 |
| Mongo | 31 | 0 / 0 / 0 |
| MCP | 23 | 0 / 0 / 0 |
| Vector | 20 | 0 / 0 / 0 |
| MySQL 5.7 预聚合 | 3 | 0 / 0 / 0 |

最终 `final-summary.json` 校验通过：仓库 47 个 IT 类均有执行记录；5,326 个 Linux 快照文件
已核对，当前生产代码/测试/脚本与所测快照一致（仅文档在测试后补结果；shell 文件按 LF 比较）。
两个 Launcher 重放使用相同 JAR 哈希。上述测试阶段有重叠，不将它们相加宣称独立用例数。
完整命令、日志、报告和失败尝试均保留在证据目录；本轮隔离容器及网络按所有权标签清理，
清理结果见 `cleanup.json`，不清理测试证据。

### Query 应用启动注册复查

`MemberBindingService` 的实现由 `QueryModelLoader` 构造注入；除了类上的 `@Component`，
必须由 `DbModelAutoConfiguration.@Import` 显式注册，才能支持不扫描 Foggy 包的业务应用。
当前 main 工作区已包含该显式注册，Engine 的 Boot 3 `AutoConfiguration.imports` 也已登记
`DbModelAutoConfiguration`；两种 Viewer 自动配置均声明在 Engine 自动配置之后加载。
保留 QueryFacade-only 模式的可选绑定兼容性，但不能以“上下文启动成功”替代完整 Engine 的 Bean 检查。

新增 `io.foggytest.query.QueryApplicationContextTest`：使用 Foggy 包之外的独立 Query 应用，
仅通过 `@EnableAutoConfiguration` 发现配置，不扫描、手工导入或 mock Engine/Viewer Bean。
分别覆盖默认 SQLite 与 memory Viewer，断言 loader、binding、facade、member service 和 controller
唯一注册，并以真实 SQLite TM/QM 执行改名字段的成员查询，确认绑定能力实际可用。

专项 `query-startup-regression-2`：新增启动用例 2 项，连同 Engine 自动配置、Viewer 自动配置及
成员服务回归共 24 项，0 失败、0 错误、0 跳过。首轮测试夹具使用未绑定数据源的命名空间，
已改为测试自身的默认 SQLite 数据源；两个上下文在首轮也均成功完成 Bean 注册。
本次只新增测试及验证记录，生产注册修复已包含在前述 main 工作区改动中。

### 消费方候选包依赖取证

消费方补充实际入口 `com.shundao2014.query.QueryCloudServiceApplication` 后，检查其依赖和失败
候选归档 `chengda-test-cd-20261021-customer-copy.tar.gz`（来源提交 `cefe567677e9ab5d48cf9036200abeb0f5f8a585`）。
Query 启动类仅排除 Runtime HTTP 自动配置；Engine/Viewer 由 Boot imports 装配。消费方 POM
引用 `9.3.0-SNAPSHOT`，构建脚本使用 Maven 构建并通过 `dependency:copy-dependencies` 收集运行依赖。

归档中的实际字节确认：

| 依赖 | SHA-256 | 注册事实 |
| --- | --- | --- |
| foggy-dataset-model-engine | `10c290cb7e7ebe739df3c20eee1542c910d596bef585c4426c753cfd1f7a1af4` | 有 MemberBindingService.class，DbModelAutoConfiguration 未导入它 |
| foggy-data-viewer | `258d02305acf848c488418e7b437874b5717dd5fe79ebcd925029691510a7681` | MemberQueryService 的 @Autowired 仍为默认 required=true |

两者版本均为 `9.3.0-SNAPSHOT`，与本机 Maven 仓库字节完全相同；本地元数据为
`localCopy=true`、`lastUpdated=20261021044207`（UTC）。因此该失败候选消费了缺少显式注册的
本地中间构建，不能用源码 main 已修复或 Maven 版本字符串相同推定候选已包含修复。

使用同一个 `QueryApplicationContextTest`，仅替换 Engine/Viewer 构件作隔离对照：候选构件
两项均复现 `No qualifying bean ... MemberBindingService`；换为已验证的 main 构件后两项均启动
并完成成员查询，0 失败、0 跳过。该重放验证 Engine/Viewer 自动装配边界，不宣称启动了带外部
数据库、Nacos 和身份系统的完整消费方应用。证据：`target/query-candidate-audit/` 中的依赖哈希、
字节码、classpath、`candidate-context.log`、`fixed-context.log` 与 `replay-summary.json`。

后续交付应发布/交付同一轮已验证依赖，再重新构建候选并核对实际 JAR 哈希与启动门禁；
不在 Query 应用补扫描或手工 Bean 来掩盖上游漏注册。本次未改写共享 Maven 仓库、旧候选归档，
未部署或启停客户环境。用户给出的默认引擎目录当时在旧开发分支，本轮 main 修复位于
`releases/engine-9.3.0` 工作区；两处源码位置必须在构建前核实，不能混用。
