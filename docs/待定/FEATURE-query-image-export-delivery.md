---
doc_role: workitem
doc_type: implementation-and-acceptance
intended_for: developers-and-reviewers
purpose: 记录真实查询 PNG 导出的跨仓文档同步、交付验证和安全证据索引，不承担正式发布声明。
status: DELIVERY_VALIDATION_PASSED_PENDING_RELEASE
recorded_at: 2026-10-03
---

# FEATURE 查询图片导出交付验证

本项在 `codex/query-image-export` 开发分支交付真实查询结果可视化。正式发布版本尚未确定，
不调整 Maven、CLI 或 Skill 版本，不移动 stable stack、tag 或发布网站；已发布 CLI `0.1.24`
不能作为包含本功能的发行证据。确认版本后再将本记录迁入正式迭代目录。

## 实现与维护仓库

| 内容 | Canonical owner | 本项范围 |
| --- | --- | --- |
| Java 查询、权限、PNG、MCP | `foggy-data-mcp-bridge` | `QueryImageExportService`、`QueryImageRenderer`、`dataset.export_image`；Java 17 headless |
| CLI 文件导出 | `foggy-runtime-cli` | `query export-image`，MCP 工具发现、inline PNG 校验、原子保存 |
| 用户文档 | `foggy-data-mcp-docs` | EN/ZH `implementation/runtime-api-examples.md`、`mcp-operations.md` |
| Skill 源 | `foggy-ai-analysis` | EN/ZH `runtime-cli-command-rules.md`、已有 `foggy-semantic-query` 的路由与 DSL reference |

本轮文档与 Skill 源分别已提交为 `5a40907` 和 `ba5d1e0`；Java 生产代码的协议兼容修复
提交为 `dc2c1e33`。配套评审使用相同开发分支，不将这些提交等同于正式发布。

仓库内 `.codex/skills` 与用户 `~/.agents/skills` 是消费副本，不能替代上述 Skill 源仓库。
Skill 同步仅修改既有入口和 reference，不增加重复 Skill，不打包或发布 Skill。

Java 使用已有 XChart 绘制单系列柱状图、折线图，Java2D/ImageIO 绘制表格及标题页脚。
随包中文字体在首次使用时加载，运行时不下载字体，不依赖系统安装中文字体；渲染不调用
Docker、Node、浏览器或外部平台。本项不扩展 Python Runtime、AI 绘画或复杂报表设计器。
用户接口、实现选择及边界详见[查询结果 PNG 导出](../dev-guide/query-image-export.md)。

## 对外合同

- Java 和 CLI 共享 `dataset.export_image`；以目标 endpoint 的 `tools/list` 为准。
  CLI 默认走 `/mcp/analyst/rpc`，缺失工具返回 `UNSUPPORTED_IMAGE_TOOL`，exit `3`。
- 图片基于一次实际 QueryModel 查询。`X-NS`、opaque `Authorization`、模型和字段/行治理
  沿用查询链路，不接受调用方伪造结果行，不建立 raw SQL 或私有 endpoint fallback。
- 默认 MCP 返回原生 `image/png` 内容和不重复 Base64 的元数据；`delivery: link` 复用已有
  图表存储，需要部署方配置可访问 URL。CLI 使用 inline 并保存至指定文件，不下载链接。
- 表格最多 50 行、12 列，柱状图 40 点，折线图 100 点。分页、引擎截断和展示上限都须
  标记部分结果。单系列只消费最终结果字段，保留查询顺序、负数、null，不自行补齐或聚合。
- 标题、字段 caption 和已知单位可以显式设置，caption 缺省取 schema title/字段名；单位
  不猜测、不缩放数值。字体不支持的字符或无法清晰排版时明确失败。
- 宽度 640–2400 px；请求高度表格 360–2400 px、图表 520–2400 px，表格输出自动增高至
  4096 px；上限 800 万像素、8 MiB。标题 28 px，正文/表头/轴名称 18 px，刻度 16 px，
  页脚 14 px。不缩小字号来挤入过多信息。

## 安全验收索引

| 验证点 | 可复核证据 | 当前状态 |
| --- | --- | --- |
| 实际 SQLite 查询输入与图片一致 | `QueryImageExportIntegrationTest`，独立 SQL 对比与 `query-image-evidence.json` | Windows MVP 已通过 |
| 同名模型双 namespace 隔离 | 同一测试的两个 namespace，以及 `http-mcp-contract.json` / 两张 HTTP PNG | Windows MVP 已通过 |
| 模型、语义字段、物理列、行权限 | 同一测试的拒绝路径及行谓词；`query-row-permission-rows.json` / 对应 PNG | Windows MVP 已通过 |
| 查询失败、拒绝或空结果不渲染/保存 | 同一测试验证无 renderer/storage 调用 | Windows MVP 已通过 |
| null、负数、长中文、截断及布局 | 13 张 PNG 和 `independent-visual-qa.md` | Windows 实际图片已复查 |
| 真 HTTP MCP 工具发现与原生 PNG | 同一测试真实随机端口服务，`http-mcp-contract.json` | Windows MVP 已通过 |
| CLI 三种图片端到端原子保存 | 同一测试真实 CLI 进程和 HTTP MCP，`cli-http-evidence.json` | Windows 源码 CLI 已通过 |
| Linux packaged JAR，无系统中文字体 | `.acceptance/query-images-delivery/linux/environment.json`、`checks.json`、包内字体/OFL 校验、双 namespace 三种 PNG | 最终 JAR 55 项通过；6 张 PNG 与逐张目视版本 SHA 一致 |
| 隔离安装的 wheel/sdist 客户端 | CLI owner 的 `build/query-images-delivery/cli/receipt.json`、`server-association.json`、包校验值、8 张 PNG | 最终 JAR 14 项通过；8 张 PNG 与逐张目视版本 SHA 一致 |
| 官方 MCP 客户端原生图片展示 | workspace 验收目录的 `inspector/client-acceptance.json`、三张 native PNG 和五张 UI 截图 | Inspector 2.9.0 / 协议 2026-07-28，5 项通过 |
| 文档及 Skill 合同同步 | 两个 canonical 源仓库 diff 与本地校验 | 已更新；校验结果见下方 |
| 临时 fixture 清理 | Linux 证据目录的 `cleanup-linux.json`、`cleanup-windows.json` | 测试 Java/SSH 已关闭，端口不再监听，证据保留 |

Windows MVP 证据在本地 `query-images/`；Java 交付证据在 `.acceptance/query-images-delivery/`，
安装 CLI 证据在其 owner 的 `build/query-images-delivery/cli/`。不把机器地址、凭据、连接串、
完整请求头或原始异常日志提交到公开仓库。Windows 截图和源码验证不能替代 Linux、安装产物
或实际客户端的签收；后续必须记录实际结果，不能用预期能力填充通过结论。

## 复现与证据规则

打包验收使用 Maven `runtime-api` profile 生成真正的 launcher 交付 JAR，确保
`/api/v1/capabilities`、模型 describe/validate 与 MCP 同时可用。首次默认 profile 的 JAR
可以启动，但 `/api/v1/capabilities` 返回 404，原因是未包含 Runtime API 装配；这不是渲染器
失败，也不能据此签收完整交付链路。重新使用 `-Pruntime-api` 打包后再验收，不通过外部
classpath 拼装模拟交付产物。profile 是构建验收事实，不写入普通用户的查询命令流程。
跨平台可复用探针为 `scripts/verify-query-image-delivery.py`，说明在
`scripts/README-query-image-delivery.md`；其输出位于 `.acceptance/query-images-delivery/linux/`。
本轮 Linux 环境记录为 OpenJDK `17.0.20.1`，系统及隔离字体目录的中文字体计数均为 0。
`runtime-api` profile 的最终 launcher 为 93,031,274 字节；打包字体为 8,331,336 字节，
包内字体及 OFL 校验通过。两个 namespace 中同名模型的各 6 行与独立 SQLite SELECT
逐行一致，三种原生 MCP PNG 均生成成功，模型/字段/行权限及空查询检查通过。
最终 Linux 共 55 项检查通过；JAR SHA-256 为
`b08ababbb7e68fd1198b07e5c208c13f06612022b3e06c4548849b0b7e9d8510`。
6 张 Linux PNG 已分别打开复核，中文、数字对齐、万元/笔单位、轴名称、边距及页脚正常；
两个 namespace 的金额差异在图片中真实保留。独立视觉记录见本地
`.acceptance/query-images-delivery/independent-delivery-visual-qa.md`。

官方 MCP Inspector `2.9.0` 使用 `2026-07-28` 协议时，首次连接后发现工具列表缺少现代
响应元数据。现已补充通用 list/call/discover/ping 的必要元数据，25 项聚焦回归通过，
并完成最终 JAR 的 Linux 复验。最终 6 张 PNG 的 SHA 与已逐张目视复核的首轮图片一致。
安装客户端和官方 Inspector 均已完成对最终 JAR 的真实复验。
本项只验证该客户端的真实工具与 PNG 互通，不声明完整现代 MCP 协议符合性。

官方 MCP Inspector `2.9.0` 通过 `2026-07-28` 协议直接完成真实 `tools/list` 和
`tools/call`，5 项客户端验收通过：table/bar/line 显示原生 PNG，浏览器 `img.complete=true`
且自然尺寸正确；空查询返回 `NO_QUERY_DATA`、权限拒绝返回 `QUERY_FAILED`，均没有图片。
三张已显示 DOM 图片分别保存为 `native-table.png`、`native-bar.png`、`native-line.png`，
实际尺寸为 1200×595、1200×720、1200×720；原图逐张检查中文、数字对齐、单位、轴名和
页脚通过。安全收据绑定 `dc2c1e33` 与最终 JAR SHA，并保留五张无请求头/凭据的 UI 截图。
证据在 workspace 验收目录 `.acceptance/query-images-delivery/inspector/`。

真实查询测试在 `foggy-dataset-mcp` 的
`src/test/java/com/foggyframework/dataset/mcp/integration/QueryImageExportIntegrationTest.java`。
设置 `foggy.image.evidence-dir` 生成图片和行数证据；可选 `foggy.image.cli-python` 和
`foggy.image.cli-project` 启动既有 CLI 客户端。Python 在这里仅运行 CLI，不参与 Java 渲染。

验收记录保留构建/安装来源、Java/CLI/客户端版本、namespace 名称、成功/拒绝状态、图片
尺寸与字节数、实际行数及截断标志。图片交付前检查中文、标题、单位、数值对齐、null 空隙、
换行、轴标签和页脚；不得提交 Authorization、连接信息、Base64 或内部查询异常详情。
开发分支产物须记录源码 commit 和校验值；版本字符串本身不能证明正式发行包已包含新能力。

CLI 当前本地 wheel/sdist 构建保留包版本 `0.1.24`，仅作为开发分支验收产物，不是公开发行。
隔离 venv 已核对包 SHA、site-packages 模块来源、源码 SHA、console entry points 和
`dependencies=[]`，清除 `PYTHONPATH`。最终联调 receipt 为 `success=true`、`prepareOnly=false`，
wheel/sdist 各 7 项通过：实际 CLI 生成三种 PNG、第二 namespace 返回不同数据、无效字段及
权限拒绝返回 exit `2`、受控的缺工具 endpoint 仅调用 `tools/list` 并返回 exit `3`；失败时
已有文件均保留。缺工具探针模拟工具缺失，不声称运行过某一历史发行版。

两种安装方式的 8 张输出 PNG 均已分别目视检查，中文、表头/轴、单位、零基线、字号及页脚
正常；对应 PNG 的 SHA 一致。主表 1200×595、31,208 字节，主柱图/折线图 1200×700、
27,593 / 28,079 字节；本批 CLI 往返 406–469 ms，仅是该环境的小结果记录，不作为冷启动
或大数据量性能承诺。

最终 CLI receipt 时间为 `2026-10-03T05:49:07.506946+00:00`，SHA-256 为
`1fa544bf95bb50b6110066f052071996d5ff5a03ecd0a0d9950172b3eef225d9`。
`server-association.json` 将该 receipt、安全的 14 项检查和 8 张 PNG 关联到上述最终 JAR
SHA，避免使用首次包的结果代替最终交付。原包及 venv 保持不变，重新生成的 8 张 PNG 与
逐张目视版本的 SHA 完全一致。

| 本地开发产物 | SHA-256 |
| --- | --- |
| wheel | `be4ccbe3777ef62935ce0a28c0ac8060b6c5cb18e1141e62f6ae124d333a1278` |
| sdist | `5f606833cbff09cc0a4145c006c21e8108e9685a8b477fd7b870286129ad3d5a` |

## 文档与 Skill 本地校验

- `skill-creator/scripts/quick_validate.py`：EN、ZH 的 `foggy-ai-analysis` 和
  `foggy-semantic-query` 三个源入口均通过。
- `foggy-data-mcp-docs`：`npm ci --ignore-scripts --no-audit --no-fund` 和 VitePress
  production build 均通过。依赖由既有 lockfile 安装，不调整版本和下载资产；构建保留既有
  chunk 大小提示，没有构建或链接错误。
- 两个 owner 的 `git diff --check` 已通过。文档明确开发中状态，不宣称 `0.1.24` 已发布本功能。

交付验证已完成：最终 Linux 55 项、安装 CLI 14 项、官方 MCP 客户端 5 项均通过。独立视觉
复核覆盖最终交付的 17 张 PNG，另保留 Windows 13 张边界输出的既有复核记录。正式版本、
发布资产和网站上线仍未执行，不将本工作项的通过结论扩大为正式发行或完整协议符合性。

验收结束后已关闭本任务的 Java 进程及 SSH 转发，Linux/Windows 测试端口均停止监听，
清理收据全部通过，生成证据继续保留。探针始终生成自己的 synthetic identity，不读取或
复用全局 Authorization；最终 helper 语法检查通过。
