# P0-Odoo 本地模型注册中心消费契约 — Progress

## 基本信息

- 目标版本：`8.1.10.beta`
- 需求等级：`P0`
- 状态：`已完成`
- 责任项目：`foggy-data-mcp-bridge`
- 上游需求：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-需求.md`
- 实施计划：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-implementation-plan.md`
- 完成日期：2026-04-04
- 审阅方式：产物验证（子 agent 执行时无 progress 模板，由审阅者根据产物补写）

## 前置条件检查

| 前置条件 | 状态 |
|----------|------|
| `foggy-model-registry` Stage 1 完成 | ✅ |
| `foggy-odoo-bridge-pro` Stage 2 完成（model-manifest.json 已确认） | ✅ |
| registry 已发布至少一个 community bundle | ✅ |

## Development Progress

### Step 1. 创建 pull 脚本 ✅

- 状态：已完成
- 脚本路径：`scripts/pull-odoo-models.sh`
- 输出目录：`addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/`

### Step 2. 生成初始 lock 文件 ✅

- 状态：已完成
- lock 路径：`addons/foggy-odoo-bridge-java/models.lock.json`
- lock 内容：registry + package `foggy.odoo.community` + version `1.1.0` + sha256 checksum + content_checksum

### Step 3. 创建 CI 漂移校验脚本 ✅

- 状态：已完成
- 脚本路径：`scripts/check-model-drift.sh`

### Step 4. 添加 GENERATED 标记 ✅

- 状态：已完成（`GENERATED.md` 已存在于模型目录）

### Step 5. 兼容性确认 ✅

- 状态：已确认
- BundleLoader 兼容：目录结构未变
- odoo profile 兼容：加载路径不受影响

## 计划外变更

- lock 文件增加 `content_checksum` 字段（bundle checksum 之外增加目录内容 checksum）

## Testing Progress

| 用例 | 结果 |
|------|------|
| pull community bundle 成功 | ✅ |
| models.lock.json 格式正确 | ✅ |
| GENERATED.md 存在 | ✅ |

## Experience Progress

- 当前状态：`N/A`

## 需求验收标准对照

| 验收标准 | 状态 |
|----------|------|
| Java 侧能通过 lock 文件拉取 community/pro bundle | ✅ |
| lock 文件与本地模型目录不一致时 CI 明确失败 | ✅ check-model-drift.sh |
| Java 侧不再依赖手工同步模型目录 | ✅ GENERATED 标记 |
| Java 侧兼容现有测试和启动脚本 | ✅ 目录结构未变 |

## 阻塞项

无。

## 后续衔接

| 后续项 | 状态 |
|--------|------|
| lock 文件已提交到 git | ✅ |
| GENERATED 标记已添加 | ✅ |
| 漂移校验可集成到 CI | ✅ |
