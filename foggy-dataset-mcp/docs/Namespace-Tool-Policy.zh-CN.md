# Namespace MCP 工具策略

Java MCP Bridge 会在当前 `X-NS` 对应的 bundle 中查找唯一的 `tools.config.js`。没有该文件时，所有已注册且符合既有角色规则的工具均保持开放；配置加载或执行失败时也默认放行。

可直接复制随模块发布的 [`tools.config.js` 模板](../src/main/resources/examples/namespace/tools.config.js) 到 namespace 模型 bundle 根目录。模板中的 `policyUrl` 默认为 `null`，因此默认开放既有角色允许的全部工具；填写策略服务 URL 后才会执行远程 `post`。

脚本使用 Foggy FSScript 语法，`export default` 可以是工具名数组，也可以是接收请求上下文的函数。返回 `[]` 表示不开放任何工具，返回 `["*"]` 表示开放全部已注册工具；未知工具名会被忽略。策略同时作用于 `tools/list`、`tools/call`、直接工具调用和 SSE 调用。

静态示例：

```javascript
export default [
    "dataset.query_model",
    "dataset.explain_query"
];
```

调用上游策略服务的示例：

```javascript
const resolveTools = function(context) {
    const result = post({
        url: "https://policy.example.com/foggy/tools",
        headers: {
            Authorization: context.authorization,
            "Content-Type": "application/json"
        },
        data: {
            namespace: context.namespace,
            userRole: context.userRole,
            registeredTools: context.registeredTools
        },
        returnClass: "java.util.Map"
    });
    return result.enabledTools;
};

export default resolveTools;
```

函数上下文包含 `namespace`、`authorization`、`userRole`、`traceId`、`headers` 和 `registeredTools`。原始 token 只在本次脚本执行中提供，不进入工具策略缓存，也不会由策略服务记录到日志；是否通过 `post` 转发由 namespace 管理员决定。

`post` 复用平台 FSScript HTTP 客户端的连接/请求超时、响应大小限制和禁止跨源重定向转发敏感头等约束。策略只能从 Java 进程已注册的工具中筛选，不能通过脚本注册新工具。
