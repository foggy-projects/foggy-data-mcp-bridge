---
type: execution-prompt
version: 8.2.0.beta
milestone: M2
target_repo: foggy-data-mcp-bridge
target_module: foggy-dataset-model
req_id: M2-QueryPlan-Java
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: done
completed_at: 2026-04-21
python_reference_landed_at: 2026-04-21
python_baseline: 2564 passed / 1 skipped
java_baseline: foggy-dataset-model sqlite lane 1246 passed / 0 failures (M1 基线 1134 + 112 · M2 贡献 76 tests)
---

# Java M2 · `QueryPlan` 对象模型开工提示词

## 执行位置（读在最前）

- **实际工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- **逻辑仓**：`foggy-data-mcp-bridge`（Compose Query 分支最终会合回 mainline）
- **目前阶段**：8.2.0.beta 所有改动都只在上述 worktree 里；mainline `foggy-data-mcp-bridge/` 目录 HEAD 还**没有** `engine/compose/` 新包，也没有 `docs/8.2.0.beta/`
- **本文档里所有 `foggy-data-mcp-bridge/...` 形式的路径**，物理上都定位到 `foggy-data-mcp-bridge-wt-dev-compose/...`。例如：
  - `foggy-data-mcp-bridge/docs/8.2.0.beta/M1-AuthorityResolver-SPI签名冻结-需求.md`
    → `foggy-data-mcp-bridge-wt-dev-compose/docs/8.2.0.beta/M1-AuthorityResolver-SPI签名冻结-需求.md`
  - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/...`
    → `foggy-data-mcp-bridge-wt-dev-compose/foggy-dataset-model/src/main/java/...`
- **Maven 命令**在 worktree 根目录执行：`mvn test -pl foggy-dataset-model ...`
- **不要**往 mainline `foggy-data-mcp-bridge/` 目录写入 —— 否则会造成分支漂移

Python 侧参考实现位于 `foggy-data-mcp-bridge-python`（独立仓，非 worktree，路径如其字面）。Odoo Pro 同理。

## 角色与语境设定

你是 `foggy-data-mcp-bridge` 仓 `foggy-dataset-model` 模块的维护者。你要在 Java 侧把 `QueryPlan` 对象模型与最小 API 镜像 Python 已落地的 M2 —— 让两端形状 1:1 对齐，做到 M6 SQL 编译器和 M7 script runner 可以跨语言按同一套契约工作。

这次交付只做"关系节点树"的对象模型 + `from(...)` 入口 + 5 个 public 方法里 3 个纯构造方法（`query / union / join`）。`execute()` 和 `toSql()` 抛 `UnsupportedInM2Exception`，等 M6/M7 再接。

## 必读前置

严格按顺序读完再动手：

1. **主需求**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
   - 重点章节：`§用户语法`、`§核心语义`、`§union 规范`、`§join 规范`、`§典型示例`
2. **实现规划**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
   - 重点：`§QueryPlan API 规划` § `§Schema 与别名规则` § `§SQL 编译边界`（不实施，但决定对象模型的数据载体形状）
3. **代码清单**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-代码清单.md`
   - 重点：M2 相关类清单与建议路径
4. **沙箱脚手架**：`foggy-data-mcp-bridge/docs/8.2.0.beta/M9-三层沙箱防护测试脚手架.md`
   - 重点：`§Layer C Plan 动词白名单` —— 你要在本步保证只暴露 5 个方法
5. **Python 对等实现**（**这就是你要对齐的事实来源**）：
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/plan/plan.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/plan/dsl.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/plan/result.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/plan/__init__.py`
   - `foggy-data-mcp-bridge-python/tests/compose/plan/*`（5 个测试文件 · 73 tests 全绿 · 这些就是你的验收事实）
6. **上一步 M1 Java 落地范本**（同模块、同风格参考）：
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/context/Principal.java`
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/ModelBinding.java`
   - 对应测试：`.../test/java/.../engine/compose/**/*Test.java`

## 对齐原则（硬要求）

1. **Python 是本期事实来源**：当代码风格、字段名、边界条件在 Python 侧已定案（如 `JoinOn` 只允许 `{=, !=, <, >, <=, >=}`、union 不在 M2 做列数校验），Java 侧严格镜像
2. **Java 继续 M1 的 explicit Builder + final 风格**：不上 Lombok、不用 Java 17 Record（与 M1 九个类保持一致，reviewer 不用切换心智）
3. **JDK 17 可用**：用 `List.of` / `Set.of` / `Map.of`、`List.copyOf`、`Collections.unmodifiableList` 这套现代不可变集合 API
4. **只暴露 Layer-C 白名单的 5 个方法**：`query / union / join / execute / toSql`；任何其他 public 方法（raw、memoryFilter、toArray、forEach、items、rows…）不得出现在 `QueryPlan` 或任何子类的 public 面上
5. **不做 schema 推导 / SQL 编译**：这两项分别是 M4/M6 scope；本步把"shape 校验"能做的（pagination 非负、columns 非空、join on 非空、join type 白名单）全部做到位，把"需要上下文的检查"（字段是否来自 source.output_schema）留到 M4

## 交付清单

### 源码（9 个类，全部 public final）

包 `com.foggyframework.dataset.db.model.engine.compose.plan`：

```
plan/
├── QueryPlan.java                   abstract base · 5 public 方法 + baseModelPlans() protected
├── BaseModelPlan.java               leaf; reuses Python BaseModelPlan shape
├── DerivedQueryPlan.java            plan derived from another plan
├── UnionPlan.java                   left + right + allFlag
├── JoinPlan.java                    left + right + type + on[]
├── JoinOn.java                      {left, op, right} · op whitelist 6 个
├── JoinType.java                    enum {INNER, LEFT, RIGHT, FULL}
├── SqlPreview.java                  {sql, params} · toSql() 占位返回
├── UnsupportedInM2Exception.java    extends RuntimeException · 用于 execute/toSql
└── Dsl.java                         public static QueryPlan from(FromRequest req) 入口
```

`Dsl.java` 里定义 `public static QueryPlan from(Builder-style from-request)` 或 `from(FromOptions opts)` —— 用 Builder 链表达参数（Python 的 kwargs 在 Java 里对应 Builder 模式）。**不要**在 Java 里起名 `from_` —— Java 没有 `from` 关键字冲突，直接 `Dsl.from(FromOptions.builder()...build())` 即可。

> 小陷阱：如果日后宿主脚本也走 Java 层，`from` 在 Java 里作为标识符是合法的（不是保留字），没有必要加下划线。

### 测试（JUnit5 + `@DisplayName` 中文，镜像 Python 73 tests 至少一一对应）

包 `com.foggyframework.dataset.db.model.engine.compose.plan`（test 源码根）：

```
BaseModelPlanTest.java             ~10 tests · 构造/必填/pagination/frozen/hashcode/tree walk/execute-unsupported/isa-queryplan
DerivedQueryPlanTest.java          ~8 tests
UnionPlanTest.java                 ~10 tests
JoinPlanTest.java                  ~14 tests · 含 JoinOn op whitelist + type 大小写归一 + dict 入参等价（Java 里改成 Map 入参）
FromEntryTest.java                 ~12 tests · model/source 互斥 + 必填 + pagination + 可选字段透传 + 内核等价
PlanCompositionTest.java           ~6 tests · 3 个 spec 典型示例 + multi-level chain + Layer-C whitelist 硬断言 + base-model preorder
```

**硬指标**：Java 测试集合 ≥ 60 tests 全绿；可以适度合并（例如 Java 没有必要把"构造入参类型错误"拆成 N 个 method），但不得跨过 Python 测试集合里已覆盖的任何语义面。

### Layer-C 白名单硬断言

在 `PlanCompositionTest.java` 里写一条反射测试：

```java
@Test
@DisplayName("Layer-C: QueryPlan public 面只有 5 个方法，禁用面缺席")
void layerCWhitelistEnforced() {
    Set<String> allowed = Set.of("query", "union", "join", "execute", "toSql",
            // Object 继承 + builder 必要出口
            "equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll");
    Set<String> forbidden = Set.of("raw", "rawSql", "memoryFilter",
            "forEach", "items", "rows", "toArray", "iterator");

    for (Class<?> cls : List.of(BaseModelPlan.class, DerivedQueryPlan.class,
            UnionPlan.class, JoinPlan.class)) {
        for (Method m : cls.getMethods()) {
            String name = m.getName();
            assertFalse(forbidden.contains(name),
                    () -> cls.getSimpleName() + " 不得暴露 " + name
                            + "（Layer-C 白名单违规）");
        }
    }
}
```

## 数据契约速查表（与 Python 对齐）

| 概念 | Java | Python |
|------|------|--------|
| 列集合 | `List<String> columns` · unmodifiable copy | `Tuple[str, ...]` · frozen via tuple |
| slice | `List<Object> slice`（dict-like，复用 Map/Pojo 自行定义） | `Tuple[Any, ...]`（现阶段 list of dict） |
| group_by / order_by | `List<String>` · unmodifiable · 可空 list | `Tuple[str, ...]` · 可空 tuple |
| limit / start | `Integer`（可 null）· 非负 | `Optional[int]` · 非负 |
| distinct | `boolean` · 默认 false | `bool` · 默认 False |
| JoinOn | final class with Builder `{String left, String op, String right}` | frozen dataclass |
| JoinOn.op whitelist | `=`, `!=`, `<`, `>`, `<=`, `>=`（Python 6 个） | 6 个，一致 |
| JoinPlan.type | 枚举 `JoinType.{INNER, LEFT, RIGHT, FULL}`；public API 接受字符串再归一 | str "inner/left/right/full" |
| `from(...)` 互斥 | 传 `model` 构造 `BaseModelPlan`；传 `source` 构造 `DerivedQueryPlan`；两者都传或都不传 → `IllegalArgumentException` | 同，抛 `ValueError` |
| `query(...)` | 方法链糖 · 返回 `DerivedQueryPlan` | 同 |
| `union(other, all)` | `boolean all` 默认 false | `bool` 默认 False |
| `join(other, type, on)` | `JoinType type` · `List<JoinOn> on` · on 非空 | 同 |
| `execute()` | 抛 `UnsupportedInM2Exception extends RuntimeException` 并引用 M6/M7 | 抛 `UnsupportedInM2Error extends NotImplementedError` |
| `toSql()` | 同上 | 同上 |
| `baseModelPlans()` | protected/package-private · 返回 `List<BaseModelPlan>` · 左-右 preorder | 同 |

## 非目标（禁止做）

- 不做 SQL 字符串生成（M6）
- 不做字段 vs source output schema 校验（M4）
- 不做 `union` 双侧列数/类型一致性（M4）
- 不做 `join` 引用字段可见性（M4）
- 不在 Plan 对象上存任何与具体 `QueryModel`/`JoinGraph` 相关的运行时对象 —— 只存 QM 名字符串 `String model`
- 不暴露 ComposeQueryContext / AuthorityResolver 给 Plan —— 这是 M7 script runner 把 ctx 显式传进 `execute(ctx)` 的职责

## 验收硬门槛

1. `mvn test -pl foggy-dataset-model -Dtest='*Plan*Test,FromEntryTest,JoinOnTest,PlanCompositionTest'` 全绿
2. `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db` 全绿（从 1134 基线推进；期望 1134 + 60+）
3. Layer-C 白名单硬断言通过
4. 新增类全部 `final` · 所有集合字段 unmodifiable copy · 所有构造经过 Builder · 全部 `toString` 不泄漏 `authorizationHint`（M1 已立规）
5. 本提示词完成后把 8.2.0.beta progress.md 的 M2 行 `python-ready-for-review / java-pending` 更新为 `ready-for-review`，追加 Java 测试基线数字

## 典型代码片段（Java 对齐 Python）

`BaseModelPlan.java` 骨架（作为参考，具体细节请按 M1 Java 代码同风格完善）：

```java
package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.*;

public final class BaseModelPlan extends QueryPlan {
    private final String model;
    private final List<String> columns;
    private final List<Object> slice;
    private final List<String> groupBy;
    private final List<String> orderBy;
    private final Integer limit;
    private final Integer start;
    private final boolean distinct;

    private BaseModelPlan(Builder b) {
        if (b.model == null || b.model.isEmpty())
            throw new IllegalArgumentException("BaseModelPlan.model must be non-empty");
        validateColumns(b.columns, "BaseModelPlan.columns");
        validatePagination(b.limit, b.start, "BaseModelPlan");

        this.model = b.model;
        this.columns = List.copyOf(b.columns);
        this.slice = b.slice == null ? List.of() : List.copyOf(b.slice);
        this.groupBy = b.groupBy == null ? List.of() : List.copyOf(b.groupBy);
        this.orderBy = b.orderBy == null ? List.of() : List.copyOf(b.orderBy);
        this.limit = b.limit;
        this.start = b.start;
        this.distinct = b.distinct;
    }

    public String model() { return model; }
    public List<String> columns() { return columns; }
    // … 其余 accessor

    @Override
    public List<BaseModelPlan> baseModelPlans() { return List.of(this); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String model;
        private List<String> columns;
        private List<Object> slice;
        private List<String> groupBy;
        private List<String> orderBy;
        private Integer limit;
        private Integer start;
        private boolean distinct;

        public Builder model(String v) { this.model = v; return this; }
        public Builder columns(List<String> v) { this.columns = v; return this; }
        // … 其余 setter 链
        public BaseModelPlan build() { return new BaseModelPlan(this); }
    }
}
```

## 预估规模

- 源码：9 个类 · ~700 LOC（含 Builder 样板）
- 测试：~500 LOC · 60+ tests
- 全部工作量约 1 人日

## 停止条件

- Python 侧任何类型被发现与本提示词描述不符 → 以 Python 源码为准修正提示词；不要直接把 Java 写成"有小差异"
- `mvn test` 全回归有回归（任何现有测试从绿转红）→ 立即停 · 不提交 PR
- Layer-C 白名单硬断言任何一条失败 → 立即停，修好再继续

## 完成后需要更新的文档

1. `docs/8.2.0.beta/P0-ComposeQuery-*-progress.md` 里 M2 行：`ready-for-review`；`变更日志` 追加 "M2 Java 侧 QueryPlan 对象模型落地：xxx tests 全绿，`foggy-dataset-model` 基线从 1134 → 1134+N"
2. 本提示词 `status: ready-to-execute` → `status: done` 并填写完成日期与测试基线
3. root CLAUDE.md 的 "Compose Query M1 SPI 落地" 小节之后新增 "Compose Query M2 QueryPlan 对象模型" 小节
