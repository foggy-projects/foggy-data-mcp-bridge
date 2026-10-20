---
doc_type: reference
intended_for: model authors and engine maintainers
purpose: 区分 QM 具体列的查询标识、来源和输出别名，说明兼容访问与权限边界。
---

# QM 字段命名合同

```javascript
{ ref: line.invoiceLineId, name: 'queryId', alias: 'resultId', caption: '明细 ID' }
```

| 配置 | 合同 |
|---|---|
| ref | 绑定来源列、表实例和维度角色，不因重命名改变 SQL 表达式 |
| name | QM 查询标识，columns、slice、groupBy、orderBy 使用此名称 |
| alias | SELECT 输出别名和结果对象键；已选列 orderBy 也可使用输出别名 |
| caption | 展示标题 |

上例选择 `columns: ['queryId']`，结果键为 `resultId`。`name` 与 `alias` 可以不同。
未提供 name 或 alias 的值各自沿用既有 ref 派生规则；仅改 name 不会自动改 alias。
alias 不自动成为 columns/slice/groupBy 的通用同义词；显式 condition 定义的既有解析保持不变。
SQL WHERE 不直接引用 SELECT alias，而是由引擎解析为条件绑定的源表达式。

具体维度列也适用上述合同：

```javascript
{ ref: line.track.album.artist$id, name: 'albumArtistId', alias: 'albumArtistId' },
{ ref: line.track.album.artist$caption, name: 'albumArtistName', alias: 'artistLabel' }
```

查询使用 albumArtistId / albumArtistName，结果使用 albumArtistId / artistLabel。
具体 caption 命名投影按其来源 caption 进行筛选；既有默认路径和显式 condition 配置仍沿用原规则。

## 兼容与元数据

- 未配置列覆盖的 QM 保持既有字段名、输出键和维度自动展开。
- 显式 columns 只选择请求的投影；默认全字段选择会包含新注册的命名投影及保留的维度兼容列。
  需要固定返回列集合时，应显式指定 columns。
- 已有 TM dimension.alias、点路径、下划线路径、短维度名的访问保留。它们不等同于 QM 列 alias。
  旧维度兼容入口的返回键也沿用原规则；需要新输出键时，选择对应的 QM 查询 name。
- 表实例 alias 用于绑定 JOIN owner；DSL `AS` 是请求级投影；两者也不改变 QM 列 name 合同。
- JSON / 单模型 Markdown / 多模型 Markdown 均公开自定义查询 name，并标明 outputName / 输出键。
  JSON 的字段字典键及 `fieldName` 是查询标识；字段信息内既有的 `name` 仍是展示名称，
  不与 QM 配置项 `name` 混为一谈。
  仍可查询的旧维度兼容入口可以同时出现；已不能选择的普通属性原名称不再被当作公开字段推荐。
  同时声明原属性与改名投影时，两者都保留。多模型 JSON 中不同模型的输出别名以
  `fields[fieldName].models[modelName].outputName` 为准。
- 整维度 ref 的 name/alias 目前不提供展开前缀重命名。为保持旧模型可加载，仍按原路径展开，
  并记录 `QM_DIMENSION_NAME_OVERRIDE_IGNORED` 警告。请分别配置具体 ID/caption 项。
- 自定义名称不能绕过来源字段的 TM/QM 隐藏规则或 deniedColumns；runtime fieldAccess 可使用
  新 name，已有来源维度授权也覆盖其命名投影。没有改变 namespace、原子刷新或缓存数据格式。

模型名称/输出配置改变后须按既有流程刷新模型；部署新引擎时，对受影响模型按现有运维流程
失效旧结果缓存，避免继续读取此前忽略 alias 时的返回键。

## 计算列、度量和 pivot

- QM `formula` 项也遵守 `name` / `alias` 合同；省略 alias 时输出 name。请求中的
  `calc AS result` 仅覆盖该次投影，不修改模型定义。不同的已选字段不能使用同一输出键。
- SQL 查询只选择末级预定义公式时，引擎注入完整依赖闭包；依赖不自动出现在最终 columns 中。
  依赖包括表达式、`partitionBy`、`windowOrderBy` 及传递计算字段。查询权限、物理列映射和
  元数据可见性使用同一规则；窗口依赖不因未直接返回而免于权限检查。
- 已选聚合表达式的 ORDER BY 别名检查其来源权限；这不会把别名变为 slice/groupBy 的输入同义词。
- 度量元数据仅公开实际绑定的查询字段，聚合类型取实际 QM 绑定。原名与改名投影同时声明时均保留。
- pivot 的轴名、metric 名继续使用查询 name。内存处理、TopN 下推和辅助总计查询在返回边界
  显式转换输出键；AVG、COUNT_DISTINCT 等不可加度量保留原聚合方式。
- 普通查询结果 `schema.columns[].name` 对应实际 items 键；不同时附加 `queryName`。
  模型元数据 `fieldName` 仍是查询标识，`outputName` 是输出键。

部署兼容性：此前显式配置但被忽略的 formula alias 将生效，相关调用方应按 alias 读取结果；
此前可间接使用未授权窗口依赖的查询将被拒绝。部署时刷新模型并失效受影响的结果缓存。

使用封闭 JSON schema 的客户端需允许结果列上的可选 `queryName`；默认 name 与 alias 相同时不返回该属性。
