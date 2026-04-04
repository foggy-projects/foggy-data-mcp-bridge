# P0-Odoo 本地模型注册中心消费契约 — Execution Prompt

## 基本信息

- 目标版本：`8.1.10.beta`
- 上游文档：
  - 需求：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-需求.md`
  - 代码清单：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-code-inventory.md`
  - 实施计划：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-implementation-plan.md`
- 进度报告模板：`docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-progress.md`

## 开工提示词

你现在负责在 `foggy-data-mcp-bridge` 仓库中实现 Java 侧的 registry 消费契约。

### 你需要先读的文档

1. `docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-需求.md` — 目标和验收标准
2. `docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-code-inventory.md` — 代码触点
3. `docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-implementation-plan.md` — 步骤和顺序

### 你需要做的事

按 implementation plan 的 Step 1-5 顺序执行：

1. **创建 `scripts/pull-odoo-models.sh`**：从 registry 拉取 bundle 到模型目录
   - 默认 registry 路径：`../foggy-model-registry/data`
   - 解包到 `addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/`
   - 写 `models.lock.json`
   - 支持 `--edition community|pro`、`--channel stable|beta`、`--key <value>`

2. **生成初始 lock 文件**：运行一次 pull，提交 `models.lock.json`

3. **创建 `scripts/check-model-drift.sh`**：比对 lock checksum 与实际目录 checksum
   - 使用与 registry publish 相同的 sha256 算法
   - 不一致时输出差异并退出非零

4. **添加 GENERATED 标记**：在模型目录下添加 `GENERATED.md`

5. **兼容性确认**：确认 BundleLoader 和 odoo profile 加载路径兼容

### 你不需要做的事

- 不修改 BundleLoader 加载逻辑
- 不修改 Namespace 机制
- 不在 CI 中自动 pull
- 不在 commit 钩子中自动拉取

### 验收方式

```bash
# 1. 从 registry 拉取 community bundle
bash scripts/pull-odoo-models.sh \
  --registry ../foggy-model-registry/data \
  --channel stable \
  --edition community

# 2. 确认 lock 文件
cat addons/foggy-odoo-bridge-java/models.lock.json

# 3. 漂移校验 — 未修改时应通过
bash scripts/check-model-drift.sh

# 4. 手动改一个 TM 文件 → 漂移校验应失败
echo "# test" >> addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/model/OdooSaleOrderModel.tm
bash scripts/check-model-drift.sh
# 期望：exit 1

# 5. 恢复后构建验证
git checkout -- addons/foggy-odoo-bridge-java/src/main/resources/foggy/templates/odoo/model/OdooSaleOrderModel.tm
mvn clean package -pl foggy-mcp-launcher -am -DskipTests
```

### 执行完成后

创建 `docs/8.1.10.beta/P0-Odoo本地模型注册中心消费契约-progress.md`，按模板格式填写完成状态。
