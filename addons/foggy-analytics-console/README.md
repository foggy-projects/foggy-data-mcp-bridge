# Foggy Analytics Console

独立的 Analytics 产品 Console。它面向 `ADMIN` / `DESIGNER` 完成 Report、Dashboard 草稿设计、
校验、预览和单向发布；`VIEWER` 只消费已发布渲染结果。它不替代 FAP Workbench 或 Runtime Console。

## 装配

模块默认不进入 launcher。开发环境可显式使用 launcher 的 `analytics-console` Maven profile，生产宿主
也可以只依赖本模块和 `foggy-analytics-runtime-api`：

```xml
<dependency>
  <groupId>com.foggysource</groupId>
  <artifactId>foggy-analytics-console</artifactId>
  <version>9.3.0-SNAPSHOT</version>
</dependency>
```

最小本地配置示例：

```yaml
foggy:
  analytics:
    runtime-api:
      enabled: true
      bundles:
        - ref: console-drafts
          path: /absolute/development/path/console-drafts
          source-state: RUNTIME_OWNED
  analytics-console:
    enabled: true
    security-mode: static-dev-only
    catalog-path: /absolute/development/path/console-catalog.json
```

`static-dev-only` 仅用于本机开发。生产必须保留默认 `host-managed` 并提供
`AnalyticsConsoleSubjectResolver` Bean，从已认证的宿主请求解析 subject、产品角色以及不透明的数据权限
authority；浏览器提交的 owner、role、authority 均不会被信任。

入口：

- Web：`/analytics-console/`
- 产品 API：`/analytics-console/api/v1`
- Analytics Runtime API：`/analytics/api/v1`

产品写请求必须携带 `X-Foggy-Analytics-Console-Request: 1`。内置前端已自动附加该请求头。

## FAP 可选桥接

FAP 默认关闭。启用时还必须由宿主提供 `AnalyticsConsoleFapBindingResolver`，并配置以下服务端属性：

```yaml
foggy:
  analytics-console:
    fap:
      enabled: true
      base-url: https://fap.internal.example
      provider-ref: analytics-console-provider
      skill-name: analytics-design-guidance
      capability-name: analytics.design-read
      callback-capability-id: analytics.design-read
      callback-capability-revision: 1
      callback-authorization: ${ANALYTICS_CONSOLE_FAP_CALLBACK_AUTHORIZATION}
      timeout-seconds: 30
```

`capability-name` 是提交 Ask 时由服务端冻结的选择；callback ID/revision 是入站回调的 exact contract
guard。Resolver 返回的 FAP credential、workspace/model binding 和数据 authority 不能写入 catalog 或 API
响应。Console 只保存不透明的 Ask/Execution/Task 关联，不保存 prompt。

## 边界

- Java Analytics Bundle store 是定义和 revision 的技术真相；Console catalog 只保存产品 owner、目录、
  展示 ACL、状态和不透明 FAP 关联。
- Viewer 详情不返回定义内容，只能使用受治理的 preview/render。
- 数据权限继续由当前 subject 对应的 QM/TM authority 执行；Console ACL 不能作为数据过滤条件。
- TMS 使用自己的发布表和 Function SDK adapter；它不依赖本 Console，也不与 Console 同步元数据。
- 当前 JSON catalog 是单进程 MVP store。多实例部署前必须替换 `AnalyticsConsoleCatalogRepository`，不能让
  多个进程共享写同一个 catalog 文件。

CLI 不复用 Console 产品 API：`foggy runtime` 继续连接 Foggy Runtime API v1，`foggy analytics`
连接独立 Analytics Runtime API v1。
