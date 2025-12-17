# foggy-benchmark-spider2

[中文文档](README.zh-CN.md)

> **WIP (Work In Progress)** - This module is not yet complete and is not included in the open source release.

MCP tool benchmark module based on the [Spider2-Lite](https://github.com/xlang-ai/Spider2) dataset.

## Current Status

This module is for validating Foggy Framework's AI query capabilities, but is incomplete due to:

- Spider2 databases require manual understanding of business semantics to build accurate JM/QM models
- Auto-generated models lack sufficient business semantic descriptions
- More time needed for data analysis and model optimization

## Features (Planned)

- Use Spider2 SQLite databases for JM/QM modeling
- Test MCP tools with Spider2 natural language questions
- Support multi-AI model comparison evaluation
- Generate standardized test reports

## Quick Start

### 1. Download Spider2 Data

```bash
# Clone Spider2 repository
git clone https://github.com/xlang-ai/Spider2.git D:/foggy-projects/Spider2

# Download SQLite databases (from Google Drive)
# https://drive.google.com/file/d/1coEVsCZq-Xvj9p2TnhBFoFTsY-UoYGmG
# Extract to Spider2/spider2-lite/resource/databases/spider2-localdb/
```

### 2. Configure Environment Variables

```bash
# Alibaba Cloud Qwen
export DASHSCOPE_API_KEY=your-api-key

# Or OpenAI
export OPENAI_API_KEY=your-api-key
```

### 3. Modify Configuration (Optional)

Edit `src/main/resources/application.yml`:

```yaml
spider2:
  # Spider2 data paths
  jsonl-path: D:/foggy-projects/Spider2/spider2-lite/spider2-lite.jsonl
  database-base-path: D:/foggy-projects/Spider2/spider2-lite/resource/databases/spider2-localdb

  # Limit test cases
  max-test-cases: 100

  # Test specific databases only
  enabled-databases:
    - E_commerce
    - california_schools
```

### 4. Run Tests

```bash
cd foggy-benchmark-spider2

# Run all tests
mvn test

# Run specific test
mvn test -Dtest=Spider2BenchmarkTest

# Skip AI tests (test data loading only)
mvn test -Dtest=Spider2BenchmarkTest#checkSpider2Configuration
```

## Module Structure

```
foggy-benchmark-spider2/
├── src/main/java/com/foggyframework/benchmark/spider2/
│   ├── Spider2BenchmarkApplication.java    # Main class
│   ├── config/
│   │   ├── Spider2Properties.java          # Configuration properties
│   │   └── Spider2DataSourceConfig.java    # SQLite data source
│   ├── loader/
│   │   ├── Spider2TestCaseLoader.java      # Test case loader
│   │   └── Spider2DatabaseInspector.java   # Database structure inspector
│   ├── executor/
│   │   └── BenchmarkExecutor.java          # Test executor
│   ├── evaluator/
│   │   ├── ResultEvaluator.java            # Result evaluator
│   │   └── ReportGenerator.java            # Report generator
│   └── model/
│       ├── Spider2TestCase.java            # Test case model
│       ├── BenchmarkResult.java            # Test result model
│       └── EvaluationReport.java           # Evaluation report model
├── src/main/resources/
│   └── application.yml                     # Configuration file
└── src/test/
    ├── java/.../Spider2BenchmarkTest.java  # Benchmark test class
    └── resources/application-test.yml      # Test configuration
```

## Test Reports

After running tests, reports are generated at `target/reports/spider2-benchmark-report.md`.

Report contents include:
- Overview statistics (test count, success rate, duration)
- Model comparison (grouped by AI model)
- Database statistics (grouped by database)
- Failed case details

## Spider2 Dataset Description

Spider2-Lite contains 500+ natural language questions covering:
- SQLite local databases (local*)
- BigQuery cloud databases (bq*)
- Snowflake cloud databases (sf_*)

This module only tests with SQLite databases.

## Future Plans

- [ ] Create JM/QM models for common databases
- [ ] Integrate MCP tool calls (instead of direct AI calls)
- [ ] Add result accuracy evaluation
- [ ] Support parallel multi-model testing
