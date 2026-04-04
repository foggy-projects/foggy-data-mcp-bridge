# P0-Odoo 本地模型注册中心消费契约-需求

## 基本信息

- 目标版本：`8.1.10.beta`
- 需求等级：`P0`
- 状态：`待处理`
- 责任项目：`foggy-data-mcp-bridge`

## 背景

Odoo TM/QM 模型的权威来源后续将收口到独立 private authority 仓，并先通过 workspace 内的独立模块 `foggy-model-registry` 向消费仓分发。

Java 侧当前仍存在以下治理风险：

- Odoo 模型目录存在仓内副本
- 模型更新依赖手工同步
- release / 测试环境容易与真实 authority 漂移

本阶段不要求 Java 侧参与商城、计费或对外服务，只要求 Java 侧遵守统一消费契约。

## 问题定义

Java 侧当前真正需要解决的不是“从哪里拷贝模型”，而是“如何只消费一个明确版本的 bundle，并让 CI 能发现漂移”。

如果继续接受：

- 人工复制目录
- 可变 `latest`
- 无 lock 文件的临时目录

那么 authority 仓即使建立起来，也无法真正成为唯一来源。

## 目标

- Java 只消费 registry 发布的 bundle
- Java 使用 lock 文件固定版本与 checksum
- Java CI 能校验当前模型目录与 lock 指向内容一致
- Java 不再要求开发者手工维护 Odoo 模型副本

## 最小消费契约

### 1. 输入

Java 侧只认以下四类信息：

- `registry`
- `package`
- `version`
- `checksum`

允许 `channel` 作为人类友好入口，但一旦执行拉取，必须先解析成具体 `version`，再写入 lock。

### 2. lock 文件

```json
{
  "registry": "http://127.0.0.1:9401",
  "package": "foggy.odoo.community",
  "version": "1.1.0",
  "checksum": "sha256:..."
}
```

### 3. 拉取规则

- Java 不直接依赖可变 `latest`
- Java 不直接从 authority 源目录读取模型
- Java 不直接从 `foggy-odoo-bridge-pro` 目录拷贝模型
- Java 构建或测试前通过统一脚本拉取 bundle 到 staging 目录

### 4. staging 规则

允许存在本地 staging 模型目录，但它必须满足：

- 由脚本生成
- 可被清理和重建
- 内容受 lock 文件约束

## 任务拆分

### 1. Java 消费入口

- 增加 `pull-models` 或等效脚本入口
- 支持从 lock 文件拉取并解包
- 为现有测试 / 启动脚本提供统一模型目录参数
- 统一面向 `foggy-model-registry` 消费，而不是面向 Odoo 仓目录

### 2. Java CI

- 增加 lock 校验
- 增加 checksum 校验
- 增加“工作区模型目录是否与 lock 一致”的漂移检查

### 3. Java 目录治理

- 仓内若保留 staging 副本，必须明确为 generated，不允许手改
- 后续优先从仓内源码副本迁移到 release / runtime staging

## 验收标准

- Java 侧能够通过 lock 文件拉取 Odoo community/pro bundle
- lock 文件与本地模型目录不一致时，CI 明确失败
- Java 侧不再依赖“提交前手工同步模型目录”
- Java 侧能继续兼容现有测试和启动脚本，不要求一次性重做所有入口

## 非目标

- 本条不负责 authority 仓拆分本身
- 本条不负责 key 的签发策略
- 本条不要求 Java 现在支持对外 registry 服务

## 关联文档

- Odoo：`foggy-odoo-bridge-pro/docs/prompts/v1.1/P0-07-local-model-registry-min-spec.md`
- Python：`foggy-data-mcp-bridge-python/docs/v1.0/P0-Odoo本地模型注册中心消费契约-需求.md`

## 跟踪维度

- 开发进度：`待开始`
- 测试进度：`待开始`
- 体验进度：`N/A`
