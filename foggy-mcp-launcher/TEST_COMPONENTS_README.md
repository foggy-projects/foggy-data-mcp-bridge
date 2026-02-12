# 测试组件说明

## 概述

本项目包含两个用于测试保存查询功能的组件：
1. `DemoSecurityIdentityResolver` - 演示用的安全身份解析器
2. `SavedQueryTestController` - 保存查询功能测试控制器

这两个组件仅用于开发和测试，**默认不启用**，需要通过配置激活。

## 配置开关

### 配置项

```yaml
foggy:
  test:
    enabled: false  # 默认关闭
```

### 激活方式

#### 方式 1: 使用 Docker Profile（推荐）

使用 `docker` profile 启动应用，配置已自动启用：

```bash
java -jar foggy-mcp-launcher.jar --spring.profiles.active=docker
```

或使用环境变量：

```bash
export SPRING_PROFILES_ACTIVE=docker
java -jar foggy-mcp-launcher.jar
```

#### 方式 2: 通过命令行参数

```bash
java -jar foggy-mcp-launcher.jar --foggy.test.enabled=true
```

#### 方式 3: 通过环境变量

```bash
export FOGGY_TEST_ENABLED=true
java -jar foggy-mcp-launcher.jar
```

#### 方式 4: 修改配置文件

在 `application.yml` 中修改：

```yaml
foggy:
  test:
    enabled: true
```

## 组件说明

### 1. DemoSecurityIdentityResolver

**功能**: 实现 `SecurityIdentityResolver` SPI，用于从 Authorization header 解析用户身份。

**实现方式**: 简单的 mock 实现，根据 token 关键字返回不同的用户身份：

| Token 包含关键字 | userId | deptId | tenantId | Role |
|----------------|--------|--------|----------|------|
| `manager` | user_manager_001 | dept_sales | tenant_demo | MANAGER |
| `analyst` | user_analyst_001 | dept_analytics | tenant_demo | ANALYST |
| `admin` | user_admin_001 | dept_it | tenant_demo | ADMIN |
| 其他 | user_default_001 | dept_sales | tenant_demo | USER |

**使用示例**:

```bash
# 作为门店经理
curl -H "Authorization: Bearer manager-token-123" \
     http://localhost:7108/data-viewer/api/saved-query

# 作为数据分析师
curl -H "Authorization: Bearer analyst-token-456" \
     http://localhost:7108/data-viewer/api/saved-query
```

**注意**:
- 这是一个演示实现，**不适用于生产环境**
- 生产环境应实现真正的 JWT token 解析逻辑
- 实现 `SecurityIdentityResolver` 接口并注册为 Spring Bean 即可替换

### 2. SavedQueryTestController

**功能**: 提供测试端点验证 `SecurityIdentityResolver` 是否正常工作。

**端点**: `GET /test/identity`

**请求示例**:

```bash
curl -H "Authorization: Bearer manager-token-123" \
     http://localhost:7108/test/identity
```

**响应示例**:

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": "user_manager_001",
    "deptId": "dept_sales",
    "tenantId": "tenant_demo",
    "attributes": {
      "role": "MANAGER",
      "storeKey": "1",
      "userName": "张三（经理）"
    }
  }
}
```

**错误情况**:

1. SecurityIdentityResolver 未配置（foggy.test.enabled=false）:
```json
{
  "code": 500,
  "msg": "SecurityIdentityResolver 未配置"
}
```

2. 缺少 Authorization header:
```json
{
  "code": 500,
  "msg": "缺少 Authorization header"
}
```

3. 身份解析失败:
```json
{
  "code": 500,
  "msg": "身份解析失败"
}
```

## 验证测试组件是否启用

### 方法 1: 检查日志

启动应用时，如果测试组件已启用，会看到以下日志：

```
ConditionalOnProperty matched: foggy.test.enabled=true
Creating bean 'demoSecurityIdentityResolver'
Creating bean 'savedQueryTestController'
```

### 方法 2: 调用测试端点

```bash
curl -H "Authorization: Bearer test" http://localhost:7108/test/identity
```

如果返回 404，说明组件未启用。
如果返回用户身份信息，说明组件已启用。

### 方法 3: 检查 Actuator

访问 `/actuator/beans` 查看是否包含以下 Bean：
- `demoSecurityIdentityResolver`
- `savedQueryTestController`

## 完整测试流程

### 1. 启动应用（启用测试组件）

```bash
# 使用 Docker profile
java -jar foggy-mcp-launcher.jar --spring.profiles.active=docker

# 或直接启用配置
java -jar foggy-mcp-launcher.jar --foggy.test.enabled=true
```

### 2. 测试身份解析

```bash
# 测试不同角色
curl -H "Authorization: Bearer manager-token-123" \
     http://localhost:7108/test/identity

curl -H "Authorization: Bearer analyst-token-456" \
     http://localhost:7108/test/identity

curl -H "Authorization: Bearer admin-token-789" \
     http://localhost:7108/test/identity
```

### 3. 测试保存查询功能

```bash
# 保存查询（作为门店经理）
curl -X POST http://localhost:7108/data-viewer/api/saved-query \
  -H "Authorization: Bearer manager-token-123" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "我的销售查询",
    "description": "测试查询",
    "columns": ["date", "product", "amount"],
    "slice": [{"field": "date", "op": ">=", "value": "2024-01-01"}],
    "visibility": "PRIVATE"
  }'

# 列出查询
curl -H "Authorization: Bearer manager-token-123" \
     http://localhost:7108/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel
```

## 生产环境使用

**重要**: 在生产环境中：

1. **不要启用** `foggy.test.enabled` 配置
2. **实现自己的** `SecurityIdentityResolver`，解析真实的 JWT token
3. **删除或移除** `DemoSecurityIdentityResolver` 和 `SavedQueryTestController`

### 自定义 SecurityIdentityResolver 示例

```java
@Component
public class JwtSecurityIdentityResolver implements SecurityIdentityResolver {

    @Autowired
    private JwtTokenParser jwtTokenParser;

    @Override
    public ResolvedIdentity resolve(String authorization) {
        // 移除 "Bearer " 前缀
        String token = authorization.replace("Bearer ", "");

        // 解析 JWT token
        Claims claims = jwtTokenParser.parse(token);

        // 提取用户信息
        String userId = claims.get("userId", String.class);
        String deptId = claims.get("deptId", String.class);
        String tenantId = claims.get("tenantId", String.class);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("userName", claims.get("userName", String.class));
        attributes.put("role", claims.get("role", String.class));

        return new ResolvedIdentity(userId, deptId, tenantId, attributes);
    }
}
```

## 故障排除

### 问题 1: 保存查询返回 503 错误

**原因**: SecurityIdentityResolver 未配置或未启用测试组件。

**解决**:
```bash
# 确认配置已启用
java -jar foggy-mcp-launcher.jar --foggy.test.enabled=true
```

### 问题 2: /test/identity 返回 404

**原因**: 测试组件未启用。

**解决**: 参考"激活方式"部分启用配置。

### 问题 3: 身份解析返回 null

**原因**: Authorization header 格式不正确或为空。

**解决**:
```bash
# 确保 Authorization header 包含有效值
curl -H "Authorization: Bearer manager-token-123" ...
```

## 总结

- ✅ 测试组件默认关闭，需要显式启用
- ✅ Docker profile 自动启用测试组件
- ✅ 生产环境应使用真实的 JWT 解析实现
- ✅ 提供测试端点验证配置是否生效
