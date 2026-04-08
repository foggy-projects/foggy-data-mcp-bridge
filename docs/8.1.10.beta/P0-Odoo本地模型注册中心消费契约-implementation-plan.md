# P0-Odoo 本地模型注册中心消费契约 — Implementation Plan

## 基本信息

- 目标版本：`8.1.10.beta`
- 上游需求：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-需求.md`
- 代码清单：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-code-inventory.md`
- 仓库：`foggy-data-mcp-bridge`

## 前置条件

- `foggy-model-registry` Stage 1 已完成（publish / pull 可用）
- `foggy-odoo-bridge-pro` Stage 2 已完成（model-manifest.json 已确认）
- registry 已发布至少一个 community bundle

## 实施步骤

### Step 1. 创建 pull 脚本

在 `scripts/pull-odoo-models.sh` 创建拉取入口：

1. 读取 `addons/foggy-odoo-bridge-java/models.lock.json`（如存在）
2. 调用 registry pull（本地模式或 HTTP 模式）
3. 将 bundle 解包到 `addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/`
4. 更新 lock 文件

支持参数：
- `--registry <url-or-path>`（默认 `../foggy-model-registry/data`）
- `--channel <stable|beta>`（默认 `stable`）
- `--edition <community|pro>`
- `--key <value>`（pro 时必需）

验收：运行后模型目录内容与 registry bundle 一致，lock 文件已更新。

### Step 2. 创建 lock 文件初始版本

手动运行一次 pull，生成首个 `models.lock.json`，提交到 git。

验收：`models.lock.json` 存在且内容完整（registry / package / version / checksum）。

### Step 3. 创建 CI 漂移校验脚本

在 `scripts/check-model-drift.sh` 创建校验入口：

1. 读取 `models.lock.json` 中的 checksum
2. 对当前模型目录计算实际 sha256（与 registry publish 使用相同算法）
3. 比对两者；不一致时输出差异信息并退出非零

验收：
- 模型未修改时校验通过
- 手动修改任意 TM/QM 后校验失败

### Step 4. 标记模型目录为 generated

在 `addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/` 下添加 `GENERATED.md`：

```
本目录由 foggy-model-registry 同步生成，禁止手工修改。
使用 scripts/pull-odoo-models.sh 更新。
```

验收：文件存在。

### Step 5. 兼容性确认

只读分析确认：

1. BundleLoader 从 classpath 加载 `foggy/templates/odoo/` 下的 TM/QM — 目录结构不变，无需修改
2. `odoo` profile 的模型注册路径兼容
3. 现有测试和启动脚本不受影响

验收：`mvn clean package -pl foggy-mcp-launcher -am -DskipTests` 构建成功。

## 不做的事

- 不修改 BundleLoader 加载逻辑
- 不修改 Namespace 机制
- 不在 CI 中自动 pull（只做漂移校验）
- 不删除现有模型文件（由 pull 脚本覆盖管理）

## 预估工作量

| Step | 预估 | 说明 |
|------|------|------|
| 1. pull 脚本 | 30 min | shell 脚本，调用 registry pull |
| 2. 初始 lock | 5 min | 运行一次 pull |
| 3. 漂移校验 | 20 min | checksum 比对 |
| 4. GENERATED 标记 | 5 min | 文档 |
| 5. 兼容确认 | 15 min | 只读分析 + 构建验证 |
| **合计** | **~1.5h** | |
