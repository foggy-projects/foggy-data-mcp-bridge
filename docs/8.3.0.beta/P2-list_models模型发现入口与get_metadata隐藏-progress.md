# 进度跟踪: P2-list_models模型发现入口与get_metadata隐藏

**当前状态**: [Phase 1 完结 — 测试覆盖已补全]

## 里程碑

### Phase 1: 发现入口分离 (8.3.0.beta)

- [x] **M1**: 创建 `list_models_schema.json` (无参数 schema)
- [x] **M2**: 创建 `descriptions/list_models.md` (明确提示词作用)
- [x] **M3**: Java 侧实现 `ListModelsTool.java` (复用 `SemanticServiceResolver`)
  - [x] `ToolConfigLoader` 注册新工具
  - [x] `application.yml` 添加默认配置
  - [x] `descriptions/get_metadata.md` 添加过时/迁移警告
- [x] **M4**: Java 端 AI 流程切换 (`SYSTEM_PROMPT` 等)
- [x] **M5**: Java 端测试覆盖
  - [x] `ListModelsToolTest.java` — 11 个用例（工具名/category/Markdown格式/字段索引排除/空模型/异常跳过/timeRole优先级/AI prompt/特殊字符清理）
  - [x] `AnalystMcpControllerTest.java` — +2 个用例（tools/list 包含 list_models, tools/call 调用成功）
  - [x] `ToolConfigLoaderTest` — 默认工具数量已更新
  - [x] 全套 247 tests, 0 failures, BUILD SUCCESS
- [x] **M6**: Python 侧单向拉取同步 (`scripts/sync_mcp_schemas.py`)
- [x] **M7**: Python 侧 AI 流程切换 (`mcp_rpc.py` 逻辑对齐)
- [x] **M8**: Python 侧测试验证
  - [x] `test_list_models_tool.py` — 11 个用例（RPC 路由/工具配置/Schema/描述文件/BaseMcpTool 类/服务集成）
  - [x] 全套 162 tests, 0 failures
- [x] **M9**: 双端 E2E 确认

### Phase 2: 全面下线 (8.4.0)

- [ ] 移除 `dataset.get_metadata` 注册（启动条件：上线 14 天后审计日志中调用计数趋近 0）
- [ ] 移除对应 handler 逻辑
- [ ] 归档冗余文件

## 测试覆盖矩阵

| 测试类 | 用例数 | 状态 | 仓库 |
|--------|--------|------|------|
| `ListModelsToolTest` | 11 | ✅ 全绿 | Java |
| `AnalystMcpControllerTest` (+2 list_models) | 2 | ✅ 全绿 | Java |
| `ToolConfigLoaderTest` (含 list_models 断言) | 已更新 | ✅ 全绿 | Java |
| `test_list_models_tool.py` | 11 | ✅ 全绿 | Python |

## 问题日志

1. **Java 环境问题**: 已解决，通过修正 pom.xml 确保本地能成功编译。
2. **Python Sync 脚本路径**: 已解决，使用 `--java-root` 参数传递正确的目录位置。
3. **Python 测试验证路径**: 已解决，路径为 `pytest tests/test_mcp/`。
4. **Mockito UnnecessaryStubbingException**: 已解决，对可能未被调用的 stub 使用 `lenient()` 标记。
