# Template Strings

Template strings are one of FSScript's core features, wrapped with backticks `` ` ``.

## Basic Usage

```javascript
let name = 'World';
let greeting = `Hello ${name}!`;  // Hello World!
```

## Expression Interpolation

```javascript
let a = 10;
let b = 20;
let result = `Sum is ${a + b}`;  // Sum is 30
```

## Multi-line Strings

```javascript
let sql = `
    SELECT *
    FROM users
    WHERE status = 'active'
`;
```

## Nested Templates

```javascript
let items = ['a', 'b', 'c'];
let list = `Items: ${items.map(i => `<li>${i}</li>`).join('')}`;
```

## Escaping

```javascript
`Use backtick: \``   // Use backtick: `
`Dollar sign: \${}`   // Dollar sign: ${}
```

## Practical Application: Dynamic SQL

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
