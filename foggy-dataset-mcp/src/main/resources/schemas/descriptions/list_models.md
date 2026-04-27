# list_models

这是发现所有可用模型的首选工具。

## 工具职责
- 工具只返回模型路由信息，不返回字段明细。
- 需要字段明细时调用 `dataset.describe_model_internal`。
- 不应为了首轮模型发现调用 `dataset.get_metadata`。
