# S4 开发日志：external patch 合并

## 基本信息
- 日期：`2026-04-02`
- 阶段：`阶段4：外部权限 patch 合并`
- 记录人：`Codex`

## 本阶段目标
- 为 synthetic member-QM 定义统一的 external patch 结构。
- 让 DSL 入口与 simple 入口都能命中同一套 patch 合并逻辑。
- 在不引入内部角色权限体系的前提下，完成 `visibleColumns / forcedSlice / forcedOrderBy` 的求交与合并。

## 核心修改文件
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/member/SyntheticMemberExternalPatch.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/SyntheticMemberExternalPatchStep.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/common/query/DimensionDataQueryForm.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/JdbcServiceImpl.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/member/SyntheticMemberQueryModelRuntimeTest.java`

## 关键改动点
- 新增 `SyntheticMemberExternalPatch`，统一承载：
  - `visibleColumns`
  - `forcedSlice`
  - `forcedOrderBy`
- 新增 `SyntheticMemberExternalPatchStep`，并通过 `@Order(-10)` 放在请求校验前执行。
- patch 仅对 synthetic member-QM 生效，普通 QM 查询不受影响。
- 支持两类 extData 输入：
  - 直接传入 `SyntheticMemberExternalPatch`
  - 传入 `Map`，并兼容嵌套在 `syntheticMemberPatch` 节点下
- simple 入口新增 `extData` 透传，最终和 DSL 入口一起进入同一 patch 合并步骤。

## 关键设计决策
- 不在 Foggy 内部解释外部系统的角色、组织、租户模型，只接收已翻译完成的 patch。
- `visibleColumns` 只负责字段白名单求交，不负责表达“为什么可见”。
- `forcedSlice` 与请求 `slice` 采用 `AND` 合并，避免外部权限条件被客户端绕过。
- `forcedOrderBy` 允许覆盖同字段的请求排序，并在最终排序列表中稳定落位。
- 如果请求列被全部裁空，直接返回明确提示，不静默放开。

## 关键执行日志
- 典型 visibleColumns 求交：
```text
patch.visibleColumns=[id, caption]
request.columns=[id, caption, productCategory$caption]
effective.columns=[id, caption]
```

- 典型 forcedSlice 合并：
```text
forcedSlice = brand = 'Apple'
request.slice = caption like '%iPhone%'
effective.slice = (brand = 'Apple') AND (caption like '%iPhone%')
```

- 典型 forcedOrderBy 覆盖：
```text
request.orderBy = brand DESC
forcedOrderBy = brand ASC
effective.orderBy = brand ASC
```

## 问题与取舍
- 当前 synthetic 判定先按模型名包含 `#` 识别，满足 MVP，但后续如果存在更多派生模型类型，建议补显式 marker。
- 当前 patch 解析使用本地 `ObjectMapper`，实现简单；如果后续 patch 结构继续扩展，再考虑统一对象映射入口。

## 关联测试记录
- `T4-20260402-external-patch验证.md`

## 阶段状态
- Stage4 本地验收通过，可进入 Stage5。
