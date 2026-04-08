# P0-Odoo 本地模型注册中心消费契约 — Code Inventory

## 基本信息

- 目标版本：`8.1.10.beta`
- 上游需求：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-需求.md`
- 仓库：`foggy-data-mcp-bridge`

## 当前 Odoo 模型位置

Java 侧 Odoo TM/QM 当前位于 `addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/`，包含 14 TM + 14 QM，随 JAR 打包。

## Code Inventory

### Odoo 模型目录（现有）

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/`
- role: 当前 Odoo TM/QM 存放位置（随 JAR 打包）
- expected change: `update`
- notes: 后续由 pull 脚本从 registry 同步覆盖；需标记为 generated，禁止手改

### pull 脚本

- repo: `foggy-data-mcp-bridge`
- path: `scripts/pull-odoo-models.sh`
- role: 从 registry 拉取 Odoo bundle 的入口脚本
- expected change: `create`
- notes: 调用 `foggy-model-registry/scripts/pull.py` 或直接 HTTP 拉取；输出到 staging 目录；写 lock 文件

### lock 文件

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-odoo-bridge-java/models.lock.json`
- role: 锁定当前消费的 bundle 版本和 checksum
- expected change: `create`
- notes: 提交到 git；CI 校验此文件与实际模型目录一致

### CI 漂移校验脚本

- repo: `foggy-data-mcp-bridge`
- path: `scripts/check-model-drift.sh`
- role: CI 阶段校验模型目录与 lock 文件一致性
- expected change: `create`
- notes: 比对 lock 中的 checksum 与当前模型目录的实际 checksum；不一致时返回非零退出码

### Bundle 加载器（现有）

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/.../BundleLoader.java`
- role: 运行时加载 TM/QM 模型文件
- expected change: `read-only-analysis`
- notes: 确认从 classpath 或 external bundle 目录加载均兼容；registry pull 后的目录结构需与 BundleLoader 期望一致

### Namespace 配置（现有）

- repo: `foggy-data-mcp-bridge`
- path: 启动参数 `--spring.profiles.active=lite,odoo`
- role: Odoo namespace 激活
- expected change: `read-only-analysis`
- notes: 确认 odoo profile 的模型加载路径与 pull 后的 staging 目录兼容
