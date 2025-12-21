# 变量与类型

## 数据类型

### 数值类型

```javascript
// 整数
123
-456

// 浮点数
123.45
-0.5

// 长整型 (以 L 结尾)
123456789L
```

### 字符串

```javascript
// 单引号
'hello world'

// 双引号
"hello world"

// 模板字符串 (支持插值)
`Hello ${name}`
`Value is ${1 + 2}`

// 转义字符
'a\'b'      // a'b
`1\`2`      // 1`2
```

### 布尔值

```javascript
true
false
```

### 空值

```javascript
null
```

## 变量声明

```javascript
var x = 10;      // 函数作用域
let y = 20;      // 块级作用域
const PI = 3.14; // 常量

// 未初始化声明
var a;
let b;
```

## 赋值

```javascript
x = 15;
obj.prop = 100;
arr[0] = 'first';
```

## 删除

```javascript
delete x;
delete obj.property;
```

## 类型转换

| 函数 | 说明 |
|------|------|
| `parseInt(s)` | 解析整数 |
| `parseFloat(s)` | 解析浮点数 |
| `typeof(x)` | 获取类型 |
