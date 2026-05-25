# foggy-data-viewer 自定义列表下一步规划

## 背景

用户希望在 `addons/foggy-data-viewer` 的表格工具栏中增加类似旧版“自定义列表”的能力，用于保存和恢复常用列、默认过滤条件、排序、分页大小等视图配置。

本规划基于 8.6.0.beta 阶段约束：存储优先使用 Mongo；如果接入方没有提供 Mongo，则退化为文件系统。配置按用户隔离，v1 先由前端显式提供 `userId`，后端用它建立配置命名空间。

## 已确认决策

1. 存储仍以 Mongo 为主。
2. 没有 Mongo 时降级为文件系统存储。
3. v1 要求前端传入 `userId`，API 路径采用 `/users/{userId}` 这类结构。
4. `userId` 在 v1 只是配置命名空间，不作为安全边界。
5. 现有后端已经实现数据权限，查询配置泄漏暂时不是 P0 风险。
6. 后续再提供后台身份解析扩展，交给接入方按登录态、网关 header 或 session 做二次开发。

## API 草案

```text
GET    /data-viewer/api/list-preset/users/{userId}/models/{model}?businessKey=
GET    /data-viewer/api/list-preset/users/{userId}/models/{model}/default?businessKey=
POST   /data-viewer/api/list-preset/users/{userId}/models/{model}
GET    /data-viewer/api/list-preset/users/{userId}/presets/{id}
PUT    /data-viewer/api/list-preset/users/{userId}/presets/{id}
DELETE /data-viewer/api/list-preset/users/{userId}/presets/{id}
POST   /data-viewer/api/list-preset/users/{userId}/presets/{id}/default
```

`businessKey` 用于区分同一个 QM 在不同业务页面中的列表配置，例如工单列表和反馈列表可以复用同一个 model，但保存独立方案。

## 前端接口

`DataTableWithSearch` 新增 `listPreset` 配置：

```ts
type ListPresetConfig = {
  enabled?: boolean
  userId: string
  businessKey?: string
  autoLoadDefault?: boolean
  buttonText?: string
  placement?: 'toolbar-left' | 'toolbar-right'
}
```

接入示例：

```vue
<DataTableWithSearch
  :model="model"
  :list-preset="{
    enabled: true,
    userId: currentUser.id,
    businessKey: 'ticket-list',
    autoLoadDefault: true
  }"
/>
```

## 后端存储规划

新增 `ListPresetStore` 抽象，业务服务只依赖该接口：

```text
ListPresetStore
  - MongoListPresetStore
  - FileSystemListPresetStore
```

启用策略：

1. Mongo 可用时使用 `MongoListPresetStore`。
2. Mongo 不可用时使用 `FileSystemListPresetStore`。
3. 文件系统根目录提供配置项，默认可放在应用工作目录下的 `data-viewer/list-presets`。

文件系统结构建议：

```text
{baseDir}/list-presets/
  {safeUserId}/
    {safeModel}/
      {safeBusinessKey}/
        presets/
          {presetId}.json
        default.json
```

实现要求：

1. `userId`、`model`、`businessKey`、`presetId` 都必须做路径片段白名单清洗。
2. 写文件时使用临时文件加原子替换，避免半写入。
3. 同一个 `userId + model + businessKey` 只能有一个默认方案。
4. Mongo 与文件系统保存的数据结构保持一致，避免后续迁移成本。

## 执行顺序

### S1 契约冻结

1. 补齐 `ListPresetDef`、`ListPresetConfig`、`ListViewState` 类型定义。
2. 确认 API 路径、请求体、响应体。
3. 明确 `userId` 是 v1 必填项。

状态：已完成。前端已新增 `listPreset` 类型、API client 和导出入口。

### S2 后端存储和 API

1. 新增 `ListPresetDef/Service/Controller`。
2. 新增 `ListPresetStore`，实现 Mongo 和文件系统两套存储。
3. 增加默认方案唯一性处理。
4. 增加基础字段合法性校验。

状态：已完成第一版。后端已新增 `ListPresetDef`、`ListPresetController`、`ListPresetService`、`ListPresetStore`、`MongoListPresetStore`、`FileSystemListPresetStore` 和 `FallbackListPresetStore`。文件系统降级存储已覆盖默认唯一性、用户隔离、`businessKey` 隔离、删除、路径清洗和基础字段校验测试。已新增 `ListPresetFieldValidator` 扩展点，默认 no-op；接入方可注册同类型 Bean，对当前 `userId + model + businessKey` 下的 columns、columnSettings、slice、orderBy 做 QM schema 和字段权限校验。

### S3 前端状态能力

1. `DataTableWithSearch` 暴露 `getListViewState()`。
2. 补齐 `applyListViewState()`，支持列显隐、列顺序、列宽、筛选、排序、分页大小。
3. 默认方案加载完成后再触发表格查询，避免先查默认条件再被覆盖。

状态：已完成第一版。`DataTableWithSearch` 已新增 `getListViewState()`、`applyListViewState()`、`resetListViewState()`，内部维护 `activeListViewState` 派生表格列，支持列顺序、显隐、宽度、固定列、筛选、排序和分页大小。Schema 模式初始化时会先尝试加载默认 `ListPreset`，再执行首次 `loadData('mount')`。

### S4 自定义列表 UI

1. 在表格工具栏红框位置渲染“自定义列表”按钮。
2. 提供方案列表、保存当前、设为默认、删除、重命名能力。
3. 第一版不做完整 DSL 编辑器，只保存当前搜索面板已经能表达的条件。

状态：已完成 P1 版。新增 `ListPresetManager` 组件，并在 `DataTableWithSearch` 中通过 `listPreset` 配置自动挂载。默认位置为分页左侧；`placement='external'` 时不内置渲染，供业务方外部自行放置。当前支持加载列表、应用、保存当前、编辑已有方案、用当前状态覆盖已有方案、设为默认和删除；保存面板已提供常用列配置能力，支持列显隐、顺序调整、宽度和固定列设置；查询条件支持按“保存当前筛选和排序”开关决定是否写入当前筛选、排序。弹窗已展示当前已应用方案和默认方案，保存/覆盖时会阻止空可见列，应用包含失效字段的方案时会提示用户。第一版不提供完整 DSL 编辑器，只保存组件当前能表达的列、筛选、排序和分页状态。

### S5 测试和文档

1. 前端组件测试覆盖保存、加载、默认方案、列状态恢复、编辑和覆盖当前方案。
2. 后端测试覆盖 Mongo store、文件 store、默认唯一性、路径清洗。
3. 前后端测试必须运行通过后才算阶段完成。
4. 文档补充接入示例和降级存储说明。
5. 阶段结束后进入 implementation quality gate、test coverage audit 和 acceptance signoff。

状态：已完成本轮 1~5 推进。已修复 verification-app 构建类型链路，补齐自定义列表列配置面板、默认过滤条件摘要和保存语义，并新增 `ListPresetControllerTest` 覆盖 Controller 包装层。接入文档已补充 `listPreset` 最小接入示例、`userId` 边界和 Mongo/文件系统降级说明；8.6.0.beta 已新增自定义列表验收清单。

## 暂缓项

1. 后台自动解析用户身份。
2. 强共享权限模型。
3. 完整 DSL 编辑器。
4. 复杂列分组、固定列、聚合字段方案化。
5. 配置加密或敏感条件脱敏。

## 验收标准

1. 接入方传入 `userId` 后，用户可以保存并加载自己的列表方案。
2. 设置默认方案后，下次进入同一 `model + businessKey` 页面自动恢复。
3. 有 Mongo 时配置写入 Mongo；没有 Mongo 时配置写入文件系统。
4. 不同 `userId`、不同 `businessKey` 的方案互不覆盖。
5. 旧的 `enableSavedQuery` 和 `/saved-query` 不被破坏。

## 当前验证记录

2026-05-24：

1. `addons/foggy-data-viewer/frontend`: `npm test -- --run` 通过，10 个测试文件、258 个测试。
2. `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。
3. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer -DskipTests=false` 通过，56 个测试。
4. `addons/foggy-data-viewer/frontend`: `npx vue-tsc -p tsconfig.json --noEmit` 未通过，失败点仍为既有 Vue 严格类型问题，错误列表未包含新增 `ListPresetManager.vue` 或 `api/listPreset.ts`。
5. `addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/DataTableWithSearch.test.ts src/components/DataTable.test.ts` 通过，2 个测试文件、108 个测试。
6. `addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/list-preset/ListPresetManager.test.ts src/components/DataTableWithSearch.test.ts` 通过，2 个测试文件、72 个测试。
7. `addons/foggy-data-viewer/frontend`: `npm test -- --run` 通过，11 个测试文件、263 个测试。
8. `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。
9. `addons/foggy-data-viewer/verification-app`: 已新增“自定义列表（新）”验证场景；`npm run build` 当前未通过，原因是 verification-app 的 `vue-tsc -b` 会直接检查 `file:../frontend` 源码，现有 `@` alias 无法在 verification-app 类型检查上下文解析，并伴随既有严格类型错误。该问题不影响组件库 `build:lib` 结果，后续需单独整理 verification-app 的类型检查配置或改为依赖已构建产物。

2026-05-24 晚间补充：

1. `addons/foggy-data-viewer/verification-app`: 已通过本地模块类型 shim 修复 `foggy-data-viewer` 消费侧类型链路；`npm run build` 通过。构建仅保留 Vite 大 chunk warning。
2. `addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/list-preset/ListPresetManager.test.ts src/components/DataTableWithSearch.test.ts` 通过，2 个测试文件、78 个测试。
3. `addons/foggy-data-viewer/frontend`: `npm test -- --run` 通过，11 个测试文件、269 个测试。错误路径用例会按预期打印 stderr，不影响结果。
4. `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。
5. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer -Dtest=ListPresetControllerTest` 通过，10 个测试。
6. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer` 通过，66 个测试。

2026-05-24 夜间补充：

1. `addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/list-preset/ListPresetManager.test.ts` 通过，9 个测试。
2. `addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/list-preset/ListPresetManager.test.ts src/components/DataTableWithSearch.test.ts` 通过，2 个测试文件、80 个测试。
3. `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。
4. `addons/foggy-data-viewer/verification-app`: `npm run build` 通过。构建仅保留 Vite 大 chunk warning。
5. 文档更新：`addons/foggy-data-viewer/frontend/USAGE.md` 已新增自定义列表接入说明；`docs/8.6.0.beta/foggy-data-viewer-custom-list-acceptance-checklist.md` 已新增验收清单。

2026-05-24 后端字段校验扩展补充：

1. `addons/foggy-data-viewer`: 已新增 `ListPresetFieldValidator`，默认 no-op，不强制依赖 QM schema 服务。
2. `ListPresetService` 在 create/update 时调用字段校验器；校验器抛 `IllegalArgumentException` 后由 Controller 转为业务失败响应。
3. `DataViewerAutoConfiguration` 已提供默认 no-op Bean，接入方可注册同类型 Bean 覆盖。
4. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer -Dtest=ListPresetServiceTest` 通过，9 个测试。
5. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer` 通过，69 个测试。

2026-05-24 收口记录：

1. 实现质量闸门已完成：`docs/8.6.0.beta/quality/foggy-data-viewer-custom-list-implementation-quality.md`。
2. 测试覆盖审计已完成：`docs/8.6.0.beta/coverage/foggy-data-viewer-custom-list-coverage-audit.md`。
3. 验收签收记录已完成：`docs/8.6.0.beta/acceptance/foggy-data-viewer-custom-list-acceptance.md`。
4. `addons/foggy-data-viewer/verification-app`: `npm run build` 通过，Vite 大 chunk 提示作为演示应用体积风险记录。

2026-05-24 P2 推进补充：

1. `addons/foggy-data-viewer/verification-app`: 已新增 Playwright e2e，用 mock 后端覆盖保存、设默认、应用、删除、刷新后默认方案先加载再查数的浏览器级流程。
2. `addons/foggy-data-viewer/verification-app`: `npm run test:e2e -- --project=chromium` 通过，1 个浏览器流程用例。
3. `addons/foggy-data-viewer/verification-app`: Playwright 端口固定为 `53174` 且禁用已有服务复用，避免误连其他本地应用。
4. `addons/foggy-data-viewer`: 已新增 `MongoListPresetStoreIntegrationTest`，真实 Mongo 读写测试通过 `FOGGY_DATA_VIEWER_MONGO_IT=true` 显式开启；默认测试跳过，符合“无 Mongo 可退化文件系统”的验证策略。
5. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer` 通过，76 个测试，Mongo opt-in 用例默认跳过 3 个。
6. 后端错误语义补充：Controller 覆盖缺失 preset 的 404；Service 覆盖空 model 与 update 空 columns 拒绝。

2026-05-24 存储策略与前端状态收口：

1. `foggy.data-viewer.list-preset.storage` 已新增 `AUTO | MONGO | FILE`。默认 `AUTO` 仅在配置了 `spring.data.mongodb.uri` 时使用 Mongo；没有 Mongo URI 时直接使用文件系统。
2. 显式 `storage=MONGO` 时保持 Mongo 优先，并在运行时不可用时降级到文件系统；显式 `storage=FILE` 时只使用文件系统。
3. `DataTableWithSearch` 应用默认/手动列表方案后，会把 preset 的 `slice` 同步传给 `DataTable.initialSlice`，保证表头筛选显示与实际查询状态一致。
4. `DataTable` 在 `initialSlice` 变为空数组时会清空内部过滤状态，避免应用空筛选方案后界面残留旧过滤值。
5. `row-actions` 注入的 `_actions` 合成列不再写入自定义列表配置，避免保存业务无关列。
6. 验证：`addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer "-Dtest=DataViewerAutoConfigurationTest,ListPresetServiceTest,ListPresetControllerTest"` 通过，26 个测试。
7. 验证：`addons/foggy-data-viewer/frontend`: `npm test -- --run src/components/DataTable.test.ts src/components/DataTableWithSearch.test.ts src/components/list-preset/ListPresetManager.test.ts` 通过，3 个测试文件、123 个测试。
8. 验证：`addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer` 通过，79 个测试，Mongo opt-in 用例默认跳过 3 个。
9. 验证：`addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。

2026-05-24 最终推进 1~5 验证：

1. `addons/foggy-data-viewer/frontend`: `npm test -- --run` 通过，11 个测试文件、273 个测试。错误路径用例会按预期打印 stderr，不影响结果。
2. `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。
3. `addons/foggy-data-viewer`: `mvn test -pl addons/foggy-data-viewer` 通过，79 个测试，0 失败，0 错误，Mongo opt-in 用例默认跳过 3 个。
4. `addons/foggy-data-viewer`: 使用 `FOGGY_DATA_VIEWER_MONGO_IT=true` 和 `FOGGY_DATA_VIEWER_MONGO_URI=mongodb://localhost:17017/foggy_data_viewer_it` 执行 `mvn test -pl addons/foggy-data-viewer -Dtest=MongoListPresetStoreIntegrationTest` 通过，3 个真实 Mongo 读写测试。
5. `addons/foggy-data-viewer/verification-app`: `npm run build` 通过，保留 Vite 大 chunk warning。
6. `addons/foggy-data-viewer/verification-app`: `npm run test:e2e -- --project=chromium` 通过，1 个 Chromium 浏览器流程用例，覆盖保存、应用、设默认、删除和刷新后默认方案恢复。
7. 最小接入说明已补充：`docs/8.6.0.beta/foggy-data-viewer-custom-list-integration-guide.md`。
