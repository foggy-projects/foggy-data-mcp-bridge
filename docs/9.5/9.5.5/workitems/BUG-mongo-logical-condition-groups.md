---
doc_type: bug
intended_for: developer / reviewer
purpose: 记录 Mongo 条件组在工单跨字段搜索中的失败、最小修复与验证。
status: IMPLEMENTED_LOCAL_VERIFICATION_PASSED
---

# Mongo 逻辑条件组执行

TMS APP 异常工单使用固定租户/工单范围并在多个标识字段间搜索。Mongo 的 slice 递归曾将子条件追加到外层，随后将 JdbcGroupCond 强转为 QueryTypeValueCond，真实请求返回类型转换错误。

修复将叶子保留在所属组，携带 AND/OR 连接符，递归构建 Mongo Criteria；同字段多个条件以显式逻辑数组保存。未知条件、空组和空 membership 显式失败，不能静默移除过滤条件。既有 Mongo like 正则语义保持，调用者负责转义用户文字。

`mvn -o -pl addons/foggy-dataset-model-mongo -am install -Dtest=MongoLogicalConditionTest -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false` 通过，3 项定向回归覆盖作用域与嵌套 OR/AND、重复字段和 null membership、非法条件失败。下游本地测试 runtime 已验证嵌入 jar 哈希并完成 4 项真实 Mongo Query：类型+单号命中、缺失类型+摘要命中、AND 排除不匹配数据、字面正则元字符无误匹配。分别返回 1/1/0/0 行，与两个授权测试记录的预期一致。本记录不代表新 provider 能力声明或正式环境发布。
