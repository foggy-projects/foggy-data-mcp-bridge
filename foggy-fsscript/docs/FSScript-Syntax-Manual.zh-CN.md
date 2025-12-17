# FSScript 语法手册

FSScript 是一个类 JavaScript 的脚本语言，由 Foggy Framework 提供，可用于编写表达式和脚本逻辑。

## 目录

1. [数据类型](#1-数据类型)
2. [变量声明](#2-变量声明)
3. [运算符](#3-运算符)
4. [控制流](#4-控制流)
5. [函数](#5-函数)
6. [对象与Map](#6-对象与map)
7. [数组](#7-数组)
8. [解构赋值](#8-解构赋值)
9. [异常处理](#9-异常处理)
10. [模块导入导出](#10-模块导入导出)
11. [特殊表达式](#11-特殊表达式)
12. [内置函数](#12-内置函数)

---

## 1. 数据类型

### 1.1 数值类型

```javascript
// 整数
123
-456

// 浮点数
123.45
-0.5

// Long 类型（以 L 结尾）
123456789L
```

### 1.2 字符串

```javascript
// 单引号字符串
'hello world'

// 双引号字符串
"hello world"

// 反引号模板字符串（支持表达式插值）
`Hello ${name}`
`Value is ${1 + 2}`

// 转义字符
'a\'b'      // a'b
`1\`2`      // 1`2
```

### 1.3 布尔值

```javascript
true
false
```

### 1.4 空值

```javascript
null
```

---

## 2. 变量声明

### 2.1 var 声明

```javascript
var x = 10;
var name = 'John';
```

### 2.2 let 声明

```javascript
let y = 20;
let result = x + y;
```

### 2.3 const 声明

```javascript
const PI = 3.14159;
const config = { key: 'value' };
```

### 2.4 赋值

```javascript
x = 15;              // 简单赋值
obj.prop = 100;      // 对象属性赋值
arr[0] = 'first';    // 数组元素赋值
```

### 2.5 删除变量/属性

```javascript
delete x;                    // 删除变量
delete obj.property;         // 删除对象属性
delete obj.nested.prop;      // 删除嵌套属性
```

---

## 3. 运算符

### 3.1 算术运算符

| 运算符 | 描述 | 示例 |
|--------|------|------|
| `+` | 加法 / 字符串连接 | `1 + 2` => `3`, `'a' + 'b'` => `'ab'` |
| `-` | 减法 | `5 - 3` => `2` |
| `*` | 乘法 | `2 * 3` => `6` |
| `/` | 除法 | `6 / 3` => `2` |
| `%` | 取模 | `7 % 3` => `1` |
| `-x` | 一元负号 | `-5` |

### 3.2 自增/自减运算符

```javascript
x++     // 后置自增，返回原值
++x     // 前置自增，返回新值
x--     // 后置自减，返回原值
--x     // 前置自减，返回新值

// 示例
const ss = {b: 1};
ss.b++;     // 返回 1，ss.b 变为 2
++ss.b;     // ss.b 变为 2，返回 2
```

### 3.3 比较运算符

| 运算符 | 描述 | 示例 |
|--------|------|------|
| `==` | 等于 | `'a' == 'a'` => `true` |
| `<>` | 不等于 | `'a' <> 'b'` => `true` |
| `<` | 小于 | `1 < 2` => `true` |
| `>` | 大于 | `2 > 1` => `true` |
| `<=` | 小于等于 | `1 <= 1` => `true` |
| `>=` | 大于等于 | `2 >= 1` => `true` |

### 3.4 逻辑运算符

| 运算符 | 描述 | 示例 |
|--------|------|------|
| `&&` | 逻辑与 | `true && false` => `false` |
| `\|\|` | 逻辑或 | `true \|\| false` => `true` |
| `!` | 逻辑非 | `!true` => `false` |
| `or` | 逻辑或 (关键字) | `a or b` |

### 3.5 位运算符

| 运算符 | 描述 |
|--------|------|
| `&` | 按位与 |
| `\|` | 按位或 |
| `^` | 按位异或 |
| `~` | 按位取反 |

### 3.6 三元运算符

```javascript
condition ? valueIfTrue : valueIfFalse

// 示例
let result = x > 0 ? 'positive' : 'non-positive';
let value = config ? config.value || 0 : 0;
```

### 3.7 可选链运算符

```javascript
obj?.property       // 安全属性访问
obj?.nested?.value  // 链式安全访问

// 示例
insertPages?.length > 1 ? 'multiple' : 'single';
r?.code !== 0 && r?.code !== 200;
```

### 3.8 展开运算符

```javascript
// 数组展开
[...arr1, ...arr2, newItem]

// 对象展开
{ ...obj1, ...obj2, newProp: value }

// 示例
let merged = [...[1, 2], ...[3, 4], 5];  // [1, 2, 3, 4, 5]
let combined = { ...{a: 1}, ...{b: 2}, c: 3 };  // {a: 1, b: 2, c: 3}
```

### 3.9 其他运算符

| 运算符 | 描述 | 示例 |
|--------|------|------|
| `in` | 成员检查 | `key in obj` |
| `like` | 模式匹配 | `name like 'pattern'` |

---

## 4. 控制流

### 4.1 if 语句

```javascript
// 基本 if
if (condition) {
    // 代码块
}

// if-else
if (condition) {
    // 条件为真时执行
} else {
    // 条件为假时执行
}

// if-else if-else
if (condition1) {
    // ...
} else if (condition2) {
    // ...
} else {
    // ...
}

// 示例
var b = 1;
if (false) {
    b = 'false branch';
} else if (b == 1) {
    b = 'b equals 1';
} else {
    b = 'else branch';
}
```

### 4.2 switch 语句

```javascript
switch (expression) {
    case value1:
        // 代码
        break;
    case value2:
        // 代码
        break;
    case value3:
    case value4:
        // 多个 case 共享代码
        break;
    default:
        // 默认代码
}

// 示例
let data = 0;
switch (test) {
    case 1:
        data = 11;
        break;
    case '2':
        data = '22';
        break;
    case (1 + 1):  // 支持表达式
        data = 22;
        break;
    case 4:
    case 5:
        data = 44;
        break;
    default:
        data = 999;
}
```

### 4.3 for 循环

```javascript
// C 风格 for 循环
for (var i = 0; i < 10; i++) {
    // 代码
}

// for-in 循环（遍历对象键）
for (let key in obj) {
    // 代码
}

// for-of 循环（遍历数组值）
for (const value of array) {
    // 代码
}

// for-: 循环（遍历集合）
for (var item : collection) {
    // 代码
}

// 示例
let sum = 0;
for (var i = 0; i < 10; i++) {
    sum = sum + i;
}

let b = [1, 2];
let v = 0;
for (const x of b) {
    v = v + x;
}  // v = 3
```

### 4.4 while 循环

```javascript
while (condition) {
    // 代码
}

// 示例
var count = 0;
while (count < 5) {
    count++;
}
```

### 4.5 循环控制

```javascript
break;          // 退出当前循环
break label;    // 退出指定标签的循环
continue;       // 跳过当前迭代
continue label; // 跳过指定标签循环的当前迭代
```

---

## 5. 函数

### 5.1 命名函数

```javascript
function functionName(param1, param2) {
    // 函数体
    return result;
}

// 示例
function add(a, b) {
    return a + b;
}

function greet(name) {
    return 'Hello, ' + name;
}
```

### 5.2 匿名函数

```javascript
function(x) {
    return x * 2;
}
```

### 5.3 箭头函数

```javascript
// 带括号的箭头函数
let add = (a, b) => a + b;

// 单参数（可省略括号）
let double = x => x * 2;

// 无参数
let getTime = () => new Date();

// 带代码块的箭头函数
let complex = (a, b) => {
    let result = a + b;
    return result * 2;
};

// 示例
let a = a => { 'b' };  // 返回 'b'
let ff = (a) => { 'b' };  // 返回 'b'

// 在对象中使用箭头函数
let obj = {
    handler: e => { 'processed' }
};
```

### 5.4 函数调用

```javascript
functionName(arg1, arg2);

// 方法调用
obj.method(args);

// 数组方法链式调用
[1, 2, 3].map(e => e + 1).join(',');  // "2,3,4"
```

### 5.5 return 语句

```javascript
function calculate() {
    let x = 10;
    return x * 2;  // 返回 20
}
```

---

## 6. 对象与Map

### 6.1 对象字面量

```javascript
// 基本语法
let obj = { key1: value1, key2: value2 };

// 属性简写
let a = 1;
let b = 2;
let obj = { a, b };  // 等同于 { a: a, b: b }

// 混合使用
let obj = { a, b, c: 3 };

// 尾部逗号
let obj = { a: 1, b: 2, };  // 允许尾部逗号

// 展开运算符
let merged = { ...obj1, ...obj2, extra: 'value' };
```

### 6.2 属性访问

```javascript
obj.property        // 点表示法
obj['property']     // 方括号表示法
obj?.property       // 可选链（安全访问）
obj.nested.deep     // 嵌套访问
```

### 6.3 属性操作

```javascript
obj.newProp = 'value';     // 添加/修改属性
delete obj.property;       // 删除属性
```

---

## 7. 数组

### 7.1 数组字面量

```javascript
let arr = [1, 2, 3];

// 稀疏数组
let sparse = [, 1, 2, 3];      // 第一个元素为空
let sparse2 = [, 1, , 2, 3];   // 包含空位

// 尾部逗号
let arr = [1, 2, 3,];  // 允许尾部逗号

// 展开运算符
let combined = [...arr1, ...arr2, newElement];
```

### 7.2 数组访问

```javascript
arr[0]              // 第一个元素
arr[arr.length - 1] // 最后一个元素
arr.length          // 数组长度
```

### 7.3 数组方法

```javascript
// map - 转换元素
[1, 2, 3].map(e => e + 1);  // [2, 3, 4]

// filter - 过滤元素
[1, 2, 3].filter(e => e > 1);  // [2, 3]

// join - 连接为字符串
[1, 2, 3].join(',');  // "1,2,3"

// includes - 检查包含
[1, 2, 3].includes(2);  // true

// push - 添加元素
let arr = [];
arr.push(1);  // arr = [1]
```

### 7.4 字符串方法

```javascript
'a,b,c'.split(',')    // ['a', 'b', 'c']
'a,b'.split(',').length  // 2
'hello'.length        // 5
```

---

## 8. 解构赋值

### 8.1 对象解构

```javascript
var { a, b, c } = { a: 1, b: 2 };
// a = 1, b = 2, c = undefined (null)

let { x, y } = { x: 10, y: 20 };
```

### 8.2 数组解构（展开）

```javascript
// 使用展开运算符
let [first, ...rest] = [1, 2, 3];  // first = 1, rest = [2, 3]
```

---

## 9. 异常处理

### 9.1 try-catch

```javascript
try {
    // 可能抛出异常的代码
    riskyOperation();
} catch (e) {
    // 处理异常
    log(e);
}
```

### 9.2 try-finally

```javascript
try {
    // 代码
} finally {
    // 总是执行
}
```

### 9.3 try-catch-finally

```javascript
try {
    // 代码
} catch (error) {
    // 处理异常
} finally {
    // 清理代码
}
```

### 9.4 throw 语句

```javascript
throw new Error("Something went wrong");
throw expression;
```

---

## 10. 模块导入导出

### 10.1 导入

```javascript
// 导入整个脚本
import 'path/to/script.fsscript'

// 具名导入
import { func1, func2 } from 'module.fsscript'
import { a, b } from 'export_test.fsscript'

// 命名空间导入（ES6 风格）
import * as utils from 'utils.fsscript'
// 访问导出的内容
utils.someFunction()       // 访问具名导出
utils.default              // 访问默认导出

// 默认导入
import ModuleName from 'module.fsscript'
import T from 'export_test.fsscript'

// 别名导入
import originalName as alias from 'module.fsscript'

// 从 Spring Bean 导入
import { method } from @beanName
```

### 10.2 导出

```javascript
// 导出变量
export var x = 10;
export let y = 20;
export const z = 30;

// 导出函数
export function myFunction() {
    // ...
}

// 默认导出（符合 ES6 规范，存储在 "default" 键下）
export default value;
export default { key: 'value' };

// 批量导出
export { a, b, c };

// 在函数内部导出（FSScript 扩展，非标准 JavaScript）
function setup() {
    export var config = { key: 'value' };
}
```

**注意：** FSScript 支持在函数和代码块内部使用 `export`，这是对标准 JavaScript 的扩展。标准 JavaScript 只允许在模块顶层使用 `export`。

---

## 11. 特殊表达式

### 11.1 上下文访问

```javascript
this            // 当前上下文
request         // HTTP 请求对象
_evaluator      // 表达式求值器
_ee             // 表达式求值器（简写）
```

### 11.2 Spring Bean 访问

```javascript
@beanName                    // 获取 Spring Bean
@beanName(arg1, arg2)        // 调用 Bean 方法
```

### 11.3 请求参数/属性

```javascript
$paramName      // 获取请求参数
$.paramName     // 获取参数（另一种写法）
#attrName       // 获取请求属性
```

### 11.4 new 表达式

```javascript
new Date()           // 创建 Date 实例
new Date(timestamp)  // 带参数创建实例
new ClassName(args)  // 创建类实例
```

### 11.5 typeof 运算符

```javascript
typeof x        // 返回类型字符串
typeof value
```

### 11.6 模板字符串表达式

```javascript
`Hello ${name}`           // 变量插值
`Result: ${1 + 2}`        // 表达式插值
`Path: ${path}/${file}`   // 多个插值
```

---

## 12. 内置函数

### 12.1 数学函数

| 函数 | 描述 | 示例 |
|------|------|------|
| `Math.ceil(n)` | 向上取整 | `Math.ceil(1.2)` => `2` |

### 12.2 类型转换

| 函数 | 描述 |
|------|------|
| `parseInt(s)` | 解析整数 |
| `parseFloat(s)` | 解析浮点数 |
| `typeof(x)` | 获取类型 |

### 12.3 日期函数

| 函数 | 描述 |
|------|------|
| `currentDate()` | 获取当前日期 |
| `dateFormat(date, pattern)` | 格式化日期 |
| `dateTimeFormat(date, pattern)` | 格式化日期时间 |
| `toStartTime(date)` | 转换为当天开始时间 |
| `toEndTime(date)` | 转换为当天结束时间 |
| `toDate(str)` | 字符串转日期 |
| `checkDaysRange(date, days)` | 检查日期范围 |

### 12.4 工具函数

| 函数 | 描述 |
|------|------|
| `uuid()` | 生成 UUID |
| `sleep(ms)` | 休眠指定毫秒 |
| `toJson(obj)` | 转换为 JSON 字符串 |
| `clearEmpty(obj)` | 清除空属性 |
| `parseFile(path)` | 解析文件 |

### 12.5 日志函数

| 函数 | 描述 |
|------|------|
| `log(message)` | 输出日志 |
| `debug(message)` | 输出调试日志 |

---

## 附录：运算符优先级

从高到低：

1. 括号 `()`
2. 成员访问 `.` `[]` `?.`
3. 一元运算符 `-` `!` `~` `++` `--` `delete`
4. 乘除取模 `*` `/` `%`
5. 加减 `+` `-`
6. 比较 `<` `>` `<=` `>=` `in` `like`
7. 相等 `==` `<>`
8. 位与 `&`
9. 位异或 `^`
10. 位或 `|`
11. 逻辑与 `&&`
12. 逻辑或 `||`
13. 三元 `?:`
14. 赋值 `=`
