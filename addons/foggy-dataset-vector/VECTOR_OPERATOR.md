# 向量查询操作符说明

## 推荐操作符：`~` (波浪号)

### 语义
`~` 表示"相似"、"近似"，用于向量数据库的语义相似度检索。

### 使用方式

#### 方式 1：使用 `~` 操作符（推荐）
```json
{
  "model": "QueryTemplateVectorQueryModel",
  "slice": [
    {
      "name": "content",
      "type": "~",
      "value": "最近一周各品牌销售情况"
    }
  ],
  "limit": 5
}
```

#### 方式 2：使用 query 字段（兼容）
```json
{
  "model": "QueryTemplateVectorQueryModel",
  "slice": [
    {
      "name": "query",
      "type": "=",
      "value": "最近一周各品牌销售情况"
    }
  ],
  "limit": 5
}
```

### 为什么选择 `~`？

1. **语义清晰**:
   - `~` 在数学中表示"近似等于"
   - 在计算机科学中常表示"相似"、"匹配"
   - PostgreSQL 使用 `~` 表示正则匹配

2. **简洁易用**:
   - 单字符操作符，易于输入
   - 不与现有操作符冲突

3. **符合直觉**:
   - `=` 表示精确匹配
   - `like` 表示模糊匹配
   - `~` 表示相似度匹配

### 操作符对比

| 操作符 | 含义 | 适用场景 |
|--------|------|----------|
| `=` | 精确等于 | 关系数据库精确查询 |
| `like` | 模糊匹配 | 关系数据库字符串匹配 |
| `in` | 包含于 | 关系数据库集合查询 |
| `[]` | 范围 | 关系数据库区间查询 |
| **`~`** | **相似** | **向量数据库语义检索** |

### AI 使用示例

AI 可以这样理解和使用：

```
用户问："找一下类似的销售查询"

AI 生成 DSL：
{
  "model": "QueryTemplateVectorQueryModel",
  "slice": [
    {
      "name": "content",
      "type": "~",
      "value": "销售查询"
    }
  ],
  "limit": 5
}
```

### 实现细节

在 `CondType` 枚举中添加：
```java
@ApiModelProperty(value = "向量相似度查询", notes = "用于向量数据库的语义相似度检索")
SIMILAR("~");
```

在 TM 文件中解析：
```javascript
export function buildQuery(params) {
    const sliceConditions = params.slice || [];

    // 查找使用 ~ 操作符的条件
    const similarCondition = sliceConditions.find(s => s.type === '~');
    if (similarCondition) {
        return similarCondition.value;
    }

    return '';
}
```

### 其他备选方案

如果 `~` 不合适，可以考虑：

1. **`similar`** - 语义明确但较长
   ```json
   {"name": "content", "type": "similar", "value": "查询文本"}
   ```

2. **`vector`** - 明确表示向量查询
   ```json
   {"name": "content", "type": "vector", "value": "查询文本"}
   ```

3. **`semantic`** - 强调语义查询
   ```json
   {"name": "content", "type": "semantic", "value": "查询文本"}
   ```

但综合考虑，**`~` 是最佳选择**。
