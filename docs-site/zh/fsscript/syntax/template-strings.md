# 模板字符串

模板字符串是 FSScript 的核心特性之一，使用反引号 `` ` `` 包裹。

## 基本用法

```javascript
let name = 'World';
let greeting = `Hello ${name}!`;  // Hello World!
```

## 表达式插值

```javascript
let a = 10;
let b = 20;
let result = `Sum is ${a + b}`;  // Sum is 30
```

## 多行字符串

```javascript
let sql = `
    SELECT *
    FROM users
    WHERE status = 'active'
`;
```

## 嵌套模板

```javascript
let items = ['a', 'b', 'c'];
let list = `Items: ${items.map(i => `<li>${i}</li>`).join('')}`;
```

## 转义

```javascript
`使用反引号: \``   // 使用反引号: `
`美元符号: \${}`   // 美元符号: ${}
```

## 实际应用：动态 SQL

```javascript
import {workonSessionTokenUsingCache as token} from '@saasBasicWebUtils';

export const sql = `
    SELECT t.id, t.name, t.amount
    FROM orders t
    WHERE t.tenant_id = '${token.tenantId}'
        ${sqlExp(form.param.teamId, 'AND t.team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND t.status IN ')}
    ORDER BY t.create_time DESC
`;
```
