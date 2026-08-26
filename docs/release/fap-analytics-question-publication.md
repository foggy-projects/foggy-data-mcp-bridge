# Analytics question Function 的 FAP 宿主发布

标准 Launcher 只携带 Analytics Provider callback、问数 Skill 和不可变 Function 发布材料。FAP
资源由 FAP 宿主的管理面显式发布；Launcher 启动、Console 启用和每次 Function 调用都不会创建、更新、
刷新、重试或修复 FAP Function、Skill、Capability，也不会持有 Provider 管理凭据。

## 发布材料

`FapAnalyticsQuestionFunctionCatalog` 是五项问数 Function 的唯一 Java 发布子目录：

- `foggy.analytics.model-dependencies.list@v3`
- `foggy.analytics.semantic-models.describe@v2`
- `foggy.analytics.semantic-queries.execute@v2`
- `foggy.analytics.query-model.run@v2`
- `foggy.analytics.compose.run@v1`

宿主 publisher 应从 `publicationValues()` 取得 exact
`BusinessFunctionProjection` wire value，不要复制或重写 Schema。Console JAR 同时携带：

- `fap/analytics-question-answering/skill-metadata.json`
- `fap/analytics-question-answering/SKILL.md`
- `fap/analytics-question-answering/references/`
- `fap/analytics-question-answering/function-schema-delivery.json`
- `fap/analytics-question-answering/host-publication-manifest.json`（新构建）

`host-publication-manifest.json` 只冻结 Skill identity 和五项 Function 的 schema/projection digest；它声明
`HOST_MANAGED_EXPLICIT` 和 `launcherStartupMutationAllowed=false`，不是远程 apply 指令。已发布且缺少该新增
manifest 的旧 Launcher（包括 `0.1.18`）仍按原发布合同有效；它们通过内嵌 schema delivery 和 Adapter
catalog digest 进行验证。

## 一键导出与同步边界

源码工作区可以从 Java Catalog 一键同步所有生成型交付文件并检查漂移：

```bash
python3 scripts/release/fap_question_delivery.py sync --skill-revision <revision>
python3 scripts/release/fap_question_delivery.py check
python3 scripts/release/fap_question_delivery.py bundle --output <handoff-bundle.json>
```

`sync` 只更新仓库内的 Skill metadata、schema delivery 和 host publication manifest；`check` 完全只读。
`bundle` 输出完整 Function projections、Skill 文档、callback 合同和 digest，可由 FAP 宿主 publisher 直接
消费。

安装并启用 Console 后，不需要源码 checkout。管理员可调用：

```text
GET /analytics-console/api/v1/integrations/fap/question-publication
```

响应的 `data` 同样是 `foggy.analytics.question-host-sync-bundle.v1`。推荐由 FAP 管理面提供一个
`sync-from-foggy-runtime` 命令：拉取 bundle、比较当前 describe 结果、追加缺失的 Function/Skill/Capability
revision，再 describe 回验。Runtime API 只提供只读 handoff，不接收 FAP 管理凭据，也不执行 apply。

## 宿主发布顺序

1. 验证 Launcher 包和内嵌 Console/Function 合同：

   ```bash
   python3 scripts/release/runtime_launcher_package.py verify --jar <launcher.jar>
   ```

2. FAP 宿主 publisher 从上述在线 handoff endpoint 拉取 `data.functions`，或序列化
   `FapAnalyticsQuestionFunctionCatalog.publicationValues()`，先做只读门禁：

   ```bash
   python3 scripts/release/fap_question_publication_gate.py \
     --catalog-json <host-exported-business-function-projections.json>
   ```

   只有输出 `status=READY_FOR_HOST_APPLY` 且 `mutationPerformed=false` 才进入宿主管理面的 dry-run。

3. 由宿主既有、经过认证的 FAP management client 追加五个 immutable FunctionRef。不得在同一
   FunctionRef 下发布不兼容 Schema；不兼容变更使用新的 `@v2`、`@v3` 等 FunctionRef。
4. 追加 `analytics-question-answering` Skill revision，并核对 Skill digest、metadata revision 与五项
   `INLINE` schema delivery。
5. 追加包含同一五项 FunctionRef 的 Capability revision。不要原地覆盖已经被会话冻结的旧 revision。
6. Function、Skill、Capability 均可 describe/读取且 digest 一致后，再配置 Console 的 FAP URL、
   provider/binding、callback capability 和服务端授权，最后显式开启 FAP。

本仓库没有 FAP management API/client 合同，因此门禁脚本刻意不提供 `apply`、凭据、自动重试或 repair
参数。实际写入必须由 FAP 宿主仓库的受管 publisher 完成；若宿主无法导出上述 wire projections，应先在
宿主侧补齐 exporter，而不是让 Launcher 绕过管理面。

## 验收与回退

- FAP describe 返回的五项 `schemaDigest` 和 `projectionDigest` 必须与门禁输出完全一致。
- 单模型 Function 输入只包含 `namespace`、`modelName`、`mode`（适用时）和业务查询参数；不得出现
  `expectedModelRevision`、`modelRevision` 或 `MODEL_REVISION_CONFLICT`。
- 权限、Sandbox、协议与查询错误仍由既有 Provider callback/Analytics Runtime 路径处理。
- 发布失败时保持 FAP 未启用或继续绑定上一组 immutable Skill/Capability；不要修改历史会话、任务或业务
  数据来“修复”发布。
