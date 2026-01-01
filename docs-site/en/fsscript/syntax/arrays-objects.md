# Arrays & Objects

## Arrays

### Array Literals

```javascript
let arr = [1, 2, 3];

// Sparse array
let sparse = [, 1, 2, 3];

// Spread operator
let combined = [...arr1, ...arr2, newElement];
```

### Array Methods

```javascript
[1, 2, 3].map(e => e + 1);      // [2, 3, 4]
[1, 2, 3].filter(e => e > 1);   // [2, 3]
[1, 2, 3].join(',');            // "1,2,3"
[1, 2, 3].includes(2);          // true
```

## Objects

### Object Literals

```javascript
let obj = { key1: value1, key2: value2 };

// Property shorthand
let a = 1, b = 2;
let obj = { a, b };  // Equivalent to { a: a, b: b }

// Spread operator
let merged = { ...obj1, ...obj2, extra: 'value' };
```

### Property Access

```javascript
obj.property
obj['property']
obj?.property       // Optional chaining
```

## Destructuring

### Object Destructuring

```javascript
var { a, b, c } = { a: 1, b: 2 };
// a = 1, b = 2, c = undefined
```

### Array Destructuring

```javascript
let [first, ...rest] = [1, 2, 3];
// first = 1, rest = [2, 3]
```
