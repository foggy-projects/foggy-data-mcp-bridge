---
name: qm-validate
description: 验证 TM/QM 模型文件是否正确。需要后端服务运行中。当用户生成模型后需要验证、或使用 /qm-validate 时使用。
---

# QM Validate

验证项目中的 TM/QM 模型文件，通过后端验证接口检查语法和语义正确性。

## 使用场景

- 使用 `qm-generate` 或 `tm-generate` 生成模型后验证正确性
- 手动编写/修改模型文件后检查是否有错误
- 排查模型加载失败的原因

## 执行流程

### Step 1: 确定服务地址

读取项目中的 `application.yml` / `application-*.yml`，查找 `server.port` 配置。

- 如果找到 → 使用 `http://localhost:{port}`
- 如果未找到 → 默认使用 `http://localhost:7108`

### Step 2: 检查服务是否运行

```bash
curl -s http://localhost:{port}/actuator/health
```

- 如果返回 `{"status":"UP"}` → 跳到 Step 4
- 如果连接失败 → 进入 Step 3

### Step 3: 服务未运行 → 引导启动

1. 检查项目 CLAUDE.md 中是否记录了启动命令
2. 如果没有：
   - 查找启动脚本（`*.sh`、`*.bat`、`Makefile`）
   - 查找 `pom.xml` 确定模块结构（单模块 vs 多模块）
   - 查找 `@SpringBootApplication` 主类
   - 查找 `application-*.yml` 确定可用 profile
   - **询问用户**使用哪个 profile 启动
3. 将确认的启动命令记录到项目 CLAUDE.md
4. 提示用户在另一个终端执行启动命令，等待服务就绪后继续

### Step 4: 确定模型文件路径

- 使用 Glob 查找项目中 `foggy/templates/` 目录的绝对路径
- 如果用户指定了特定文件 → 使用其所在目录
- 如果找到多个 templates 目录 → 询问用户使用哪个

### Step 5: 调用验证接口

```bash
curl -s -X POST http://localhost:{port}/api/semantic-layer/validate \
  -H "Content-Type: application/json" \
  -d '{
    "path": "{模型目录绝对路径}",
    "namespace": "claude-validate",
    "clearExisting": true,
    "includeStackTrace": true
  }'
```

### Step 6: 解析结果并输出

根据返回的 JSON 结构解析结果：

**成功时**（`success: true`）：
```
验证通过

- 总文件数: {totalFiles}
- 有效文件: {validFiles}
- 耗时: {durationMs}ms
```

如有 warnings，追加：
```
警告:
- [{file}] {message}
  建议: {suggestion}
```

**失败时**（`success: false`）：

先按 `category` 字段分类错误：
- `MODEL` — 真实模型错误（语法、引用、字段等）
- `CASCADING` — 因上游 TM 加载失败导致的级联错误

优先展示 MODEL 错误，CASCADING 错误折叠显示：
```
验证失败 ({invalidFiles}/{totalFiles} 个文件有错误)

模型错误 ({modelErrorCount}):
1. [{file}] ({type})
   {message}
   行号: {line}
   建议: {suggestion}

级联错误 ({cascadingErrors}, 修复上游TM后自动消除):
  - [{file}] {message} (因 {tmName} 加载失败)
```

如果 `cascadingErrors > 0` 且有 MODEL 类型的 TM 错误，提示用户优先修复 TM 错误，级联错误会自动消除。

然后根据错误类型给出修复建议：
- 语法错误 → 读取对应文件，定位错误行，给出修复代码
- 引用错误（TM 未找到）→ 检查 TM 文件是否存在，文件名是否匹配
- 字段引用错误 → 检查 TM 中是否定义了该字段

## 响应体参考

```json
{
  "success": false,
  "namespace": "claude-validate",
  "totalFiles": 5,
  "validFiles": 3,
  "invalidFiles": 2,
  "cascadingErrors": 1,
  "errors": [
    {
      "file": "query/OrderQueryModel.qm",
      "type": "QM",
      "line": 12,
      "column": null,
      "severity": "ERROR",
      "code": "ScriptParseException",
      "message": "Unexpected token...",
      "suggestion": null,
      "category": "MODEL",
      "stackTrace": "..."
    },
    {
      "file": "query/SalesReport.qm",
      "type": "QM",
      "category": "CASCADING",
      "message": "Table model 'DimProduct' not found..."
    }
  ],
  "warnings": [
    {
      "file": "model/DimProductModel.tm",
      "type": "TM",
      "line": null,
      "severity": "WARNING",
      "code": null,
      "message": "...",
      "suggestion": "..."
    }
  ],
  "durationMs": 230
}
```

## 决策规则

- 如果服务未运行且用户不想启动 → 告知无法离线验证，建议先启动服务
- 如果验证通过但有 warnings → 仍然报告为通过，但列出警告供参考
- 如果有错误且用户同意修复 → 直接读取出错文件并修复
- 如果模型目录下无 `.tm` / `.qm` 文件 → 提示目录可能不正确
