# 自定义查询配置与执行

Schema 模式的 `DataTableWithSearch` 在每次搜索、分页、排序、刷新、加载方案时，复制用户配置并整理执行参数，然后进入原有 search/query hooks 和 `fetchData`。

- 用户最多选择 50 个显示字段、配置 20 条条件。空条件也占配置名额；重复字段允许，按 AND/OR 原样执行。
- 空值条件不提交，配置本身保留。`0`、`false`、`is null`、`is not null` 保留；半填范围提示错误。
- 维度使用元数据声明的 selectionFieldName 提交 ID；字典提交 code，多选生成 IN。
- `fixedSlice` 在所有查询 hooks 后、调用页面 fetchData 前追加，顶层按 AND 合并。它不进入方案，不占用户条件配额。
- `requiredRuntimeColumns` 补入执行字段；隐式依赖不显示、不保存、不占用户字段配额。

```vue
<DataTableWithSearch
  :schema="schema"
  :fetch-data="fetchData"
  :fixed-slice="signedOnly"
  :required-runtime-columns="['id']"
  query-time-zone="Asia/Shanghai"
  :list-preset="{ userId: currentUserId, model: qmModel, businessKey: 'signed-orders' }"
/>
```

`signedOnly` 由业务页面构造，例如 `[{ field: 'status', op: '=', value: signedStatusCode }]`。不要同时在 initialSlice、方案或其他 hook 中重复添加它。页面 fetchData 如需修改请求，应保留收到的业务条件。组件约束用于 UI 操作，数据权限仍由后端负责。

## 相对日期

方案条件保存 UI 值对象，例如 `{ field: 'createdAt', op: '[)', value: { $relativeDate: 'today', dateTime: true } }`。每次执行前转换为普通 `[start, end)`，该对象不会进入查询引擎。现有 list-preset API 可以持久化此 value 对象；读取它的客户端需要支持这套转换。

快捷项包括今天、昨天、近三天、近一周、本周、上周、近两周、本月、上月、近一个月，不提供明天选项。具体口径：

- 近 N 天包含今天，结束边界为明天零点。
- 自然周从周一开始，本周/本月使用完整自然周期的边界。
- 近一个月以明天零点为结束，向前移一个日历月，月末日期按目标月末截断。
- `queryTimeZone` 决定“今天”的业务日期，默认浏览器时区；请按后端业务日历显式配置。输出为业务当地日期/时间文本，不带 UTC 偏移。
- 日期输出 `yyyy-MM-dd`；DateTime 输出 `yyyy-MM-dd HH:mm:ss`。

## 方案保存

继续使用现有 list-preset API。保存表单新增“保存后立即应用”，默认选中；保存成功后应用服务端返回的方案并通过现有 reload 入口查询。取消勾选则只保存。空条件和相对日期保留在配置中，固定条件与隐式运行字段不进入方案。

后端已有 Mongo/文件存储选择，无需为这些 UI 规则修改后端或查询引擎。生产环境的 Mongo 连接和认证用户映射由宿主应用配置。

## 受控模式

受控模式由页面管理请求，使用公开的 `prepareCustomQuery` 完成相同的用户参数整理：

```ts
import { prepareCustomQuery } from 'foggy-data-viewer'

const userQuery = prepareCustomQuery(tableRef.value.getListViewState(), {
  timeZone: 'Asia/Shanghai'
})
// 随后进入页面业务 hooks / fetchData，并追加业务条件和执行依赖。
await fetchData({
  page,
  pageSize: userQuery.pageSize ?? 50,
  columns: [...new Set([...userQuery.columns, ...requiredRuntimeColumns])],
  slice: [...userQuery.slice, ...signedOnly],
  orderBy: userQuery.orderBy
})
```

不要将转换后的请求写回方案状态。Schema 模式使用组件的 refresh/reload；直接调用高级 getQuery().loadData 会绕开组件的用户配置整理入口。
