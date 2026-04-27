# 进度跟踪: P2-list_models模型发现入口与get_metadata隐藏

**当前状态**: [Phase 1 完结]

## 里程碑

### Phase 1: 发现入口分离 (8.3.0.beta)

- [x] **M1**: 创建 `list_models_schema.json` (无参数 schema)
- [x] **M2**: 创建 `descriptions/list_models.md` (明确提示词作用)
- [x] **M3**: Java 侧实现 `ListModelsTool.java` (复用 `SemanticServiceResolver`)
  - [x] `ToolConfigLoader` 注册新工具
  - [x] `application.yml` 添加默认配置
  - [x] `descriptions/get_metadata.md` 添加过时/迁移警告
- [x] **M4**: Java 端 AI 流程切换 (`SYSTEM_PROMPT` 等)
- [x] **M5**: Java 端测试覆盖 (`AiToolsIntegrationTest` 绿灯)
- [x] **M6**: Python 侧单向拉取同步 (`scripts/sync_mcp_schemas.py`)
- [x] **M7**: Python 侧 AI 流程切换 (`mcp_rpc.py` 逻辑对齐)
- [x] **M8**: Python 侧测试验证 (`pytest tests/mcp/`)
- [x] **M9**: 双端 E2E 确认 (使用 `python tools/mcp_repl.py` 或 cursor 调用 `list_models`)

### Phase 2: 全面下线 (8.4.0)

- [ ] 移除 `dataset.get_metadata` 注册
- [ ] 移除对应 handler 逻辑
- [ ] 归档冗余文件

## 问题日志

- 1. **Java 环境问题**: 已解决，通过修正 pom.xml 确保本地能成功编译。
- 2. **Python Sync 脚本路径**: 已解决，使用 `--java-root` 参数传递正确的目录位置。
- 3. **Python 测试验证路径**: 已解决，路径为 `pytest tests/test_mcp/`。
