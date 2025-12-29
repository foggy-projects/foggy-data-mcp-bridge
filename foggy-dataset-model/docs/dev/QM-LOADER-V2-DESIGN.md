# QM Loader V2 设计方案

## 1. 背景与目标

### 1.1 当前问题

现有 QM 文件配置方式存在以下痛点：
- JOIN 条件用字符串定义（如 `'fo.order_id = fp.order_id'`），无类型检查
- 字段引用用字符串（如 `name: 'orderId'`），重构时易遗漏
- IDE 无法提供智能提示和错误检测

### 1.2 目标

- 提供类型安全的字段引用方式
- 支持链式 API 定义 JOIN 关系
- 保持配置的简洁性和可读性
- QM 加载时进行字段存在性校验

## 2. V2 格式规范

### 2.1 语法定义

```javascript
// 内置函数，无需 import
const fo = loadTableModel('FactOrderModel');
const fp = loadTableModel('FactPaymentModel');

export const queryModel = {
    name: 'OrderPaymentJoinQueryModel',
    caption: '订单支付联合查询',
    loader: 'v2',  // 声明使用 V2 加载器

    // 主表（对应 JoinGraph.root）
    model: fo,

    // JOIN 关系定义（每项对应 JoinGraph.addEdge()）
    joins: [
        fo.leftJoin(fp).on(fo.orderId, fp.orderId)
    ],

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { ref: fo.orderId, ui: { fixed: 'left', width: 150 } },
                { ref: fo.orderStatus },
                { ref: fo.orderTime }
            ]
        },
        {
            caption: '支付信息',
            items: [
                { ref: fp.paymentId },
                { ref: fp.payMethod },
                { ref: fp.payAmount }
            ]
        }
    ],

    orders: [
        { ref: fo.orderTime, order: 'desc' }
    ]
};
```

### 2.2 结构说明（IDE 可视化友好）

| 字段 | 类型 | JoinGraph 映射 | IDE 可视化 |
|------|------|----------------|------------|
| `model` | `TableModelProxy` | `new JoinGraph(root)` | 主表选择器 |
| `joins` | `JoinBuilder[]` | 每项 → `addEdge()` | 关联关系列表 |
| `columnGroups` | 分组配置 | - | 字段配置面板 |
| `orders` | 排序配置 | - | 排序规则列表 |

### 2.3 IDE 配置界面示意

```
┌─────────────────────────────────────────────────┐
│ 查询模型配置                                     │
├─────────────────────────────────────────────────┤
│ 主表 (model)                                    │
│ ┌─────────────────────────────────────────────┐ │
│ │ [选择表模型] FactOrderModel              ▼ │ │
│ └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│ 关联关系 (joins)                       [+ 添加] │
│ ┌─────────────────────────────────────────────┐ │
│ │ ① fo ──[LEFT JOIN]──▶ fp                   │ │
│ │   ON: fo.orderId = fp.orderId    [编辑][✕] │ │
│ ├─────────────────────────────────────────────┤ │
│ │ ② fo ──[LEFT JOIN]──▶ dc                   │ │
│ │   ON: fo.customerId = dc.id      [编辑][✕] │ │
│ └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│ 显示字段 (columnGroups)                         │
│ ...                                             │
└─────────────────────────────────────────────────┘
```

## 3. 核心类设计

### 3.1 类图

```
┌─────────────────────────────────────────────────────────────────┐
│                        fsscript 扩展点                           │
├─────────────────────────────────────────────────────────────────┤
│  PropertyHolder          │  PropertyFunction                    │
│  + getProperty(name)     │  + invoke(evaluator, method, args)  │
└─────────────────────────────────────────────────────────────────┘
                    △                      △
                    │                      │
         ┌─────────┴──────────────────────┴─────────┐
         │                                          │
┌────────┴────────┐                    ┌────────────┴────────────┐
│ TableModelProxy │                    │      JoinBuilder        │
├─────────────────┤                    ├─────────────────────────┤
│ - modelName     │ ──creates──>       │ - left: TableModelProxy │
│ - alias         │                    │ - right: TableModelProxy│
│ - columnRefs    │                    │ - joinType: JoinType    │
├─────────────────┤                    │ - conditions: List      │
│ + getProperty() │                    ├─────────────────────────┤
│ + leftJoin()    │                    │ + on(left, right)       │
│ + innerJoin()   │                    │ + and(left, right)      │
│ + rightJoin()   │                    │ + eq(left, value)       │
└────────┬────────┘                    └─────────────────────────┘
         │
         │ creates
         ▼
┌─────────────────┐                    ┌─────────────────────────┐
│   ColumnRef     │                    │    JoinCondition        │
├─────────────────┤                    ├─────────────────────────┤
│ - proxy         │                    │ - left: ColumnRef       │
│ - columnName    │                    │ - operator: String      │
│ - subProperty   │                    │ - right: Object         │
├─────────────────┤                    └─────────────────────────┘
│ + getFullRef()  │
└─────────────────┘
```

### 3.2 包结构

```
com.foggyframework.dataset.db.model
├── proxy/                          # 代理类包
│   ├── TableModelProxy.java        # 表模型代理（PropertyHolder + PropertyFunction）
│   ├── ColumnRef.java              # 字段引用
│   ├── JoinBuilder.java            # JOIN 构建器
│   ├── JoinBuilderFunction.java    # JoinBuilder 到 FsscriptFunction 适配器
│   ├── JoinCondition.java          # JOIN 条件
│   └── LoadTableModelFunction.java # loadTableModel 内置函数
├── def/query/
│   ├── DbQueryModelDef.java        # 添加 loader 和 joins 字段
│   └── SelectColumnDef.java        # ref 支持 ColumnRef 类型
├── engine/query_model/
│   ├── QueryModelLoaderImpl.java   # 移除 V2 分支，统一流程
│   ├── QueryModelBuilderV2.java    # V2 格式专用构建器（实现 QueryModelBuilder）
│   └── QueryModelLoaderV2.java     # 辅助类（解析和校验）
├── spi/
│   └── QueryModelBuilder.java      # 策略接口
└── config/
    └── QmValidationOnStartup.java  # 启动时校验（可配置）
```

### 3.3 架构说明

V2 采用 **策略模式**，通过 `QueryModelBuilder` 接口扩展：

```
QueryModelLoaderImpl（统一入口）
    │
    ├── parseV1Model()  → 解析 V1 格式
    │
    └── for each QueryModelBuilder:
            │
            ├── QueryModelBuilderV2 (优先级最高)
            │   ├── 检查 loader == 'v2'
            │   ├── 解析 model + joins
            │   └── 创建 QueryModel
            │
            └── JdbcTableModelLoaderImpl
                └── 处理 V1 格式
```

## 4. 详细设计

### 4.1 ColumnRef - 字段引用

```java
public class ColumnRef {
    private final TableModelProxy tableModelProxy;
    private final String columnName;
    private final String subProperty;  // 用于 fo.customer$memberLevel 场景

    // 获取完整引用路径
    public String getFullRef() {
        if (subProperty != null) {
            return columnName + "$" + subProperty;
        }
        return columnName;
    }

    // 获取所属表模型名
    public String getModelName() {
        return tableModelProxy.getModelName();
    }
}
```

### 4.2 TableModelProxy - 表模型代理

```java
public class TableModelProxy implements PropertyHolder, PropertyFunction {

    private final String modelName;
    private String alias;
    private final Map<String, ColumnRef> columnRefs = new HashMap<>();

    // PropertyHolder: 支持 fo.orderId 语法
    @Override
    public Object getProperty(String name) {
        // 处理 fo.customer$memberLevel 语法
        if (name.contains("$")) {
            String[] parts = name.split("\\$", 2);
            return new ColumnRef(this, parts[0], parts[1]);
        }
        return columnRefs.computeIfAbsent(name, k -> new ColumnRef(this, k, null));
    }

    // PropertyFunction: 支持 fo.leftJoin(fp) 语法
    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        return switch (methodName) {
            case "leftJoin" -> new JoinBuilder(this, (TableModelProxy) args[0], JoinType.LEFT);
            case "innerJoin" -> new JoinBuilder(this, (TableModelProxy) args[0], JoinType.INNER);
            case "rightJoin" -> new JoinBuilder(this, (TableModelProxy) args[0], JoinType.RIGHT);
            default -> PropertyHolder.NO_MATCH;
        };
    }
}
```

### 4.3 JoinBuilder - JOIN 构建器

```java
public class JoinBuilder implements PropertyFunction {

    private final TableModelProxy left;
    private final TableModelProxy right;
    private final JoinType joinType;
    private final List<JoinCondition> conditions = new ArrayList<>();

    // 支持链式调用
    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        return switch (methodName) {
            case "on", "eq" -> {
                addCondition((ColumnRef) args[0], "=", args[1]);
                yield this;
            }
            case "and" -> {
                addCondition((ColumnRef) args[0], "=", args[1]);
                yield this;
            }
            case "neq" -> {
                addCondition((ColumnRef) args[0], "<>", args[1]);
                yield this;
            }
            default -> PropertyHolder.NO_MATCH;
        };
    }

    private void addCondition(ColumnRef left, String operator, Object right) {
        conditions.add(new JoinCondition(left, operator, right));
    }

    // 生成 ON 子句 SQL
    public String buildOnClause() {
        return conditions.stream()
            .map(JoinCondition::toSqlFragment)
            .collect(Collectors.joining(" AND "));
    }
}
```

### 4.4 JoinCondition - JOIN 条件

```java
public class JoinCondition {
    private final ColumnRef left;
    private final String operator;
    private final Object right;  // ColumnRef 或常量值

    public String toSqlFragment() {
        String leftPart = left.getTableModelProxy().getAlias() + "." + left.getColumnName();
        String rightPart;
        if (right instanceof ColumnRef cr) {
            rightPart = cr.getTableModelProxy().getAlias() + "." + cr.getColumnName();
        } else {
            rightPart = "?";  // 常量用占位符
        }
        return leftPart + " " + operator + " " + rightPart;
    }
}
```

## 5. 加载流程

### 5.1 V2 加载器流程

```
┌────────────────────────────────────────────────────────────────┐
│  1. fsscript 执行 QM 文件                                       │
│     - loadTableModel('FactOrderModel') → TableModelProxy       │
│     - fo.orderId → ColumnRef                                   │
│     - fo.leftJoin(fp).on(...) → JoinBuilder                   │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│  2. QueryModelLoaderImpl 检测 loader 字段                       │
│     - loader == null || 'v1' → 走原有逻辑                       │
│     - loader == 'v2' → 调用 QueryModelLoaderV2                 │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│  3. QueryModelLoaderV2 解析                                     │
│     a. 解析 model 数组                                          │
│        - TableModelProxy → 加载 TableModel，创建 JdbcModelDx   │
│        - JoinBuilder → 提取 JOIN 信息，创建 JdbcModelDx        │
│     b. 解析 columnGroups                                        │
│        - ColumnRef → 校验字段存在性，创建 DbQueryColumn         │
│     c. 解析 orders                                              │
│        - ColumnRef → 校验字段存在性，创建 OrderDef              │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│  4. 字段校验                                                    │
│     - 检查 ColumnRef 引用的字段在对应 TableModel 中是否存在     │
│     - 不存在时抛出明确错误：                                    │
│       "字段 'orderIdXXX' 在模型 'FactOrderModel' 中不存在"      │
└────────────────────────────────────────────────────────────────┘
```

### 5.2 启动时校验（可配置）

```yaml
foggy:
  dataset:
    validate-on-startup: true  # 启动时校验所有 QM
```

```java
@Component
@ConditionalOnProperty(name = "foggy.dataset.validate-on-startup", havingValue = "true")
public class QmValidationOnStartup implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // 扫描所有 .qm 文件并加载校验
        List<BundleResource> qmFiles = findAllQmFiles();
        for (BundleResource qm : qmFiles) {
            try {
                queryModelLoader.loadJdbcQueryModel(qm);
                log.info("QM 校验通过: {}", qm.getPath());
            } catch (Exception e) {
                log.error("QM 校验失败: {} - {}", qm.getPath(), e.getMessage());
                throw e;  // 或收集所有错误后统一抛出
            }
        }
    }
}
```

## 6. 内置函数注册

```java
@Configuration
public class DatasetFsscriptConfig {

    @Bean
    public FsscriptBuiltinFunction loadTableModelFunction(
            TableModelLoaderManager tableModelLoaderManager) {
        return new FsscriptBuiltinFunction("loadTableModel", args -> {
            String modelName = (String) args[0];
            return new TableModelProxy(modelName);
        });
    }
}
```

## 7. 错误提示设计

### 7.1 字段不存在

```
QM加载失败: OrderPaymentJoinQueryModel
  位置: columnGroups[0].items[2].ref
  错误: 字段 'orderIdXXX' 在模型 'FactOrderModel' 中不存在
  建议: 可用字段包括 orderId, orderStatus, orderTime, ...
```

### 7.2 模型不存在

```
QM加载失败: OrderPaymentJoinQueryModel
  位置: loadTableModel('FactOrderModelXXX')
  错误: 表模型 'FactOrderModelXXX' 不存在
  建议: 请检查模型名称是否正确
```

## 8. 实现计划

| 阶段 | 任务 | 文件 |
|------|------|------|
| 1 | 创建 ColumnRef 类 | `proxy/ColumnRef.java` |
| 2 | 创建 JoinCondition 类 | `proxy/JoinCondition.java` |
| 3 | 创建 JoinBuilder 类 | `proxy/JoinBuilder.java` |
| 4 | 创建 TableModelProxy 类 | `proxy/TableModelProxy.java` |
| 5 | 修改 DbQueryModelDef | `def/query/DbQueryModelDef.java` |
| 6 | 创建 QueryModelLoaderV2 | `engine/query_model/QueryModelLoaderV2.java` |
| 7 | 修改 QueryModelLoaderImpl | `engine/query_model/QueryModelLoaderImpl.java` |
| 8 | 注册内置函数 | `config/DatasetFsscriptConfig.java` |
| 9 | 更新 Demo QM 文件 | `demo/.../OrderPaymentJoinQueryModel.qm` |
| 10 | 添加启动校验 | `config/QmValidationOnStartup.java` |

## 9. 向后兼容

- 通过 `loader` 字段区分版本
- `loader` 为空或 `'v1'` 时使用原有逻辑
- `loader: 'v2'` 时使用新加载器
- 同一项目可混用两种格式的 QM 文件
