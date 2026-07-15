# foggy-dataset-model 单元测试分析报告

> 生成日期：2026-03-23 | 更新日期：2026-03-23
> 模块：`foggy-dataset-model`
> 测试文件总数：61（含支持类，新增 6 个测试类）
> 生产代码文件数：242

---

## 一、总体评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **覆盖广度** | ⭐⭐⭐⭐ | 核心引擎、方言、权限、插件、语义层均有测试 |
| **覆盖深度** | ⭐⭐⭐ | 核心路径深度好，但边界/异常覆盖不足 |
| **测试质量** | ⭐⭐⭐⭐ | 断言明确，数据对比测试设计优秀 |
| **可维护性** | ⭐⭐⭐⭐ | 继承体系清晰（DemoTestSupport → EcommerceTestSupport） |
| **回归防护** | ⭐⭐⭐⭐ | 有专门的 Bug 回归测试类 |

### 关键发现

**优势：**
1. DataComparison 模式（模型查询 vs 原生 SQL 对比）是非常好的测试策略
2. 多方言（MySQL/PG/SQLite/SQL Server）交叉验证覆盖全面
3. CalculatedFieldAggregationBugTest 展示了良好的回归测试实践
4. 电商模型测试场景丰富，接近真实业务

**主要缺口：**
1. Controller 层零测试
2. 插件管线（Pipeline）整体流程测试不足
3. 异常路径/边界条件覆盖偏弱
4. 并发安全测试缺失
5. 配置类（AutoConfiguration）无测试

---

## 二、逐类详细分析

### 2.1 电商核心测试（ecommerce/）— 17 文件

#### ✅ BasicQueryTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~15 |
| 覆盖功能 | 简单字段查询、维度 JOIN、条件过滤（eq/range/IN/组合）、排序、聚合 SQL 生成 |
| **合理性** | ✅ 优秀 — 作为基础查询入口测试，覆盖了最常用的查询模式 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 空结果集处理、超长 IN 列表、特殊字符条件值、NULL 条件处理 |
| 优先级 | P2 |

#### ✅ AggregationQueryTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~20 |
| 覆盖功能 | 单/多维分组、条件聚合、同名字段条件过滤、无 GROUP BY 聚合 |
| **合理性** | ✅ 优秀 — 聚合是核心功能，测试场景设计贴近实际分析场景 |
| **到位程度** | ✅ 到位 |
| **缺口** | HAVING 子句（已由 HavingClauseIT 补充）、嵌套聚合的边界 |
| 优先级 | P3 |

#### ✅ CalculatedFieldTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~30（最大测试类，1434 行） |
| 覆盖功能 | 四则运算、函数调用、字段依赖链、安全检查（禁止函数）、错误处理、维度列计算、实际查询执行 |
| **合理性** | ✅ 非常优秀 — 计算字段是最复杂的功能之一，测试覆盖全面 |
| **到位程度** | ✅ 非常到位 |
| **缺口** | 极深嵌套（>5层）的性能退化、Unicode 字段名 |
| 优先级 | P3 |

#### ✅ CalculatedFieldAggregationBugTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~15（804 行） |
| 覆盖功能 | 嵌套聚合防护、column not found 修复、混合聚合、循环引用检测、多级依赖排序 |
| **合理性** | ✅ 非常优秀 — 专门的回归测试类，每个 bug 修复都有对应用例 |
| **到位程度** | ✅ 非常到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ AdvancedAnalyticsTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~12 |
| 覆盖功能 | COUNT(DISTINCT)、窗口函数（ROW_NUMBER/RANK/LAG/移动平均）、QM 预定义公式字段 |
| **合理性** | ✅ 优秀 — 高级分析是差异化功能 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | NTILE、PERCENT_RANK 等其他窗口函数、PARTITION BY 多字段、窗口函数与条件过滤组合 |
| 优先级 | P2 |

#### ✅ AccessControlTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~8 |
| 覆盖功能 | 权限 SQL 注入、字段引用 API、有/无权限数据对比、用户过滤 + 权限组合 |
| **合理性** | ✅ 优秀 — 安全相关测试很重要 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 多角色组合权限、权限冲突场景、空权限配置、越权访问尝试 |
| 优先级 | P1 |

#### ✅ DataComparisonTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~15（869 行） |
| 覆盖功能 | 基础聚合对比、维度分组对比、多维过滤对比、明细查询对比 |
| **合理性** | ✅ 非常优秀 — "模型 vs 原生 SQL"对比是黄金验证模式 |
| **到位程度** | ✅ 到位 |
| **缺口** | 可增加更多边界数据场景（NULL 密集数据、极端分布） |
| 优先级 | P3 |

#### ✅ CaptionDefTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~8 |
| 覆盖功能 | dialectFormulaDef 三级优先级、维度/属性/度量的公式覆盖、跨方言 SQL |
| **合理性** | ✅ 优秀 — 方言公式覆盖是跨数据库的关键能力 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ DimensionAutoExpandTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~5 |
| 覆盖功能 | 维度自动展开（$id/$caption/properties/子维度）、显式属性时禁用展开 |
| **合理性** | ✅ 合理 |
| **到位程度** | ⚠️ 偏薄 |
| **缺口** | 深层嵌套维度展开、混合显式+自动展开、空维度处理 |
| 优先级 | P2 |

#### ✅ ErrorCollectionTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~3（仅 96 行） |
| 覆盖功能 | 模型加载错误收集、状态报告 |
| **合理性** | ✅ 合理 — 但过于简单 |
| **到位程度** | ❌ 不到位 |
| **缺口** | 仅测试了加载错误，缺少查询时错误、多模型并发加载错误、错误恢复、错误消息国际化 |
| 优先级 | P1 |

#### ✅ FieldComparisonTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~15（748 行） |
| 覆盖功能 | $field 引用、$expr 表达式、各类操作符、算术运算、多字段操作 |
| **合理性** | ✅ 优秀 — 字段间比较是高级查询能力 |
| **到位程度** | ✅ 到位 |
| **缺口** | 跨表字段比较、无效字段引用的错误处理 |
| 优先级 | P3 |

#### ✅ ModelLoadingTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~10 |
| 覆盖功能 | TM/QM 加载、全部维度模型验证、事实表模型验证、维度/度量配置检查 |
| **合理性** | ✅ 优秀 — 模型加载是一切功能的基础 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 模型热更新、无效模型文件处理、模型文件语法错误、缺失依赖模型 |
| 优先级 | P2 |

#### ✅ MultiFactTableJoinTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~10 |
| 覆盖功能 | 多事实表 JOIN（同粒度/不同粒度）、LEFT JOIN、日期范围+分组+客户分析 |
| **合理性** | ✅ 优秀 — 多事实表是数仓核心场景 |
| **到位程度** | ✅ 到位 |
| **缺口** | 三表以上 JOIN、笛卡尔积防护、JOIN 条件冲突 |
| 优先级 | P2 |

#### ✅ NestedDimensionTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~12 |
| 覆盖功能 | 雪花模型多级嵌套、路径访问、别名、切片/过滤/分组 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ ParentChildDimensionTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 父子维度、层级查询、闭包表集成 |
| **合理性** | ✅ 优秀 — 闭包表是特色功能 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 深层级（>5层）性能、孤儿节点处理、闭包表数据不一致 |
| 优先级 | P2 |

#### ✅ ServiceIT.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~10 |
| 覆盖功能 | JdbcService 端到端、明细查询、分组聚合、分页、总列计算 |
| **合理性** | ✅ 优秀 — 服务层集成测试是质量保证的关键 |
| **到位程度** | ✅ 到位 |
| **缺口** | 异常场景（数据库连接失败、超时）、空数据集 |
| 优先级 | P2 |

#### ✅ DataEnvironmentTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | Docker 环境初始化验证、表存在性、数据量、外键、字典完整性 |
| **合理性** | ✅ 合理 — 作为环境验证很有必要 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口（环境验证不需要太多用例） |
| 优先级 | — |

---

### 2.2 方言测试（dialect/）— 3 文件

#### ✅ DialectTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~15 |
| 覆盖功能 | 标识符引用、分页 SQL、NULL 排序、字符串聚合、Schema 元数据、统计函数 |
| **合理性** | ✅ 优秀 — 四种数据库全部覆盖 |
| **到位程度** | ✅ 到位 |
| **缺口** | 特殊字符在标识符中的处理、极大 offset 分页 |
| 优先级 | P3 |

#### ✅ DialectBuildFunctionCallTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | YEAR/MONTH/DAY/HOUR/MINUTE/SECOND/DATE_FORMAT 跨方言翻译 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ✅ 到位 |
| **缺口** | 自定义日期格式串、时区相关函数 |
| 优先级 | P3 |

#### ✅ DialectFunctionTranslationTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | IFNULL→COALESCE、NVL→ISNULL、POW→POWER 等函数名映射 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ✅ 到位 |
| **缺口** | 不支持函数的友好报错 |
| 优先级 | P3 |

---

### 2.3 引擎测试（engine/）— 4 文件

#### ✅ AggSqlOptimizerTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~10（598 行） |
| 覆盖功能 | 聚合优化开关、多聚合列、条件、优化前后数据一致性 |
| **合理性** | ✅ 优秀 — 优化器测试需保证正确性不变 |
| **到位程度** | ✅ 到位 |
| **缺口** | 性能基准测试（优化效果量化） |
| 优先级 | P3 |

#### ✅ JoinGraphTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~12 |
| 覆盖功能 | 图创建、边添加、路径查找、缓存、环检测、拓扑排序、菱形依赖 |
| **合理性** | ✅ 优秀 — JOIN 图是查询引擎的关键数据结构 |
| **到位程度** | ✅ 到位 |
| **缺口** | 大规模图（>50 节点）性能、并发修改 |
| 优先级 | P3 |

#### ✅ PreAggregationMatcherTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~8 |
| 覆盖功能 | 维度/度量匹配、时间粒度上卷、优先级选择、禁用处理 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 部分匹配（只有部分维度匹配）、过滤条件对匹配的影响、多预聚合表竞争 |
| 优先级 | P2 |

#### ✅ CalculatedFieldJoinTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~6 |
| 覆盖功能 | 计算字段引用维度时自动触发 JOIN、SQL 验证 |
| **合理性** | ✅ 合理 |
| **到位程度** | ✅ 到位 |
| **缺口** | 计算字段引用多个维度的多 JOIN 场景 |
| 优先级 | P3 |

---

### 2.4 表达式测试（expression/）— 2 文件

#### ✅ InlineExpressionParserTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~8（164 行） |
| 覆盖功能 | 函数调用解析、算术表达式、多参数、维度引用、别名 |
| **合理性** | ✅ 合理 |
| **到位程度** | ⚠️ 偏薄 |
| **缺口** | 嵌套括号、操作符优先级边界、畸形表达式错误恢复、SQL 注入防护 |
| 优先级 | P1 |

#### ✅ SqlExpFactoryTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~5（94 行） |
| 覆盖功能 | 列引用创建、标识符解析、减法、字面量、二元表达式求值 |
| **合理性** | ✅ 合理 — 但过于简单 |
| **到位程度** | ❌ 不到位 |
| **缺口** | 复合表达式、函数表达式、NULL 处理、类型不匹配、一元表达式 |
| 优先级 | P1 |

---

### 2.5 权限测试（authorization/）— 1 文件

#### ✅ AuthorizationStepTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~10（428 行） |
| 覆盖功能 | SecurityContext 创建、过滤注入、执行管线顺序、租户/部门/用户级过滤、CONTINUE/ABORT 控制流 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 无权限用户的完全阻断、权限表达式求值错误、并发上下文隔离 |
| 优先级 | P1 |

---

### 2.6 反序列化测试（deserializer/）— 1 文件

#### ✅ ShorthandDeserializerTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~10（303 行） |
| 覆盖功能 | groupBy/orderBy/slice 简写格式、完整对象格式、$or 逻辑组、负号降序 |
| **合理性** | ✅ 优秀 — DSL 简写是用户体验的关键 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无效简写格式的错误消息、混合简写+完整格式 |
| 优先级 | P3 |

---

### 2.7 插件测试（plugins/）— 5 文件

#### ✅ AutoGroupByStepTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | AUTO GROUP BY 步骤单元测试 |
| **合理性** | ✅ 合理 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 与其他 Step 的交互、禁用时行为 |
| 优先级 | P2 |

#### ✅ AutoGroupByIT.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | AUTO GROUP BY 与真实查询引擎集成 |
| **合理性** | ✅ 优秀 — 单元+集成双重覆盖 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ HavingClauseIT.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | HAVING 子句生成和执行 |
| **合理性** | ✅ 合理 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 多 HAVING 条件、HAVING + 计算字段组合 |
| 优先级 | P2 |

#### ⚠️ InlineParserDebugTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 内联解析器调试工具 |
| **合理性** | ⚠️ 仅为调试用途，非正式测试 |
| **到位程度** | N/A — 调试辅助类 |
| **建议** | 标记为 @Disabled 或移至 test-utils |
| 优先级 | P3 |

#### ✅ QueryRequestValidationStepTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 查询请求参数验证 |
| **合理性** | ✅ 合理 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 全部验证规则的覆盖、自定义验证扩展 |
| 优先级 | P2 |

---

### 2.8 命名空间测试（namespace/）— 1 文件

#### ✅ NamespaceContextTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~8（138 行） |
| 覆盖功能 | ThreadLocal get/set、默认值、清除、线程隔离、null/空串、多次 set/clear |
| **合理性** | ✅ 优秀 — ThreadLocal 必须测试线程隔离 |
| **到位程度** | ✅ 到位 |
| **缺口** | 线程池复用时的清理、InheritableThreadLocal 场景 |
| 优先级 | P2 |

---

### 2.9 SPI/缓存测试（spi/）— 1 文件

#### ✅ QueryCacheProviderTest.java
| 项目 | 详情 |
|------|------|
| 测试方法数 | ~8（324 行） |
| 覆盖功能 | L1/L2 缓存开关、SecurityContext 权限、NoOp 单例、配置工厂、自定义实现 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 缓存过期、缓存穿透/击穿、并发 put/get、序列化/反序列化 |
| 优先级 | P2 |

---

### 2.10 预聚合测试（preagg/）— 3 文件

#### ✅ PreAggregationIT.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 预聚合全流程（匹配→改写→执行） |
| **合理性** | ✅ 优秀 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ PreAggregationEdgeCaseTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 边界场景处理 |
| **合理性** | ✅ 优秀 — 专门的边界测试 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ PreAggregationDataValidationTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 预聚合数据正确性验证 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ✅ 到位 |
| **缺口** | 预聚合数据过期/刷新 |
| 优先级 | P3 |

---

### 2.11 语义层测试（semantic/）— 3 文件

#### ✅ SemanticServiceV3Test.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | V3 语义元数据服务（$id/$caption 展开） |
| **合理性** | ✅ 优秀 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 大量模型的元数据性能、权限过滤下的元数据 |
| 优先级 | P2 |

#### ✅ SemanticQueryValidationTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 语义查询参数验证 |
| **合理性** | ✅ 合理 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 全部验证规则覆盖、友好错误消息 |
| 优先级 | P2 |

#### ✅ SemanticRequestContextTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 请求上下文（namespace、security）处理 |
| **合理性** | ✅ 合理 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

---

### 2.12 Odoo 集成测试（odoo/）— 5 文件

#### ✅ OdooModelLoadingTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | Odoo 9 个业务模型的 TM/QM 加载验证 |
| **合理性** | ✅ 优秀 |
| **到位程度** | ✅ 到位 |
| **缺口** | 模型热更新 |
| 优先级 | P3 |

#### ✅ OdooHierarchyQueryTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | Odoo 模型的层级查询（闭包表） |
| **合理性** | ✅ 优秀 — Odoo 大量使用父子关系 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ ClosureOperatorOrSliceTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 闭包操作符与 OR 条件/Slice 组合 |
| **合理性** | ✅ 优秀 — 复杂条件组合测试 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ PermissionSliceTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 权限行过滤（Slice） |
| **合理性** | ✅ 合理 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

#### ✅ SelfReferencingDimensionAliasTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 自引用维度别名解析 |
| **合理性** | ✅ 优秀 — 边界场景专项测试 |
| **到位程度** | ✅ 到位 |
| **缺口** | 无明显缺口 |
| 优先级 | — |

---

### 2.13 多数据库测试（multidb/）— 2 文件

#### ✅ MultiDatabaseQueryTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 跨数据库方言的查询一致性 |
| **合理性** | ✅ 非常优秀 — 多数据库是核心卖点 |
| **到位程度** | ⚠️ 基本到位 |
| **缺口** | 方言特有功能的降级处理、跨库事务 |
| 优先级 | P2 |

---

### 2.14 Demo 权限测试（demo/）— 4 文件

#### ✅ FactOrderDemoAuthQueryModelTest.java / FactSalesDemoAuthQueryModelTest.java
| 项目 | 详情 |
|------|------|
| 覆盖功能 | 完整权限演示（Spring Bean 注入、字段 API） |
| **合理性** | ✅ 合理 — 兼做文档和测试 |
| **到位程度** | ⚠️ 偏向演示，断言不够严格 |
| **缺口** | 更严格的结果验证 |
| 优先级 | P3 |

#### DemoDataMaskingStep.java / DemoTestSupport.java
| 项目 | 详情 |
|------|------|
| **性质** | 辅助类/支撑基础设施，非测试用例 |
| **合理性** | ✅ 合理 — 代码复用好 |
| 优先级 | — |

---

## 三、测试覆盖缺口汇总

### 🔴 P0 — 完全缺失（需新建测试类）

| # | 缺失领域 | 对应生产代码 | 风险等级 | 状态 |
|---|----------|-------------|---------|------|
| 1 | **Controller 层测试** | `SemanticController`, `QueryModelDataStoreController`, `DimensionDataStoreController` | 高 | 🔲 待建（需 MockMvc） |
| 2 | **AutoConfiguration 测试** | `DbModelAutoConfiguration` | 中 | ✅ 已建 `DbModelAutoConfigurationTest` |
| 3 | **异常处理器测试** | `FoggyDatasetExceptionHandler` | 中 | ✅ 已建 `FoggyDatasetExceptionHandlerTest` |
| 4 | **CTE 组合器测试** | `CteComposer`, `ComposedSql`, `CteUnit` | 中 | ⏸️ 开发中，暂缓 |
| 5 | **DataSetResult 测试** | `DataSetResult`（withJoin/filter/sort/compute） | 中 | ⏸️ 依赖 CTE，暂缓 |
| 6 | **SQL 日志拦截器测试** | `SqlLoggingInterceptor` | 低 | 🔲 待建 |
| 7 | **Bundle 生命周期测试** | `BundleLifecycleListener` | 中 | ✅ 已建 `BundleLifecycleListenerTest` |
| 8 | **i18n 消息测试** | `DatasetMessages` + properties 文件 | 低 | 🔲 待建 |
| 9 | **Proxy 模式测试** | `TableModelProxy`, `DimensionProxy` | 中 | ✅ 已建 `TableModelProxyTest` + `DimensionProxyTest` |
| 10 | **QueryFacade 测试** | `QueryFacadeImpl` | 中 | ✅ 已建 `QueryFacadeImplTest` |

### 🟡 P1 — 现有测试不到位（需增强）

| # | 测试类 | 增强方向 | 状态 |
|---|--------|---------|------|
| 1 | `ErrorCollectionTest` | ModelLoadStatus 枚举、ErrorType/ErrorLevel 覆盖、多模型独立性 | ✅ 已增强（+9 方法） |
| 2 | `SqlExpFactoryTest` | 复合表达式、函数、一元运算符、安全检查、AllowedFunctions | ✅ 已增强（94→250+ 行） |
| 3 | `InlineExpressionParserTest` | 深层嵌套、空白边界、混合表达式、特殊输入 | ✅ 已增强（163→270+ 行） |
| 4 | `AccessControlTest` | 无权限对比、多条件验证、权限+排序组合 | ✅ 已增强（+3 方法） |
| 5 | `AuthorizationStepTest` | 边界值、空字符串、ABORT 链、空步骤、process 独立性 | ✅ 已增强（+7 方法） |

### 🟢 P2 — 建议补充（提升健壮性）

| # | 测试类/领域 | 补充方向 | 状态 |
|---|------------|---------|------|
| 1 | `BasicQueryTest` | 空结果、超长 IN、特殊字符、NULL 条件 | 🔲 待补充 |
| 2 | `AdvancedAnalyticsTest` | 更多窗口函数、多字段 PARTITION BY | 🔲 待补充 |
| 3 | `DimensionAutoExpandTest` | 深层嵌套、混合展开 | 🔲 待补充 |
| 4 | `ModelLoadingTest` | 热更新、无效文件、语法错误 | 🔲 待补充 |
| 5 | `PreAggregationMatcherTest` | 部分匹配、条件影响、多表竞争 | 🔲 待补充 |
| 6 | `QueryCacheProviderTest` | 过期、穿透、并发、序列化 | 🔲 待补充 |
| 7 | `NamespaceContextTest` | 线程池复用清理 | 🔲 待补充 |
| 8 | `MultiDatabaseQueryTest` | 功能降级处理 | 🔲 待补充 |
| 9 | `SemanticServiceV3Test` | 大量模型性能、权限过滤元数据 | 🔲 待补充 |
| 10 | `HavingClauseIT` | 多 HAVING、HAVING + 计算字段 | 🔲 待补充 |
| 11 | `ParentChildDimensionTest` | 深层级性能、孤儿节点 | 🔲 待补充 |
| 12 | `ServiceIT` | 异常场景、空数据集 | 🔲 待补充 |
| 13 | `MultiFactTableJoinTest` | 三表 JOIN、笛卡尔积防护 | 🔲 待补充 |
| 14 | 并发安全测试（新建） | ThreadLocal 泄漏、模型并发加载/查询 | 🔲 待建 |

---

## 四、测试架构评估

### 4.1 继承体系 ✅ 优秀

```
DemoTestSupport（基础设施：模型加载、SQL 执行、结果打印）
  └── EcommerceTestSupport（电商特化：数据源、公式服务）
        └── BasicQueryTest / AggregationQueryTest / ...
```

**优点**：代码复用好，新测试类只需继承即可获得完整基础设施
**建议**：无需改进

### 4.2 测试数据策略 ✅ 优秀

- 使用真实电商域模型（订单、销售、库存、退货）
- Docker 多数据库环境 + 轻量 SQLite 内存模式双轨
- 数据对比模式（模型查询 vs 原生 SQL）确保正确性

### 4.3 断言模式 ⚠️ 可改进

**当前**：大量使用 `assertNotNull` + `assertTrue(size > 0)` + `System.out.println`

**建议**：
- 用 AssertJ 的 `assertThat(list).hasSize(N).extracting("field").containsExactly(...)` 提升可读性
- 减少 `System.out.println` 依赖，改为断言
- 部分 Demo 测试过于依赖目视检查，缺乏自动断言

### 4.4 测试命名 ⚠️ 可改进

**当前**：部分测试用 `@DisplayName` 中文描述，部分仅有方法名

**建议**：统一使用 `@DisplayName` + 有意义的中文描述

---

## 五、执行建议路线图

### Phase 1（短期）— 修补安全/关键缺口 ✅ 已完成

- [x] 增强 `InlineExpressionParserTest` — 深层嵌套、空白边界、特殊输入
- [x] 增强 `SqlExpFactoryTest` — 复合表达式、函数、安全检查、AllowedFunctions
- [x] 增强 `AccessControlTest` — 无权限对比、多条件、排序组合
- [x] 增强 `ErrorCollectionTest` — Status 枚举、ErrorType 覆盖、多模型独立性
- [x] 增强 `AuthorizationStepTest` — 边界值、ABORT 链、空步骤
- [x] 新建 `FoggyDatasetExceptionHandlerTest` — 业务/系统异常、RX 格式
- [x] 新建 `DbModelAutoConfigurationTest` — Bean 注册验证
- [x] 新建 `TableModelProxyTest` — 字段访问、JOIN、别名、equals
- [x] 新建 `DimensionProxyTest` — 链式路径、ColumnRef 转换
- [x] 新建 `BundleLifecycleListenerTest` — 缓存清理、命名空间隔离
- [x] 新建 `QueryFacadeImplTest` — 端到端查询、异常场景

### Phase 2（中期）— Controller 层 + 剩余 P0

- [ ] 新建 `SemanticControllerTest`（MockMvc）
- [ ] 新建 `QueryModelDataStoreControllerTest`（MockMvc）
- [ ] 新建 `SqlLoggingInterceptorTest`
- [ ] 新建 `DatasetMessagesTest`（i18n）

### Phase 3（长期，持续）— 健壮性提升

- [ ] 逐步补充 P2 列表中的用例
- [ ] 引入 AssertJ 替代部分朴素断言
- [ ] 建立并发安全测试套件
- [ ] 统一 `@DisplayName` 命名规范
- [ ] 清理 Debug 类（InlineParserDebugTest → @Disabled 或删除）

---

## 六、统计仪表盘

| 指标 | 初始值 | 当前值 | 变化 |
|------|--------|--------|------|
| 测试文件总数 | 55 | 61 | +6 |
| 正式测试类 | 47 | 53 | +6 |
| 辅助/支撑类 | 8 | 8 | — |
| 测试方法总数（估） | ~300+ | ~340+ | +40 |
| P0 缺失领域 | 10 | 4（含2暂缓） | -6 ✅ |
| P1 待增强 | 5 | 0 | -5 ✅ |
| P2 待补充 | 14 | 14 | — |
| 已到位（无需改动） | 18 | 29 | +11 |

---

*本文档作为持续跟踪文件，每次补充测试后更新对应状态标记。*
