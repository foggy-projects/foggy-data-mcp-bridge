# S10 Python Parity 规划与冻结契约

## 1. Java 侧基线冻结 (API Snapshot)

为了确保 Python 引擎能够精确实现与 Java 引擎相同的语义，我们需要冻结以下接口的输入/输出快照：

### 1.1 Pivot Request & AST 冻结
- **目标**: 确保 Python 解析器 `PivotAstBuilder` 产生的 `PivotOptions`、`AxisField`、`CalculatedFieldDef` 结构一致。
- **快照方法**: 使用 10+ 种典型场景的 `SemanticQueryRequest` 作为 JSON 范本，确保 Python `ConvertFrom-Json` 后生成的模型对象一一对应。

### 1.2 Pivot Result 结构冻结
- **目标**: 确保 Python 数据流返回与 Java 侧同样的响应体（包含 `_sys_meta` 注入、树形层级、`data` / `items` 结构）。
- **结构检查点**:
  - `format="flat"` 时的行级对齐。
  - `format="grid"` 时 `rows`, `columns`, `metrics`, `data` 数组对齐。
  - `format="tree"` 时的 `TreeNode` 嵌套结构对齐。

## 2. Python 侧对齐清单 (Implementation Checklist)

### 2.1 AST 层映射
- [ ] 在 `foggy_dataset.model.semantic` 增加 `pivot` 相关 Pydantic Schema。
- [ ] 对齐 `HierarchyMode` (flat, tree)。
- [ ] 限制校验：Python 侧同样拒绝 `tree` + `crossjoin` 或 `tree` + `subtotals`。

### 2.2 内存算法管道对齐 (Memory Pipeline)
- [ ] **Phase 1 骨架查询**: 实现 Pivot 查询时的 SQL 生成屏蔽 (确保 groupBy 不包含被截断的列)。
- [ ] **Phase 2.1 轴切片器 (AxisHavingFilter)**: 使用 Pandas / PyArrow 实现。
- [ ] **Phase 2.2 TopN 截断器 (AxisTopNTruncator)**: 实现分组内排序和截断。
- [ ] **Phase 2.3 属性挂载 (PropertyAttacher)**: 对齐字典匹配（或者使用 Pandas merge）。
- [ ] **Phase 3 Shaper (ResultShaper)**: 实现 Flat / Grid / Tree 三种结果形态的内存组装。

### 2.3 非可加度量合并与降级 (Non-Additive Rollup)
- [ ] **MetricAdditivityAnalyzer**: Python 侧复刻递归检测逻辑（P0-4 循环依赖检测、阻断 `ROLLUP_TO`、分析 `COUNT_DISTINCT`/`AVG`）。
- [ ] **RollupCache**: 缓存 `Coordinate` 命中策略。
- [ ] **NonAdditiveRollupExecutor**:
  - Python SQLAlchemy/PyPika 侧实现批量 `UNION ALL` 生成。
  - **关键防御**: 使用 `try-except` 捕获数据库语法错误并降级到 `executeGrainSerial` 串行执行。

## 3. 测试与签收
- 所有由 Java 侧 `PivotIntegrationTest` 生成的 Snapshot JSON，必须被 Python 侧作为单元测试全部打通。
- 提供跨语言测试脚本 `verify_parity.py`，调用同样的查询并比较 Java / Python 响应的一致性。
