---
record_type: remediation-submission
target: mcp-chart-export-renderer-contract
status: ready-for-independent-signoff
submitted_at: 2026-07-26
remediates:
  - AC-10
supersedes_decision: false
prior_signoff: mcp-chart-export-renderer-contract-signoff-20260726.md
---

# Remediation Submission: MCP 图表 AC-10 日志脱敏

## Purpose

记录首次独立签收中 AC-10 阻断项的修复及重验证证据。原拒签记录保持为历史结论；
本记录不构成接受决定，交付仍需新的独立签收。

## Blocking Path

- export 工具把 QueryModel 最终 `items` 交给 `ChartTool` 和 `XChartRenderer`。
- Category/XY Y、Pie value、XY X 的类型校验曾把实际输入值写入异常消息。
- `ChartTool` 会记录异常消息，因此查询数据可能进入应用日志。

## Remediation

- 修改 `XChartRenderer` 的三类类型错误，移除实际输入值。
- 保留字段名、期望类型以及 XY 分类字符串应改用 CategoryChart 的诊断信息。
- 新增三条完整 export 调用链回归测试，分别使用唯一敏感标记覆盖：
  - CategoryChart 非数值 Y。
  - XYChart 非数值/非日期 X。
  - PieChart 非数值 value。
- 每条测试验证：
  - `chartError` 保留稳定诊断但不含敏感值。
  - 捕获的应用日志不含敏感值。
  - 捕获的应用日志不含调用方 Authorization。

## Evidence

- Chart contract focused:
  - command:
    `mvn -B -ntp -pl foggy-dataset-mcp -Dtest=ChartToolTest,ExportWithChartToolsTest,XChartRendererTest,EChartsRendererTest,ToolConfigLoaderTest,ToolFilterServiceTest,AnalystMcpControllerTest,ResultValidatorTest test`
  - result: 96 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。
- Focused:
  - command:
    `mvn -B -ntp -pl foggy-dataset-mcp -Dtest=ExportWithChartToolsTest,XChartRendererTest,ChartToolTest test`
  - result: 29 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。
- Affected module:
  - command: `mvn -B -ntp -pl foggy-dataset-mcp -am test -DskipITs`
  - result: 15 个 Reactor 模块 SUCCESS；MCP 模块 520 tests，
    0 failures，0 errors，0 skipped，BUILD SUCCESS。
- Static:
  - production chart/ChartTool scan: no `实际值` query-value concatenation.
  - Surefire report scan: no AC-10 sensitive markers.
  - `git diff --check`: pass.

## Submission Status

- remediation_status: ready-for-independent-signoff
- acceptance_status: not-granted-by-this-record
- required_next_step: 新的独立 reviewer 复核 AC-10，并形成新的 signoff record。
