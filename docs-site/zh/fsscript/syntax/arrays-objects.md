# 数组与对象

## 数组

### 数组字面量

```javascript
let arr = [1, 2, 3];

// 稀疏数组
let sparse = [, 1, 2, 3];

// 展开运算符
let combined = [...arr1, ...arr2, newElement];
```

### 数组方法

```javascript
[1, 2, 3].map(e => e + 1);      // [2, 3, 4]
[1, 2, 3].filter(e => e > 1);   // [2, 3]
[1, 2, 3].join(',');            // "1,2,3"
[1, 2, 3].includes(2);          // true
```

## 对象

### 对象字面量

```javascript
let obj = { key1: value1, key2: value2 };

// 属性简写
let a = 1, b = 2;
let obj = { a, b };  // 等同于 { a: a, b: b }

// 展开运算符
let merged = { ...obj1, ...obj2, extra: 'value' };
```

### 属性访问

```javascript
obj.property
obj['property']
obj?.property       // 可选链
```

## 解构

### 对象解构

```javascript
var { a, b, c } = { a: 1, b: 2 };
// a = 1, b = 2, c = undefined
```

### 数组解构

```javascript
let [first, ...rest] = [1, 2, 3];
// first = 1, rest = [2, 3]
```
