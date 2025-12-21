# Why FSScript

## Core Use Cases

### 1. Dynamic SQL Templates

The primary use of FSScript is elegantly assembling complex dynamic SQL queries:

```javascript
import {workonSessionTokenUsingCache as token} from '@saasBasicWebUtils';

/**
 * Dynamic SQL query
 * sqlExp, sqlInExp functions prevent SQL injection
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

### 2. Import Spring Beans

```javascript
// Import single Bean
import myService from '@myServiceBean';

// Import multiple methods from Bean
import {
    getUserById,
    saveUser as save,
    deleteUser
} from '@userService';

// Call
export var user = getUserById(1001);
export var result = save(user);
```

### 3. Import Java Classes

```javascript
// Import static methods
import {format} from 'java:java.lang.String';
import {now} from 'java:java.time.LocalDateTime';

// Import entire class
import DateUtils from 'java:com.example.utils.DateUtils';

// Use
export let formatted = format("Hello %s", "World");
export let today = DateUtils.today();

// Create instances
export let list = new ArrayList();
list.add("item1");
```

### 4. Modular Scripts

```javascript
// utils.fsscript
export function formatMoney(value) {
    return value.toFixed(2) + ' USD';
}

export const TAX_RATE = 0.13;

// main.fsscript
import {formatMoney, TAX_RATE} from './utils.fsscript';

export let price = 100;
export let tax = price * TAX_RATE;
export let display = formatMoney(price + tax);
```

### 5. Closures and Scope

FSScript supports JavaScript-compliant `let` block scope:

```javascript
var closures = [];

// let creates new binding for each iteration in for loop
for (let i = 0; i < 3; i++) {
    closures.push(() => i);
}

// Result: 0, 1, 2 (not 3, 3, 3)
export var result0 = closures[0]();  // 0
export var result1 = closures[1]();  // 1
export var result2 = closures[2]();  // 2
```

## When NOT to Use

- **High-performance Computing** - FSScript is interpreted, not suitable for CPU-intensive tasks
- **Full JavaScript Compatibility** - This is not a complete JS implementation
- **Frontend Runtime** - This is a Java backend scripting engine
