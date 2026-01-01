# Functions & Closures

## Named Functions

```javascript
function functionName(param1, param2) {
    return result;
}
```

## Arrow Functions

```javascript
let add = (a, b) => a + b;
let double = x => x * 2;
let getTime = () => new Date();

// Multi-line arrow function
let process = (x) => {
    let result = x * 2;
    return result + 1;
};
```

## Function Calls

```javascript
functionName(arg1, arg2);
obj.method(args);
[1, 2, 3].map(e => e + 1).join(',');
```

## Closures

FSScript fully supports JavaScript closure features:

```javascript
function createCounter() {
    let count = 0;
    return () => {
        count++;
        return count;
    };
}

let counter = createCounter();
export var r1 = counter();  // 1
export var r2 = counter();  // 2
export var r3 = counter();  // 3
```

## Block Scope

Behavior of `let` in loops:

```javascript
var closures = [];

// let creates a new binding for each iteration in for loops
for (let i = 0; i < 3; i++) {
    closures.push(() => i);
}

// Result: 0, 1, 2 (not 3, 3, 3)
export var result0 = closures[0]();  // 0
export var result1 = closures[1]();  // 1
export var result2 = closures[2]();  // 2
```

Compare with `var` behavior:

```javascript
var closures = [];

// var has function scope, all closures share the same i
for (var i = 0; i < 3; i++) {
    closures.push(() => i);
}

// Result: 3, 3, 3
```
