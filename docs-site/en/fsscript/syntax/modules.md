# Module System

FSScript supports ES6-style module import/export.

## Import

### Import Script Files

```javascript
// Import entire script
import 'path/to/script.fsscript'

// Named import
import { func1, func2 } from 'module.fsscript'

// Default import
import ModuleName from 'module.fsscript'

// Rename import
import originalName as alias from 'module.fsscript'
```

### Import Spring Beans

```javascript
// Import entire Bean
import myService from '@myServiceBean';

// Import Bean methods
import { getUserById, saveUser } from '@userService';

// Rename
import { saveUser as save } from '@userService';
```

### Import Java Classes

```javascript
// Import static methods
import { format } from 'java:java.lang.String';
import { now } from 'java:java.time.LocalDateTime';

// Import entire class
import DateUtils from 'java:com.example.utils.DateUtils';
```

## Export

### Export Variables

```javascript
export var x = 10;
export let y = 20;
export const z = 30;
```

### Export Functions

```javascript
export function myFunction() {
    // ...
}
```

### Default Export

```javascript
export default value;
```

### Batch Export

```javascript
export { a, b, c };
```

## Modular Example

```javascript
// utils.fsscript
export function formatMoney(value) {
    return value.toFixed(2) + ' USD';
}

export const TAX_RATE = 0.13;

// main.fsscript
import { formatMoney, TAX_RATE } from './utils.fsscript';

export let price = 100;
export let tax = price * TAX_RATE;
export let display = formatMoney(price + tax);
```
