---
doc_role: rollout-playbook
version: 8.3.0.beta
target: G10 · `foggy.compose.g10.enabled` 默认值翻转
status: draft
owner: Compose Engine 维护者
created_at: 2026-04-27
---

# G10 Flag-Flip Rollout Playbook

> **范围**：将 `foggy.compose.g10.enabled` / `FOGGY_COMPOSE_G10_ENABLED` 从默认 `false` 翻转到默认 `true`，把 G10 plan-aware 路径变成生产默认路径。
>
> **前置**：G10 acceptance signed off（`accepted-with-risks`）+ FU-1（G5 Phase 2 集成测试 ≥3+≥2）+ FU-2（lane 矩阵 sweep）全部完成。

## 0. 决策门：何时启动本 playbook？

**必须满足以下全部 4 个条件**才进入翻转流程：

- [ ] **C1 G5 Phase 2 落地** · F5 plan-qualified columns 入口已上线，公共 `validate(plan, schema, ctx)` 路径**真实可达**（非 dead code path）
- [ ] **C2 集成测试 FU-1 已落** · ≥3 plan-aware 编译 + ≥2 plan-routed 权限的真实 SQL 数据比对集成测试在双仓**已绿**（spec §9.6 + §9.7）
- [ ] **C3 lane 矩阵 sweep FU-2 已跑** · `flag=true` lane 单次 sweep 与默认 lane 的 diff 已审计（应为零功能差异 / 只有 G10 路径独占用例多出的部分）
- [ ] **C4 灰度观察期** · 至少 1 个工作周内部环境运行 `flag=true`（通过启动参数显式 override），无 critical 缺陷上报

C1-C4 任一未满足 → playbook 暂缓启动；继续维持 flag 默认 `false`。

## 1. 翻转范围

### 1.1 双仓默认值定义点

| 仓 | 默认值定义点 |
|----|-------------|
| Java | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/ComposeFeatureFlags.java` · 默认值常量 + `parseBool` 解析逻辑 |
| Python | `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/feature_flags.py` · 默认值常量 + 环境变量解析 |

### 1.2 翻转**不涉及**的对象

- Spec v2 中"不在 G10 范围"的 5 项（§8）保持现状
- `ComposePlanAwarePermissionValidator` 公共入口的进一步演进（已是 G5 Phase 2 范畴）
- `FieldAccessPermissionStep`（v2 patch 已确认不修改）
- 单 BaseModelPlan 路径（在 flag-on 下走 G10 fallback，行为与今日一致）

## 2. 翻转步骤（按顺序执行）

### Step 1 · 准备（无代码变动）

- [ ] 确认 §0 决策门 C1-C4 全绿
- [ ] 通知下游消费方（Odoo Pro v1.6 REQ-001 owner / `foggy-odoo-bridge-pro` 维护者 / Python embedded path 维护者），约定翻转日期
- [ ] 在 worktree 创建翻转分支：`flag-flip/g10-default-true`

### Step 2 · Java 翻转（PR · 单文件修改）

- [ ] 修改 `ComposeFeatureFlags.java` 默认值常量：
  ```java
  // 翻转前
  private static final boolean DEFAULT_G10_ENABLED = false;
  // 翻转后
  private static final boolean DEFAULT_G10_ENABLED = true;
  ```
- [ ] 修改 `application.yml` / `application-default.yml` 中**显式** `foggy.compose.g10.enabled` 配置（如有）从 `false` 改为 `true`，或直接删除让默认生效
- [ ] 测试：
  ```bash
  mvn test -pl foggy-dataset-model -P!multi-db
  # 期望：1809+ passed（G10 路径默认开启后单元基线不应回退）
  ```
- [ ] 若任何测试失败，**禁止** PR 合并 → 回到 §0 排查

### Step 3 · Python 翻转（PR · 单文件修改）

- [ ] 修改 `feature_flags.py` 默认值常量从 `False` 翻到 `True`
- [ ] 测试：
  ```bash
  pytest -q
  # 期望：3176+ passed
  ```
- [ ] 同样：失败禁止合并

### Step 4 · 双仓 lane sweep（验证）

- [ ] Java：在主分支重新跑 sqlite lane（`flag=true` 现为默认）
- [ ] Python：在主分支重新跑 `pytest -q`
- [ ] 显式 override `flag=false` 跑反向 sweep，确认 legacy 路径仍能被外部 override：
  ```bash
  mvn test -pl foggy-dataset-model -P!multi-db -Dfoggy.compose.g10.enabled=false
  FOGGY_COMPOSE_G10_ENABLED=false pytest -q
  ```
  → 期望：完全绿；意味着 flag-off path 仍是 zero-cost legacy 短路
- [ ] 任一红 → 立刻 revert 翻转 PR

### Step 5 · 下游告知（默认行为变化）

- [ ] 在 `docs/8.3.0.beta/CHANGELOG.md`（如不存在则创建）记录：
  ```
  ## 8.3.0.beta · G10 default flip · 2026-XX-XX
  - foggy.compose.g10.enabled 默认值 false → true
  - 影响：多 plan compose 场景的 join 输出从直接 fail 变为携带歧义列；下游引用未消歧时由编译 / 权限层 fail-fast 给出新错误码
  - 回退方式：通过启动参数 -Dfoggy.compose.g10.enabled=false 或环境变量恢复 legacy 行为
  ```
- [ ] 同步更新：
  - `docs-site/zh/dataset-model/compose-query/dsl-manual.md`（Manual A）
  - `docs-site/zh/dataset-model/compose-query/api-manual.md`（Manual B）
  - 把 G2 / G5 / G11 / G12 spec 中"🚧 G10 落地前"的占位段去除
- [ ] 同步更新 `compose-query-manuals-gap-tracker.md`：G10 行 status `implemented · ready-with-gaps` → `closed`，Closure log 追加一行

### Step 6 · 监控期（翻转后 7 个工作日）

- [ ] 监控指标：
  - 新增错误码 `JOIN_AMBIGUOUS_COLUMN` / `OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP` / `COLUMN_PLAN_NOT_BOUND` / `FIELD_ACCESS_DENIED`（plan-routed 路径）的发生频率
  - `ComposePlanner.compileExpression` 编译延迟 vs 翻转前基线
  - Validator `validate(plan, schema, ctx)` 调用频率
- [ ] 任一异常上升 → 立刻 hotfix 或 revert
- [ ] 7 日无异常 → 翻转**正式 done**，后续不再保留 legacy fallback 的 deprecation 计划由独立 issue 追踪

## 3. 回退步骤（出现回归）

### 3.1 立即回退（hotfix）

```bash
# Java
git revert <flag-flip-commit-hash>
mvn install -pl foggy-dataset-model -DskipTests=false

# Python
git revert <flag-flip-commit-hash>
pytest -q
```

### 3.2 环境变量临时禁用（无需重新部署）

- Java：`-Dfoggy.compose.g10.enabled=false` 启动参数
- Python：`FOGGY_COMPOSE_G10_ENABLED=false` 环境变量
- 进程重启后生效

### 3.3 回退后处理

- 在 worktree 创建 hotfix 分支记录 root cause
- 在 G10 acceptance doc 的 Risks 章节补 incident 行
- 触发新一轮 §0 决策门评估

## 4. Risks · 翻转期已知风险

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| 单 BaseModelPlan 路径在 flag-on 下走 fallback 时性能微降 | 低 | 编译额外 alias map 查找 O(1) | 单元测试覆盖；监控期跟踪延迟 |
| 既有错误码 `JOIN_OUTPUT_COLUMN_CONFLICT` 不再触发 → 下游消费方依赖此错误码做特殊处理时失效 | 低 | 行为变化 | 翻转前全文搜索 `JOIN_OUTPUT_COLUMN_CONFLICT` 字面引用；通知下游迁移到 `JOIN_AMBIGUOUS_COLUMN` |
| Python 端 `PlanColumnRef` 已在 `select()` 时扁平化，flag-on 下 SQL 形态可能与 Java 不一致 | 中 | 跨端 parity 缺口 | G5 Phase 2 落地后通过双端 column_normalizer 对齐；翻转前不应有此 case 进入生产 |
| 用户 catch `JOIN_OUTPUT_COLUMN_CONFLICT` 但未 catch `JOIN_AMBIGUOUS_COLUMN` | 低 | 异常未被捕获 | CHANGELOG 显式说明；提供 1 个版本的过渡期同时抛两个错误码（如有需求） |

## 5. 后续清理（翻转 +30 天）

- [ ] 评估是否删除 `g10_enabled` flag（保留若至少 1 个外部消费方仍依赖 override 能力）
- [ ] 评估是否删除 `withSourceModelCleared` legacy 路径（依赖 flag 删除）
- [ ] G10 spec v2 status 从 `Draft v2 for review` → `Final · superseded by implementation`

## 6. 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-27 | 创建 draft | G10 acceptance ready-for-signoff 同批产出；C1-C4 决策门均未满足，playbook 当前不可执行 |
