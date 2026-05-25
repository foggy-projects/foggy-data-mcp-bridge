# foggy-data-viewer 自定义列表验收清单

## 范围

本清单用于 8.6.0.beta 阶段验收 `DataTableWithSearch` 的自定义列表能力，覆盖前端交互、后端存储、用户隔离、默认方案和验证证据。

## 接入前置

- 业务页面使用 `DataTableWithSearch`。
- 前端传入 `listPreset.enabled=true`。
- 前端传入稳定 `userId`，v1 中该值只作为配置命名空间。
- 前端传入 `model`，同一模型多页面复用时传入 `businessKey`。
- 后端启用 `foggy.data-viewer.enabled=true` 或保持默认启用。

## 功能验收

| 项 | 验收点 | 期望结果 | 状态 |
|---|---|---|---|
| 1 | 工具栏入口 | 启用 `listPreset` 后出现“自定义列表”按钮 | 待验收 |
| 2 | 保存方案 | 输入名称后可保存当前列表方案 | 已自动化验证 |
| 3 | 空列校验 | 所有字段隐藏时禁止保存并提示至少保留一个显示字段 | 待验收 |
| 4 | 列显隐 | 保存并应用后，隐藏字段不再显示 | 待验收 |
| 5 | 列顺序 | 保存并应用后，字段顺序按方案恢复 | 待验收 |
| 6 | 列宽 | 保存并应用后，列宽按方案恢复 | 待验收 |
| 7 | 固定列 | 保存并应用后，左/右固定列按方案恢复 | 待验收 |
| 8 | 筛选保存 | 勾选“保存当前筛选和排序”时保存当前筛选条件 | 待验收 |
| 9 | 筛选不保存 | 取消勾选时保存为空筛选和空排序 | 待验收 |
| 10 | 默认方案 | 设为默认后再次进入同一页面自动应用 | 已自动化验证 |
| 11 | 已应用提示 | 弹窗中能看到当前已应用方案 | 待验收 |
| 12 | 默认提示 | 弹窗中能看到默认方案 | 待验收 |
| 13 | 失效字段提示 | 已保存字段不在当前 schema 中时，应用方案给出失效字段提示 | 待验收 |
| 14 | 删除方案 | 删除后列表不再显示该方案 | 已自动化验证 |
| 15 | 覆盖当前 | 覆盖已有方案后，方案内容更新为当前列表状态 | 待验收 |

## 隔离验收

| 项 | 验收点 | 期望结果 | 状态 |
|---|---|---|---|
| 1 | 用户隔离 | `userId=A` 保存的方案，`userId=B` 不可见 | 待验收 |
| 2 | 模型隔离 | 不同 `model` 的方案互不污染 | 待验收 |
| 3 | 页面隔离 | 同一 `model` 下不同 `businessKey` 的方案互不污染 | 待验收 |
| 4 | 默认唯一 | 同一 `userId + model + businessKey` 只有一个默认方案 | 待验收 |

## 存储验收

| 项 | 验收点 | 期望结果 | 状态 |
|---|---|---|---|
| 1 | Mongo 优先 | Mongo store 可用时方案写入 `list_presets` | opt-in 自动化验证 |
| 2 | 文件降级 | Mongo store 不可用时方案写入文件系统 | 待验收 |
| 3 | 文件路径 | 文件路径按 `{fileBaseDir}/{safeUserId}/{safeModel}/{safeBusinessKey}` 组织 | 待验收 |
| 4 | 路径清洗 | `userId/model/businessKey/presetId` 中的非法路径字符被清洗 | 待验收 |
| 5 | 原子写入 | 文件写入使用临时文件替换，避免半写入 | 待验收 |

## 字段校验扩展验收

| 项 | 验收点 | 期望结果 | 状态 |
|---|---|---|---|
| 1 | 默认校验器 | 未注册 `ListPresetFieldValidator` 时不强制依赖 QM schema 服务 | 已自动化验证 |
| 2 | 保存校验 | 注册自定义校验器后，create 中非法字段被拒绝 | 已自动化验证 |
| 3 | 更新校验 | 注册自定义校验器后，update 中非法排序字段被拒绝 | 已自动化验证 |
| 4 | 错误映射 | 校验器抛 `IllegalArgumentException` 后 Controller 转为业务失败响应 | 待验收 |

## 兼容验收

| 项 | 验收点 | 期望结果 | 状态 |
|---|---|---|---|
| 1 | `SavedQueryManager` | 旧保存查询能力不受影响 | 待验收 |
| 2 | `enableSavedQuery` | 旧声明不因本轮自定义列表改动报错 | 待验收 |
| 3 | 普通表格 | 未启用 `listPreset` 的表格行为不变 | 待验收 |

## 自动化验证记录

2026-05-24 已完成：

- `addons/foggy-data-viewer/frontend`: `npm test -- --run` 通过，271 个测试。
- `addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/list-preset/ListPresetManager.test.ts src/components/DataTableWithSearch.test.ts` 通过，80 个测试。
- `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。
- `addons/foggy-data-viewer/verification-app`: `npm run build` 通过，仅有 Vite 大 chunk warning。
- `addons/foggy-data-viewer/verification-app`: `npm run test:e2e -- --project=chromium` 通过，1 个 Playwright 浏览器流程用例。
- `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer` 通过，76 个测试，Mongo opt-in 用例默认跳过 3 个。

## 暂缓验收项

- 后端自动解析用户身份。
- 部门/租户共享权限模型。
- 完整 DSL 条件编辑器。
- QM schema 字段权限强校验。
- 配置加密或敏感筛选条件脱敏。
