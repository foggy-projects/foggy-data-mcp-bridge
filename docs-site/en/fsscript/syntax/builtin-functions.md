# Built-in Functions

## Math Functions

| Function | Description |
|----------|-------------|
| `Math.ceil(n)` | Round up |
| `Math.floor(n)` | Round down |
| `Math.round(n)` | Round to nearest |
| `Math.min(a, b)` | Minimum value |
| `Math.max(a, b)` | Maximum value |
| `Math.abs(n)` | Absolute value |

## Type Conversion

| Function | Description |
|----------|-------------|
| `parseInt(s)` | Parse integer |
| `parseFloat(s)` | Parse float |
| `typeof(x)` | Get type |

## Date Functions

| Function | Description |
|----------|-------------|
| `currentDate()` | Get current date |
| `dateFormat(date, pattern)` | Format date |
| `toDate(str)` | String to date |

## Utility Functions

| Function | Description |
|----------|-------------|
| `uuid()` | Generate UUID |
| `sleep(ms)` | Sleep for milliseconds |
| `toJson(obj)` | Convert to JSON string |

## Logging Functions

| Function | Description |
|----------|-------------|
| `console.log(message)` | Output log |
| `console.error(message)` | Output error log |
| `log(message)` | Output log |
| `debug(message)` | Output debug log |

## JSON Operations

```javascript
// Parse JSON
let obj = JSON.parse('{"name": "test"}');

// Stringify to JSON
let str = JSON.stringify({ name: 'test' });
```

## Array Methods

```javascript
// Transform
[1, 2, 3].map(e => e * 2);         // [2, 4, 6]
[1, 2, 3].filter(e => e > 1);      // [2, 3]
[1, 2, 3].reduce((a, b) => a + b); // 6

// Find
[1, 2, 3].find(e => e > 1);        // 2
[1, 2, 3].includes(2);             // true
[1, 2, 3].indexOf(2);              // 1

// Other
[1, 2, 3].join(',');               // "1,2,3"
[1, 2, 3].reverse();               // [3, 2, 1]
[3, 1, 2].sort();                  // [1, 2, 3]
```
