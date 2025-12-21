# 函数与闭包

## 命名函数

```javascript
function functionName(param1, param2) {
    return result;
}
```

## 箭头函数

```javascript
let add = (a, b) => a + b;
let double = x => x * 2;
let getTime = () => new Date();

// 多行箭头函数
let process = (x) => {
    let result = x * 2;
    return result + 1;
};
```

## 函数调用

```javascript
functionName(arg1, arg2);
obj.method(args);
[1, 2, 3].map(e => e + 1).join(',');
```

## 闭包

FSScript 完整支持 JavaScript 的闭包特性：

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

## 块级作用域

`let` 在循环中的行为：

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

对比 `var` 的行为：

```javascript
var closures = [];

// var 是函数作用域，所有闭包共享同一个 i
for (var i = 0; i < 3; i++) {
    closures.push(() => i);
}

// 结果: 3, 3, 3
```
