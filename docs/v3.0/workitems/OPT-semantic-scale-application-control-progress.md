## 文档作用

| 属性 | 值 |
|------|-----|
| doc_type | workitem-progress |
| intended_for | Java engine team |
| purpose | 记录 semantic scale namespace loading policy 的执行进度 |
| status | replanned |

---

## 方向调整记录

上一轮 runtime `applySemanticScale` 方案已撤销。

已还原代码路径：

| 文件 | 处理 |
|------|------|
| `DatasetProperties.java` | 移除 `mcpDefault/restDefault` runtime 配置 |
| `DbQueryRequestDef.java` | 移除 `applySemanticScale` 请求字段 |
| `SemanticRequestContext.java` | 移除 `applySemanticScale` 上下文传递 |
| `ModelResultContext.java` | 移除 `applySemanticScale` 字段 |
| `DatasetAccessorConfig.java` | 移除 `DatasetProperties` 注入 |
| `LocalDatasetAccessor.java` | 移除 MCP 入口默认 scale 注入 |

新的方案：namespace 隔离 + 加载期 scale profile。

---

## 新开发计划

### Step 1：配置层

状态：todo

目标：

- 在 `DatasetProperties` 中新增 `SemanticScaleConfig`。
- 配置字段：
  - `defaultEnabled = true`
  - `disabledNamespaces = []`

验收：

- 不再出现 `mcpDefault/restDefault`。
- 不再支持请求级覆盖。
- `null` / blank namespace 的判断规则明确。

### Step 2：namespace scale policy

状态：todo

目标：

- 新增小型 resolver/helper，或在 loader 中封装判断。
- 规则：默认启用，命中 disabled namespace 才禁用。
- namespace 归一化逻辑与 `TableModelLoaderManagerImpl.buildFullName()` 保持一致。

验收：

- `tms-ai` 默认 semantic。
- `tms-biz` 配置到 disabled 后 physical。

### Step 3：模型加载期归一化

状态：todo

目标：

- 在 `TableModelLoaderManagerImpl.load(modelName, namespace)` 中处理 `DbModelDef`。
- physical namespace 下清空 property/measure 的 `semanticScaleFactor`。
- semantic namespace 下保持现有 `SemanticScaleSqlSupport.scaledDeclare()` 行为。

验收：

- query engine 不需要新增运行时分支。
- `DbPropertyImpl` 和 `DbMeasureSupport` 不需要接收上下文参数。

### Step 4：双 namespace 测试

状态：todo

目标：

- 同一套 TM/QM 文件注册成 semantic/physical 两个 namespace。
- 覆盖查询列、slice、having、calculatedFields、cache isolation。

建议用例：

| 用例 | 预期 |
|------|------|
| semantic namespace select | SQL 出现 `/ factor` |
| physical namespace select | SQL 不出现 `/ factor` |
| semantic namespace slice | 过滤值按元理解 |
| physical namespace slice | 过滤值按分理解 |
| calculatedFields | 两个 namespace 都不需要特殊上下文 |
| cache isolation | 两个 namespace 不串模型 |

### Step 5：入口文档与部署样例

状态：todo

目标：

- 文档说明 MCP/LLM 使用 semantic namespace。
- 业务系统如需物理单位，使用 physical namespace。
- `ExternalBundleProperties` 示例展示同一路径双注册。

---

## 不再执行

| 旧任务 | 处理 |
|--------|------|
| REST 入口注入 `restDefault` | cancelled |
| MCP 入口注入 `mcpDefault` | cancelled |
| `DbQueryRequestDef.applySemanticScale` | cancelled |
| `SqlExpContext` 或 ThreadLocal runtime 传递 | cancelled |
| slice/having 根据 runtime 开关反向转换 | cancelled |

---

## 当前状态

- Development: planned
- Testing: not-run
- Experience: N/A，纯后端模型加载策略，无 UI 交互

---

## 后续评审要求

实现完成后需要轻量 implementation check-in：

- 确认没有恢复 runtime `applySemanticScale`。
- 确认 namespace policy 只影响加载期。
- 确认测试覆盖 semantic/physical 两个 namespace。
- 确认 README 和部署样例同步。
