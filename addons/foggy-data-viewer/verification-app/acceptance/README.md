# 自定义查询本地验收

本环境仅使用模拟运单与隔离 JSON 文件存储，不连接 TMS，也不证明生产 Mongo 或权限链路已验收。

在 `../frontend` 执行 `npm install`、`npm test`、`npm run build`；返回 verification-app 执行 `npm install --ignore-scripts`，然后：

```sh
npm run dev -- --config vite.acceptance.config.ts
```

访问 http://127.0.0.1:53175/acceptance/。个人方案保存至 verification-app/.acceptance/presets.json（Git 忽略）。用户切换只用于验证前端上下文和模拟 API 隔离，不模拟正式认证。

## 人工回归流程

1. 查询方案 → 自定义查询：检查三步向导、字段搜索/类型筛选、选择/删除、拖动或 Alt+上下调整、列宽/固定设置。55 个可用字段全选应提示超过50。
2. 下一步：添加开单日期“今天”，重复添加两个运单号并留空；付款方式多选寄付/到付；新增 OR 组并添加始发站点，选择杭州/上海。空组应提示补充或删除；空叶条件可保留。
3. 下一步：输入方案名/说明，保持立即应用选中并保存。检查最近一次请求：日期是具体边界；空运单号被剔除；付款方式是 PREPAID/COLLECT；站点是 origin$id 的101/102；status=SIGNED 固定规则仍在；runtimeId 仅在执行 columns 中。
4. 重载页面后加载方案，应恢复上述行为。编辑方案应回填今天、两个空条件、字典名称和站点名称，不得用当前表格条件覆盖存量方案。核对隔离 JSON 中保留相对日期/空叶，不包含固定签收规则或 runtimeId。
5. 用户B → 加载查询：不显示用户A方案；切回A可加载。清空用户查询后仍有 status=SIGNED。

正式 TMS 提测时还需验证实际元数据、业务 hooks/fetchData、登录用户映射、Mongo 持久化及后端权限。
