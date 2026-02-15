# description_model_internal

获取指定数据模型的完整元数据定义，包含字段详情、数据类型、字典映射。

## 返回内容
```json
{
  "model": "模型名称",
  "description": "模型描述",
  "fields": [
    {"name": "字段名", "type": "数据类型", "description": "描述", "dictionary": "字典名", "values": {...}}
  ],
  "dictionaries": {...}
}
```

## 字段后缀说明
| 字段类型 | 后缀 | 说明 |
|---------|------|------|
| 维度 | `$id` | 返回ID值 |
| 维度 | `$caption` | 返回显示名称 |
| 字典 | 无/`$id`/`$caption` | 原始值/字典值/字典名称 |
| 度量 | - | 用于聚合(SUM/AVG/MAX/MIN/COUNT/COUNT_DISTINCT/STDDEV/VAR) |
| 计算字段 | - | QM预定义的公式/窗口计算字段 |

## 使用场景
- 查询前确认字段名和数据类型
- 查看字典字段的可选值
- 了解模型数据结构
