---
name: backend-dsl-query
description: 引导后端开发使用 DSL 查询数据。生成 Java Service 层查询代码。当用户需要在后端封装查询逻辑时使用。
---

# Backend DSL Query

引导后端开发人员使用 DSL 查询数据，生成 Java Service 层封装代码。

## 使用场景

当用户需要以下操作时使用：
- 在 Service 层封装 DSL 查询逻辑
- 根据业务需求生成查询方法（如 `getUserById`、`getOrderList`）
- 了解后端 DSL 查询 API 使用方式

## 前置条件

项目需要已集成 `foggy-dataset-model` 依赖（参考 `/foggy-java-integration` 技能）。

## 生成的文件结构

```
src/main/java/com/{package}/
├── service/
│   └── query/
│       ├── UserQueryService.java        # Service 接口
│       └── impl/
│           └── UserQueryServiceImpl.java # Service 实现
```

## 执行流程

### 第一步：了解用户需求

询问用户要生成的查询方法：
- 查询哪个模型？（使用 `qm-schema-viewer` 查看可用模型）
- 接收哪些参数？（如 userId、startDate、endDate）
- 返回哪些字段？
- 是否需要分页？
- Service 类名称？

### 第二步：确定包名和路径

1. 检查项目 `pom.xml` 或现有代码确定基础包名
2. 默认使用 `{basePackage}.service.query`
3. 询问用户确认或自定义

### 第三步：生成 Service 接口

根据用户需求生成 Service 接口。

#### 示例：根据 userId 查询用户信息

```java
package com.example.service.query;

import com.foggyframework.dataset.db.model.def.result.PagingResultImpl;
import java.util.List;

/**
 * 用户查询服务
 */
public interface UserQueryService {

    /**
     * 根据用户ID查询用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    UserDTO getUserById(Long userId);

    /**
     * 查询用户列表
     * @param params 查询参数
     * @return 用户列表（分页）
     */
    PagingResultImpl<UserDTO> getUserList(UserQueryParams params);
}
```

### 第四步：生成 Service 实现

生成基于 `QueryFacade` 或 `JdbcService` 的实现类。

#### 实现方式 A：使用 QueryFacade（推荐）

```java
package com.example.service.query.impl;

import com.example.service.query.UserQueryService;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.def.result.PagingResultImpl;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.util.PagingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户查询服务实现
 */
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final QueryFacade queryFacade;

    @Override
    public UserDTO getUserById(Long userId) {
        // 构建查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("UserQueryModel");

        // 设置查询列
        queryRequest.setColumns(List.of(
            "userId", "userName", "email", "phone", "createTime", "status"
        ));

        // 设置过滤条件
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("userId");
        slice.setOp("=");
        slice.setValue(userId);
        queryRequest.setSlice(List.of(slice));

        // 创建分页请求
        PagingRequest<DbQueryRequestDef> form =
            PagingRequest.buildPagingRequest(queryRequest, 1);

        // 执行查询
        PagingResultImpl result = queryFacade.queryModelData(form);

        if (result.getItems().isEmpty()) {
            return null;
        }

        // 转换为 DTO
        Map<String, Object> row = (Map<String, Object>) result.getItems().get(0);
        return convertToUserDTO(row);
    }

    @Override
    public PagingResultImpl<UserDTO> getUserList(UserQueryParams params) {
        // 构建查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("UserQueryModel");

        // 设置查询列
        queryRequest.setColumns(List.of(
            "userId", "userName", "email", "phone", "createTime", "status"
        ));

        // 构建动态过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();

        if (params.getUserName() != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("userName");
            slice.setOp("like");
            slice.setValue(params.getUserName());
            slices.add(slice);
        }

        if (params.getStatus() != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("status");
            slice.setOp("=");
            slice.setValue(params.getStatus());
            slices.add(slice);
        }

        if (params.getStartDate() != null && params.getEndDate() != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("createTime");
            slice.setOp("[)");
            slice.setValue(List.of(params.getStartDate(), params.getEndDate()));
            slices.add(slice);
        }

        queryRequest.setSlice(slices);

        // 设置排序
        OrderRequestDef order = new OrderRequestDef();
        order.setField("createTime");
        order.setDir("DESC");
        queryRequest.setOrderBy(List.of(order));

        // 设置分页
        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(queryRequest);
        form.setStart((params.getPage() - 1) * params.getPageSize());
        form.setLimit(params.getPageSize());

        // 执行查询
        PagingResultImpl result = queryFacade.queryModelData(form);

        // 转换结果
        List<UserDTO> users = result.getItems().stream()
            .map(item -> convertToUserDTO((Map<String, Object>) item))
            .collect(Collectors.toList());

        result.setItems(users);
        return result;
    }

    /**
     * 转换为 DTO
     */
    private UserDTO convertToUserDTO(Map<String, Object> row) {
        UserDTO dto = new UserDTO();
        dto.setUserId(((Number) row.get("userId")).longValue());
        dto.setUserName((String) row.get("userName"));
        dto.setEmail((String) row.get("email"));
        dto.setPhone((String) row.get("phone"));
        dto.setCreateTime((Date) row.get("createTime"));
        dto.setStatus((String) row.get("status"));
        return dto;
    }
}
```

#### 实现方式 B：使用 JdbcService

```java
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final JdbcService jdbcService;

    @Override
    public UserDTO getUserById(Long userId) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("UserQueryModel");
        queryRequest.setColumns(List.of("userId", "userName", "email"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("userId");
        slice.setOp("=");
        slice.setValue(userId);
        queryRequest.setSlice(List.of(slice));

        PagingRequest<DbQueryRequestDef> form =
            PagingRequest.buildPagingRequest(queryRequest, 1);

        PagingResultImpl result = jdbcService.queryModelData(form);

        if (result.getItems().isEmpty()) {
            return null;
        }

        return convertToUserDTO((Map<String, Object>) result.getItems().get(0));
    }
}
```

### 第五步：生成辅助类（可选）

#### DTO 类

```java
package com.example.dto;

import lombok.Data;
import java.util.Date;

/**
 * 用户数据传输对象
 */
@Data
public class UserDTO {
    private Long userId;
    private String userName;
    private String email;
    private String phone;
    private Date createTime;
    private String status;
}
```

#### 查询参数类

```java
package com.example.dto;

import lombok.Data;

/**
 * 用户查询参数
 */
@Data
public class UserQueryParams {
    private String userName;
    private String status;
    private String startDate;
    private String endDate;
    private Integer page = 1;
    private Integer pageSize = 20;
}
```

### 第六步：输出使用示例

```java
// 在 Controller 或其他 Service 中使用

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;

    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    public RX<UserDTO> getUserById(@PathVariable Long id) {
        UserDTO user = userQueryService.getUserById(id);
        if (user == null) {
            return RX.notFound().build();
        }
        return RX.ok(user);
    }

    /**
     * 查询用户列表
     */
    @GetMapping
    public RX<PagingResultImpl<UserDTO>> getUserList(UserQueryParams params) {
        PagingResultImpl<UserDTO> result = userQueryService.getUserList(params);
        return RX.ok(result);
    }
}
```

## 查询模式库

### 模式 1：单条记录查询

```java
public UserDTO getUserById(Long userId) {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("UserQueryModel");
    queryRequest.setColumns(List.of("userId", "userName"));

    SliceRequestDef slice = new SliceRequestDef();
    slice.setField("userId");
    slice.setOp("=");
    slice.setValue(userId);
    queryRequest.setSlice(List.of(slice));

    PagingRequest<DbQueryRequestDef> form =
        PagingRequest.buildPagingRequest(queryRequest, 1);

    PagingResultImpl result = queryFacade.queryModelData(form);
    return result.getItems().isEmpty() ? null
        : convertToDTO((Map) result.getItems().get(0));
}
```

### 模式 2：分页列表查询

```java
public PagingResultImpl<UserDTO> getUserList(int page, int pageSize) {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("UserQueryModel");
    queryRequest.setColumns(List.of("userId", "userName", "email"));
    queryRequest.setReturnTotal(true);

    PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
    form.setParam(queryRequest);
    form.setStart((page - 1) * pageSize);
    form.setLimit(pageSize);

    return queryFacade.queryModelData(form);
}
```

### 模式 3：复合条件查询

```java
public List<OrderDTO> getOrders(String status, BigDecimal minAmount) {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("OrderQueryModel");
    queryRequest.setColumns(List.of("orderId", "orderNo", "amount", "status"));

    List<SliceRequestDef> slices = new ArrayList<>();

    // 状态条件
    SliceRequestDef statusSlice = new SliceRequestDef();
    statusSlice.setField("status");
    statusSlice.setOp("=");
    statusSlice.setValue(status);
    slices.add(statusSlice);

    // 金额条件
    SliceRequestDef amountSlice = new SliceRequestDef();
    amountSlice.setField("amount");
    amountSlice.setOp(">=");
    amountSlice.setValue(minAmount);
    slices.add(amountSlice);

    queryRequest.setSlice(slices);

    PagingRequest<DbQueryRequestDef> form =
        PagingRequest.buildPagingRequest(queryRequest, 100);

    return queryFacade.queryModelData(form).getItems();
}
```

### 模式 4：分组汇总查询

```java
public List<SalesSummaryDTO> getSalesSummaryByMonth(int year) {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("SalesQueryModel");

    // 查询列
    queryRequest.setColumns(List.of(
        "salesDate$year",
        "salesDate$month",
        "quantity",      // 自动 SUM
        "salesAmount"    // 自动 SUM
    ));

    // 分组
    queryRequest.setGroupBy(List.of(
        new GroupRequestDef("salesDate$year"),
        new GroupRequestDef("salesDate$month")
    ));

    // 过滤条件
    SliceRequestDef slice = new SliceRequestDef();
    slice.setField("salesDate$year");
    slice.setOp("=");
    slice.setValue(year);
    queryRequest.setSlice(List.of(slice));

    // 排序
    queryRequest.setOrderBy(List.of(
        new OrderRequestDef("salesDate$month", "ASC")
    ));

    PagingRequest<DbQueryRequestDef> form =
        PagingRequest.buildPagingRequest(queryRequest, 12);

    return queryFacade.queryModelData(form).getItems();
}
```

### 模式 5：计算字段查询

```java
public List<OrderDTO> getOrdersWithProfit() {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("OrderQueryModel");

    // 定义计算字段
    CalculatedFieldDef profitField = new CalculatedFieldDef();
    profitField.setName("profit");
    profitField.setCaption("利润");
    profitField.setExpression("salesAmount - costAmount");
    queryRequest.setCalculatedFields(List.of(profitField));

    // 查询列（包含计算字段）
    queryRequest.setColumns(List.of(
        "orderId", "salesAmount", "costAmount", "profit"
    ));

    // 按利润降序排序
    queryRequest.setOrderBy(List.of(
        new OrderRequestDef("profit", "DESC")
    ));

    return queryFacade.queryModelData(
        PagingRequest.buildPagingRequest(queryRequest, 100)
    ).getItems();
}
```

### 模式 6：OR 条件查询

```java
public List<OrderDTO> getOrdersByStatusOrAmount(
    List<String> statuses,
    BigDecimal minAmount) {

    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("OrderQueryModel");
    queryRequest.setColumns(List.of("orderId", "status", "amount"));

    // OR 条件组
    SliceRequestDef orCondition = SliceRequestDef.or(List.of(
        // 状态 IN 条件
        new SliceRequestDef("status", "in", statuses),
        // 金额条件
        new SliceRequestDef("amount", ">=", minAmount)
    ));

    queryRequest.setSlice(List.of(orCondition));

    return queryFacade.queryModelData(
        PagingRequest.buildPagingRequest(queryRequest, 100)
    ).getItems();
}
```

## 输入要求

用户需提供：
- **查询模型名称**（必需）
- **业务需求描述**（如"根据 userId 查询用户信息"）
- **Service 类名称**（可选，默认根据模型名生成）
- **包名**（可选，默认 `{basePackage}.service.query`）

## 输出格式

```
✅ 查询 Service 生成完成！

📁 生成的文件：
  - src/main/java/com/example/service/query/UserQueryService.java
  - src/main/java/com/example/service/query/impl/UserQueryServiceImpl.java
  - src/main/java/com/example/dto/UserDTO.java (可选)
  - src/main/java/com/example/dto/UserQueryParams.java (可选)

🚀 使用示例：
  @Autowired
  private UserQueryService userQueryService;

  UserDTO user = userQueryService.getUserById(12345L);
  PagingResultImpl<UserDTO> users = userQueryService.getUserList(params);

📖 DSL 语法参考：
  使用 /dsl-syntax-guide 查看完整语法
```

## 约束条件

- 项目必须已集成 `foggy-dataset-model` 依赖
- QueryFacade 或 JdbcService 需已注入到 Spring 容器
- 生成的代码符合 Spring Boot 规范

## 决策规则

- 如果用户未指定 Service 名称 → 根据模型名自动生成（如 `UserQueryModel` → `UserQueryService`）
- 如果用户未指定包名 → 使用项目默认包名 + `.service.query`
- 如果用户需要分页 → 使用 `PagingRequest` 和 `PagingResultImpl`
- 如果用户需要单条记录 → 设置 `pageSize = 1` 并返回第一条
- 如果查询结果需要转换 → 生成 DTO 类和转换方法
- 如果用户需要去重计数/窗口函数/移动平均/统计函数 → 读取 `references/advanced-query-patterns.md`

## 依赖技能

- `qm-schema-viewer` - 获取模型 schema 信息
- `dsl-syntax-guide` - DSL 语法参考
