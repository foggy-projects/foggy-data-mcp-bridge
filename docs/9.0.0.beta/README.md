# Foggy Pivot Engine V9.0.0.beta

## 1. 版本概览 (Changelog & Summary)

V9.0.0.beta 标志着 Foggy Dataset 对多维分析 (Pivot) 能力的全面升级。本次大版本解耦了旧有的扁平化聚合管线，引入了专属的四阶段 Pivot Engine (Phase 1 基础聚合 -> Phase 2 内存加工 -> Phase 2.5 属性贴合 -> Phase 3 格式化塑形)，为 LLM Agent 和前端界面提供了结构化、安全且高度可控的透视分析能力。

### 核心特性：
- **独立的 Pivot AST 与 DSL**：使用 `rows`, `columns`, `metrics` 替代旧版结构，支持网格、树状和扁平三种形态输出。
- **动态列维度交叉与稀疏展开 (CrossJoin)**：允许智能识别列基数，实现多轴嵌套展示。
- **高级过滤与截断 (Having / TopN)**：轴级别的结果后过滤与 TopN 截断。
- **父子层级树 (Hierarchy Tree)**：通过 `hierarchyMode=tree` 自动组装嵌套维度树，递归汇总度量。
- **结构化父级占比 (parentShare)**：通过 `pivot.metrics` 对象元素表达子级占父级比例，替代公开 `ROLLUP_TO` 函数字符串。
- **Non-Additive Rollup**：基于元数据分析度量的可加性，对于 `COUNT DISTINCT` 等不可加度量，自动改写为批量 `UNION ALL` 查询以生成安全且精确的跨层级小计与总计。
- **安全拦截与 Fail-Closed 策略**：严格验证未声明的度量、系统切片 (System Slices) 与列权限 (Denied Columns)，任何非法访问默认阻断。

## 2. 已知限制清单 (Known Limitations)

- **树形层级限制**：`hierarchyMode=tree` 仅支持在 `rows` 轴使用，并且不能与 `crossjoin=true` 稀疏展开混用。
- **小计生成互斥**：当启用树形展开 (`hierarchyMode=tree`) 时，暂不支持再通过内存 `subtotals` 自动补全，因为树的父子节点本身已经起到了小计的作用。
- **MDX 坐标能力状态**：`CELL_AT` / `AXIS_MEMBER` 状态为 `rejected-for-public-dsl`——不作为 LLM 可生成的公开 DSL。`ROLLUP_TO` 不作为公开函数字符串；父级占比已由 S11 `parentShare` 第一版覆盖。高频跨轴基准引用已由 S12 `baselineRatio` 结构化派生指标完全覆盖。
- **级联 Generate**：状态为 `deferred / known-limitation`。单层分组 TopN 已支持，级联多层截断暂缓。
- **辅助查询性能开销**：如果在复杂的 CTE 场景或旧版方言中生成小计，`UNION ALL` 合并可能受限，系统将降级为多次串行查询执行，从而在超大数据集上可能带来额外的查询开销。
- **Python 端功能镜像**：目前 Pivot V9.0 仅在 Java Core 与 MCP Schema 层完成冻结与 Parity 测试，Python 镜像暂未完全对齐（详见 `s10_python_parity_plan.md`）。

## 3. 示例请求与预期响应 (Examples)

### 标准 Pivot 聚合 (请求小计)

**Request (JSON-RPC via MCP)**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "query_model",
  "params": {
    "model": "FactSalesQueryModel",
    "query": {
      "pivot": {
        "rows": [{"field": "product$categoryName"}],
        "columns": [{"field": "salesDate$year"}],
        "metrics": ["salesAmount", "uniqueCustomers"],
        "options": {
          "rowSubtotals": true,
          "columnSubtotals": true,
          "grandTotal": true
        },
        "outputFormat": "grid"
      }
    }
  }
}
```

**Response (Expected)**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "schema": { ... },
    "pivot": {
      "data": [
        {
          "product$categoryName": "Electronics",
          "salesDate$year": "2023",
          "salesAmount": 1500.0,
          "uniqueCustomers": 120
        },
        ...
        {
          "product$categoryName": "GRAND_TOTAL",
          "salesDate$year": "GRAND_TOTAL",
          "salesAmount": 50000.0,
          "uniqueCustomers": 350,
          "_sys_meta": { "isGrandTotal": true }
        }
      ]
    }
  }
}
```

## 4. 回归测试命令固化 (Regression Testing)

Pivot V9.0.0.beta 的核心组件已经过严格测试，为了验证 SQL 生成与内存管线的可靠性，我们在三套数据库环境中建立了 SQL Oracle Parity 测试标准。

要执行全量的 Pivot Parity 集成验证，请在 `foggy-data-mcp-bridge-wt-dev-compose` 根目录下运行以下命令：

**1. SQLite 本地验证 (轻量级，默认)**:
```bash
mvn test -pl foggy-dataset-model -Dtest=PivotSqlParityIntegrationTest -Dspring.profiles.active=sqlite -P!multi-db
```

**2. PostgreSQL 验证 (需预先启动 Docker 容器)**:
```bash
mvn test -pl foggy-dataset-model -Dtest=PivotSqlParityIntegrationTest -Dspring.profiles.active=postgres -P!multi-db
```

**3. MySQL 8 验证 (需预先启动 Docker 容器)**:
```bash
mvn test -pl foggy-dataset-model -Dtest=PivotSqlParityIntegrationTest -Dspring.profiles.active=mysql8 -P!multi-db
```

*注意: 必须确保环境中的对应的容器 (如 `foggy-postgres` / `foggy-mysql`) 处于运行状态，才能成功执行对应的 Parity 验证。*
