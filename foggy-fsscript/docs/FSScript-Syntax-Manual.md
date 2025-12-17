# FSScript Syntax Manual

[中文文档](FSScript-Syntax-Manual.zh-CN.md)

FSScript is a JavaScript-like scripting language provided by Foggy Framework for writing expressions and script logic.

## Table of Contents

1. [Data Types](#1-data-types)
2. [Variable Declaration](#2-variable-declaration)
3. [Operators](#3-operators)
4. [Control Flow](#4-control-flow)
5. [Functions](#5-functions)
6. [Objects & Maps](#6-objects--maps)
7. [Arrays](#7-arrays)
8. [Destructuring](#8-destructuring)
9. [Exception Handling](#9-exception-handling)
10. [Module Import/Export](#10-module-importexport)
11. [Special Expressions](#11-special-expressions)
12. [Built-in Functions](#12-built-in-functions)

---

## 1. Data Types

### 1.1 Numeric Types

```javascript
// Integer
123
-456

// Float
123.45
-0.5

// Long (ends with L)
123456789L
```

### 1.2 Strings

```javascript
// Single quotes
'hello world'

// Double quotes
"hello world"

// Template literals (with interpolation)
`Hello ${name}`
`Value is ${1 + 2}`

// Escape characters
'a\'b'      // a'b
`1\`2`      // 1`2
```

### 1.3 Boolean

```javascript
true
false
```

### 1.4 Null

```javascript
null
```

---

## 2. Variable Declaration

```javascript
var x = 10;
let y = 20;
const PI = 3.14159;

// Assignment
x = 15;
obj.prop = 100;
arr[0] = 'first';

// Delete
delete x;
delete obj.property;
```

---

## 3. Operators

### 3.1 Arithmetic

| Operator | Description | Example |
|----------|-------------|---------|
| `+` | Addition / Concatenation | `1 + 2` => `3` |
| `-` | Subtraction | `5 - 3` => `2` |
| `*` | Multiplication | `2 * 3` => `6` |
| `/` | Division | `6 / 3` => `2` |
| `%` | Modulo | `7 % 3` => `1` |

### 3.2 Increment/Decrement

```javascript
x++     // Post-increment
++x     // Pre-increment
x--     // Post-decrement
--x     // Pre-decrement
```

### 3.3 Comparison

| Operator | Description |
|----------|-------------|
| `==` | Equal |
| `<>` | Not equal |
| `<` | Less than |
| `>` | Greater than |
| `<=` | Less than or equal |
| `>=` | Greater than or equal |

### 3.4 Logical

| Operator | Description |
|----------|-------------|
| `&&` | Logical AND |
| `\|\|` | Logical OR |
| `!` | Logical NOT |

### 3.5 Ternary

```javascript
condition ? valueIfTrue : valueIfFalse
```

### 3.6 Optional Chaining

```javascript
obj?.property
obj?.nested?.value
```

### 3.7 Spread Operator

```javascript
[...arr1, ...arr2, newItem]
{ ...obj1, ...obj2, newProp: value }
```

---

## 4. Control Flow

### 4.1 if Statement

```javascript
if (condition) {
    // code
} else if (condition2) {
    // code
} else {
    // code
}
```

### 4.2 switch Statement

```javascript
switch (expression) {
    case value1:
        // code
        break;
    case value2:
        // code
        break;
    default:
        // default code
}
```

### 4.3 for Loop

```javascript
// C-style for
for (var i = 0; i < 10; i++) {
    // code
}

// for-in (iterate object keys)
for (let key in obj) {
    // code
}

// for-of (iterate array values)
for (const value of array) {
    // code
}
```

### 4.4 while Loop

```javascript
while (condition) {
    // code
}
```

### 4.5 Loop Control

```javascript
break;          // Exit loop
continue;       // Skip iteration
```

---

## 5. Functions

### 5.1 Named Functions

```javascript
function functionName(param1, param2) {
    return result;
}
```

### 5.2 Arrow Functions

```javascript
let add = (a, b) => a + b;
let double = x => x * 2;
let getTime = () => new Date();
```

### 5.3 Function Calls

```javascript
functionName(arg1, arg2);
obj.method(args);
[1, 2, 3].map(e => e + 1).join(',');
```

---

## 6. Objects & Maps

### 6.1 Object Literals

```javascript
let obj = { key1: value1, key2: value2 };

// Property shorthand
let a = 1, b = 2;
let obj = { a, b };  // Same as { a: a, b: b }

// Spread operator
let merged = { ...obj1, ...obj2, extra: 'value' };
```

### 6.2 Property Access

```javascript
obj.property
obj['property']
obj?.property       // Optional chaining
```

---

## 7. Arrays

### 7.1 Array Literals

```javascript
let arr = [1, 2, 3];

// Sparse arrays
let sparse = [, 1, 2, 3];

// Spread operator
let combined = [...arr1, ...arr2, newElement];
```

### 7.2 Array Methods

```javascript
[1, 2, 3].map(e => e + 1);      // [2, 3, 4]
[1, 2, 3].filter(e => e > 1);   // [2, 3]
[1, 2, 3].join(',');            // "1,2,3"
[1, 2, 3].includes(2);          // true
```

---

## 8. Destructuring

### 8.1 Object Destructuring

```javascript
var { a, b, c } = { a: 1, b: 2 };
// a = 1, b = 2, c = undefined
```

### 8.2 Array Destructuring

```javascript
let [first, ...rest] = [1, 2, 3];
// first = 1, rest = [2, 3]
```

---

## 9. Exception Handling

```javascript
try {
    // risky code
} catch (e) {
    // handle exception
} finally {
    // cleanup
}

throw new Error("message");
```

---

## 10. Module Import/Export

### 10.1 Import

```javascript
// Import entire script
import 'path/to/script.fsscript'

// Named imports
import { func1, func2 } from 'module.fsscript'

// Default import
import ModuleName from 'module.fsscript'

// Import with alias
import originalName as alias from 'module.fsscript'

// Import from Spring Bean
import { method } from @beanName

// Import Java class
import { method } from 'java:com.example.Utils'
```

### 10.2 Export

```javascript
// Export variables
export var x = 10;
export let y = 20;
export const z = 30;

// Export functions
export function myFunction() {
    // ...
}

// Default export
export default value;

// Batch export
export { a, b, c };
```

---

## 11. Special Expressions

### 11.1 Context Access

```javascript
this            // Current context
request         // HTTP request object
_evaluator      // Expression evaluator
_ee             // Evaluator (shorthand)
```

### 11.2 Spring Bean Access

```javascript
@beanName                    // Get Spring Bean
@beanName(arg1, arg2)        // Call Bean method
```

### 11.3 Request Parameters/Attributes

```javascript
$paramName      // Get request parameter
$.paramName     // Get parameter (alternative)
#attrName       // Get request attribute
```

### 11.4 new Expression

```javascript
new Date()
new ClassName(args)
```

### 11.5 typeof Operator

```javascript
typeof x        // Returns type string
```

---

## 12. Built-in Functions

### 12.1 Math Functions

| Function | Description |
|----------|-------------|
| `Math.ceil(n)` | Round up |

### 12.2 Type Conversion

| Function | Description |
|----------|-------------|
| `parseInt(s)` | Parse integer |
| `parseFloat(s)` | Parse float |
| `typeof(x)` | Get type |

### 12.3 Date Functions

| Function | Description |
|----------|-------------|
| `currentDate()` | Get current date |
| `dateFormat(date, pattern)` | Format date |
| `toDate(str)` | String to date |

### 12.4 Utility Functions

| Function | Description |
|----------|-------------|
| `uuid()` | Generate UUID |
| `sleep(ms)` | Sleep milliseconds |
| `toJson(obj)` | Convert to JSON |

### 12.5 Logging Functions

| Function | Description |
|----------|-------------|
| `log(message)` | Output log |
| `debug(message)` | Output debug log |

---

## Operator Precedence

From highest to lowest:

1. Parentheses `()`
2. Member access `.` `[]` `?.`
3. Unary `-` `!` `~` `++` `--` `delete`
4. Multiplicative `*` `/` `%`
5. Additive `+` `-`
6. Comparison `<` `>` `<=` `>=`
7. Equality `==` `<>`
8. Logical AND `&&`
9. Logical OR `||`
10. Ternary `?:`
11. Assignment `=`
