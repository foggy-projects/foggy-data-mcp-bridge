# 内置函数

## 数学函数

| 函数 | 说明 |
|------|------|
| `Math.ceil(n)` | 向上取整 |
| `Math.floor(n)` | 向下取整 |
| `Math.round(n)` | 四舍五入 |
| `Math.min(a, b)` | 最小值 |
| `Math.max(a, b)` | 最大值 |
| `Math.abs(n)` | 绝对值 |

## 类型转换

| 函数 | 说明 |
|------|------|
| `parseInt(s)` | 解析整数 |
| `parseFloat(s)` | 解析浮点数 |
| `typeof(x)` | 获取类型 |

## 日期函数

| 函数 | 说明 |
|------|------|
| `currentDate()` | 获取当前日期 |
| `dateFormat(date, pattern)` | 格式化日期 |
| `toDate(str)` | 字符串转日期 |

## 工具函数

| 函数 | 说明 |
|------|------|
| `uuid()` | 生成 UUID |
| `sleep(ms)` | 休眠指定毫秒 |
| `toJson(obj)` | 转换为 JSON 字符串 |

## 日志函数

| 函数 | 说明 |
|------|------|
| `console.log(message)` | 输出日志 |
| `console.error(message)` | 输出错误日志 |
| `log(message)` | 输出日志 |
| `debug(message)` | 输出调试日志 |

## JSON 操作

```javascript
// 解析 JSON
let obj = JSON.parse('{"name": "test"}');

// 序列化为 JSON
let str = JSON.stringify({ name: 'test' });
```

## 数组方法

```javascript
// 遍历与转换
[1, 2, 3].map(e => e * 2);         // [2, 4, 6]
[1, 2, 3].filter(e => e > 1);      // [2, 3]
[1, 2, 3].reduce((a, b) => a + b); // 6

// 查找
[1, 2, 3].find(e => e > 1);        // 2
[1, 2, 3].includes(2);             // true
[1, 2, 3].indexOf(2);              // 1

// 其他
[1, 2, 3].join(',');               // "1,2,3"
[1, 2, 3].reverse();               // [3, 2, 1]
[3, 1, 2].sort();                  // [1, 2, 3]
```
