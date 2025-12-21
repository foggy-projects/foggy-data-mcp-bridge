# Variables & Types

## Data Types

### Numeric Types

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

### Strings

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

### Boolean

```javascript
true
false
```

### Null

```javascript
null
```

## Variable Declaration

```javascript
var x = 10;      // Function scope
let y = 20;      // Block scope
const PI = 3.14; // Constant

// Uninitialized declaration
var a;
let b;
```

## Assignment

```javascript
x = 15;
obj.prop = 100;
arr[0] = 'first';
```

## Delete

```javascript
delete x;
delete obj.property;
```

## Type Conversion

| Function | Description |
|----------|-------------|
| `parseInt(s)` | Parse integer |
| `parseFloat(s)` | Parse float |
| `typeof(x)` | Get type |
