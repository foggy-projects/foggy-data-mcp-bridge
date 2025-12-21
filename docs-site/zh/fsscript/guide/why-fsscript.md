# 为什么用 FSScript

## 核心场景

### 1. 动态 SQL 模板

FSScript 的核心用途是优雅地组装复杂的动态 SQL 查询：

```javascript
import {workonSessionTokenUsingCache as token} from '@saasBasicWebUtils';

/**
 * 动态 SQL 查询
 * sqlExp、sqlInExp 等函数用于防止 SQL 注入
 */
export const sql = `
    SELECT t.id, t.name, t.amount, t.create_time
    FROM orders t
    WHERE t.tenant_id = '${token.tenantId}'
        ${sqlExp(form.param.teamId, 'AND t.team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND t.status IN ')}
        ${sqlExp(form.param.startTime, 'AND ? <= t.create_time')}
        ${sqlExp(form.param.endTime, 'AND t.create_time < ?')}
    ORDER BY t.create_time DESC
`;
```

### 2. 导入 Spring Bean

```javascript
// 导入单个 Bean
import myService from '@myServiceBean';

// 导入 Bean 的多个方法
import {
    getUserById,
    saveUser as save,
    deleteUser
} from '@userService';

// 调用
export var user = getUserById(1001);
export var result = save(user);
```

### 3. 导入 Java 类

```javascript
// 导入静态方法
import {format} from 'java:java.lang.String';
import {now} from 'java:java.time.LocalDateTime';

// 导入整个类
import DateUtils from 'java:com.example.utils.DateUtils';

// 使用
export let formatted = format("Hello %s", "World");
export let today = DateUtils.today();

// 创建实例
export let list = new ArrayList();
list.add("item1");
```

### 4. 模块化脚本

```javascript
// utils.fsscript
export function formatMoney(value) {
    return value.toFixed(2) + ' 元';
}

export const TAX_RATE = 0.13;

// main.fsscript
import {formatMoney, TAX_RATE} from './utils.fsscript';

export let price = 100;
export let tax = price * TAX_RATE;
export let display = formatMoney(price + tax);
```

### 5. 闭包与作用域

FSScript 支持 JavaScript 规范的 `let` 块级作用域：

```javascript
var closures = [];

// let 在 for 循环中每次迭代创建新的绑定
for (let i = 0; i < 3; i++) {
    closures.push(() => i);
}

// 结果: 0, 1, 2 (而非 3, 3, 3)
export var result0 = closures[0]();  // 0
export var result1 = closures[1]();  // 1
export var result2 = closures[2]();  // 2
```

## 不适用场景

- **高性能计算** - FSScript 是解释执行，不适合 CPU 密集型任务
- **完整 JavaScript 兼容** - 这不是完整的 JS 实现，部分高级特性不支持
- **前端运行** - 这是 Java 后端脚本引擎
