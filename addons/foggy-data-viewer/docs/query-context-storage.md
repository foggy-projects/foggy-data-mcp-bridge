# DataViewer 查询上下文存储

`dataset.open_in_viewer` 保存短期查询定义（模型、DSL、列配置和过期时间），不保存查询结果或原始 Authorization。浏览器根据 query ID 读取定义后，仍由查询引擎执行数据请求。

默认使用 SQLite 保存短期查询上下文，不需要 MongoDB。建议为每个实例配置独立且仅服务进程可访问的持久文件路径：

```yaml
foggy:
  data-viewer:
    cache:
      sqlite-path: /private/path/data-viewer-query-cache.sqlite
      ttl-minutes: 120
```

未配置路径时使用系统临时目录中的 `foggy-data-viewer-query-cache.sqlite`；正式环境应设置持久路径。SQLite 文件与业务数据源分开。读取时会检查过期时间；启动和后续写入时清理过期记录。交接码兑换时仅延长仍有效、模型和命名空间均匹配的查询。存储后端在启动时确定，不会在故障时自动切换，以免已签发的链接找不到上下文。

SQLite 模式不会注册 Mongo 保存查询接口；列表预设默认保存在文件系统中。需要共享查询上下文的多实例部署可显式设置 `foggy.data-viewer.cache.storage=mongo`，并单独选择列表预设后端。演示登录的交接码与会话由 Launcher 保存在内存中，因此 Launcher 重启后需要重新生成登录链接，即使 SQLite 中的查询定义尚未过期。多实例部署仍应使用 MongoDB 或另外提供共享存储及会话方案。
