# foggy-benchmark-spider2

> **WIP (Work In Progress)** - 此模块尚未完成，暂不包含在开源发布中。

基于 [Spider2-Lite](https://github.com/xlang-ai/Spider2) 数据集的 MCP 工具基准测试模块。

## 当前状态

此模块用于验证 Foggy Framework 的 AI 查询能力，但由于以下原因暂未完成：

- Spider2 数据库需要人工理解业务语义后才能建立准确的 TM/QM 模型
- 自动生成的模型缺乏足够的业务语义描述
- 需要更多时间进行数据分析和模型优化

## 功能（计划中）

- 使用 Spider2 SQLite 数据库进行 TM/QM 建模
- 使用 Spider2 自然语言问题测试 MCP 工具
- 支持多 AI 模型对比评估
- 生成标准化测试报告

## 快速开始

### 1. 下载 Spider2 数据

```bash
# 克隆 Spider2 仓库
git clone https://github.com/xlang-ai/Spider2.git D:/foggy-projects/Spider2

# 下载 SQLite 数据库（从 Google Drive）
# https://drive.google.com/file/d/1coEVsCZq-Xvj9p2TnhBFoFTsY-UoYGmG
# 解压到 Spider2/spider2-lite/resource/databases/spider2-localdb/
```

### 2. 配置环境变量

```bash
# 阿里云通义千问
export DASHSCOPE_API_KEY=your-api-key

# 或 OpenAI
export OPENAI_API_KEY=your-api-key
```

### 3. 修改配置（可选）

编辑 `src/main/resources/application.yml`：

```yaml
spider2:
  # Spider2 数据路径
  jsonl-path: D:/foggy-projects/Spider2/spider2-lite/spider2-lite.jsonl
  database-base-path: D:/foggy-projects/Spider2/spider2-lite/resource/databases/spider2-localdb

  # 限制测试数量
  max-test-cases: 100

  # 只测试特定数据库
  enabled-databases:
    - E_commerce
    - california_schools
```

### 4. 运行测试

```bash
cd foggy-benchmark-spider2

# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=Spider2BenchmarkTest

# 跳过 AI 测试（只测试数据加载）
mvn test -Dtest=Spider2BenchmarkTest#checkSpider2Configuration
```

## 模块结构

```
foggy-benchmark-spider2/
├── src/main/java/com/foggyframework/benchmark/spider2/
│   ├── Spider2BenchmarkApplication.java    # 启动类
│   ├── config/
│   │   ├── Spider2Properties.java          # 配置属性
│   │   └── Spider2DataSourceConfig.java    # SQLite 数据源
│   ├── loader/
│   │   ├── Spider2TestCaseLoader.java      # 测试用例加载
│   │   └── Spider2DatabaseInspector.java   # 数据库结构探测
│   ├── executor/
│   │   └── BenchmarkExecutor.java          # 测试执行器
│   ├── evaluator/
│   │   ├── ResultEvaluator.java            # 结果评估
│   │   └── ReportGenerator.java            # 报告生成
│   └── model/
│       ├── Spider2TestCase.java            # 测试用例模型
│       ├── BenchmarkResult.java            # 测试结果模型
│       └── EvaluationReport.java           # 评估报告模型
├── src/main/resources/
│   └── application.yml                     # 配置文件
└── src/test/
    ├── java/.../Spider2BenchmarkTest.java  # 基准测试类
    └── resources/application-test.yml      # 测试配置
```

## 测试报告

运行测试后，报告生成在 `target/reports/spider2-benchmark-report.md`。

报告内容包括：
- 概览统计（测试数、成功率、耗时）
- 模型对比（按 AI 模型分组）
- 数据库统计（按数据库分组）
- 失败用例详情

## Spider2 数据集说明

Spider2-Lite 包含约 500+ 自然语言问题，覆盖：
- SQLite 本地数据库 (local*)
- BigQuery 云数据库 (bq*)
- Snowflake 云数据库 (sf_*)

本模块只使用 SQLite 数据库进行测试。

## 后续计划

- [ ] 为常用数据库创建 TM/QM 模型
- [ ] 集成 MCP 工具调用（而非直接 AI 调用）
- [ ] 添加结果准确性评估
- [ ] 支持多模型并行测试
