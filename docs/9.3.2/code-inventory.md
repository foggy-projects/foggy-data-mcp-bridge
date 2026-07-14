# 9.3.2 Code Inventory

## 文档作用

- doc_type: code-inventory
- intended_for: release owner / execution agent / reviewer
- purpose: 记录受保护基线、实际代码触点、扫描前后 Bean 清单和最终注册状态。

## 受保护的既有变更

- 初始 git status 显示的 cache fingerprint/provider、model 执行链、Controller 隔离和 docs/9.3.1 变更属于 9.3.1。
- 本轮未执行 reset、checkout、clean，也未回退这些变更。
- 9.3.2 在重叠文件上只做自动配置、诊断和验证所需的增量改动。

## 风险线索与实施结果

| 触点 | 修复前行为 | 9.3.2 结果 |
|---|---|---|
| McpLauncherApplication | SpringBootApplication 扫描 com.foggyframework 根包 | 恢复默认自身包扫描，Addon 仅靠 imports |
| DbModelAutoConfiguration | model 包宽扫描可发现扩展 Loader | 改为显式 Import 清单 |
| DatasetMcpAutoConfiguration | MCP 包宽扫描 | 改为显式 MCP 内部 Import 清单 |
| model-mongo factories | 错误注册 dataset Mongo 配置 | 注册 MongoModelAutoConfiguration |
| Mongo/model Mongo Loader | 可被 stereotype 或宽扫描无条件发现 | 仅由条件化自动配置创建并 back-off |
| Vector modules | 缺 Boot 3 imports | dataset/model Vector 均有标准入口和顺序 |
| Cache module | 只有旧 factories，web/eviction 可绕过 provider | 拆为 core/web/eviction 三个标准入口并共享 provider 条件 |
| 其他基础/Addon 模块 | factories 与 imports 混合 | 移除 EnableAutoConfiguration factories 条目 |

## 实际代码清单

| 路径或模块 | 角色 | 实施结果 |
|---|---|---|
| foggy-mcp-launcher | Launcher 边界和发布物 smoke | 移除根扫描；新增包外、注册唯一性、全 Addon 和路由 smoke |
| foggy-dataset-model | model 核心自动配置 | 显式内部 Import；保留 9.3.1 Bean；新增 fallback 风险诊断 |
| foggy-dataset-mcp | MCP 自动配置 | 显式内部 Import；保持 dev/test Controller 条件 |
| addons/foggy-dataset-mongo | dataset Mongo Addon | 条件化 Loader、back-off、Boot 3 imports、切片测试 |
| addons/foggy-dataset-model-mongo | model Mongo Addon | 正确入口、移除无条件 Service、切片测试 |
| addons/foggy-dataset-vector | dataset Vector Addon | Boot 3 imports、开关、依赖/Bean 条件和 back-off |
| addons/foggy-dataset-model-vector | model Vector Addon | ordered auto-configuration、Milvus/WebFlux 缺类安全 |
| addons/foggy-dataset-model-cache | query cache Addon | provider/builder/web/eviction 条件一致，Redis/Caffeine 分支隔离 |
| GraphQL、Cloud、DataViewer、PreAgg、Odoo、MemoryGrid、Demo、Dataset | 其他标准入口 | AutoConfiguration 注解、imports、顺序或条件补齐 |
| 各模块 META-INF/spring.factories | 旧注册 | EnableAutoConfiguration 条目清零；合法 initializer 保留 |
| .gitignore | coverage 文档可见性 | 精确放行 docs/9.3.1/coverage 和 docs/9.3.2/coverage 下的 Markdown |
| docs/9.3.2 | 版本治理 | requirement、plan、progress、test、quality、coverage、acceptance 完整 |

## 扫描移除前关键 Bean 基线

- model core：TableModelLoaderManager、QueryModelLoaderImpl、QueryFacade、SemanticQueryServiceV3、query/result step executor、生产 Controller。
- MCP core：McpToolDispatcher、McpService、DatasetAccessor、MCP tools、生产 Controller、datasource manager/resolver。
- Addon 负向边界：core-only 不应出现 TmMongoModelLoaderImpl、TmVectorModelLoaderImpl、Mongo/Vector fsscript loader 或 cache provider/controller。
- 9.3.1 隔离边界：DevToolsController、SemanticServiceV3TestController 默认不存在。

## 扫描移除后关键 Bean 清单

- model 和 MCP 核心 Bean 由 owning AutoConfiguration 显式导入或声明，Launcher 和包外应用验证正常。
- core-only 上下文不会因扫描出现 Mongo、Vector 或 Cache Addon Bean。
- Mongo、Vector、Cache、GraphQL、PreAgg 联合上下文中各关键 Bean 恰好一个，无循环依赖或重复定义。
- Launcher 默认不存在 DevToolsController、SemanticServiceV3TestController、SavedQueryTestController 和 DemoSecurityIdentityResolver，相关 route 为 404。

## 最终注册清单

- 本轮治理的 17 个自动配置入口在 AutoConfiguration.imports 中各出现一次。
- 源码和最终 main JAR 均不存在 EnableAutoConfiguration factories 注册。
- foggy-dataset-client 与 foggy-fsscript-client 的 ApplicationContextInitializer factories 为合法非自动配置用途。
- Launcher 内嵌的 12 个本地模块 JAR 与根 package 产物 checksum 全部一致。

## 范围边界

- docs/9.3.3、docs/9.3.4、docs/9.3.5 及 9.4.0 物理拆分未被提前实施。
