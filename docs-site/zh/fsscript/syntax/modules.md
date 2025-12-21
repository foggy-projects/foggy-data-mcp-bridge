# 模块系统

FSScript 支持 ES6 风格的模块导入导出。

## 导入

### 导入脚本文件

```javascript
// 导入整个脚本
import 'path/to/script.fsscript'

// 具名导入
import { func1, func2 } from 'module.fsscript'

// 默认导入
import ModuleName from 'module.fsscript'

// 重命名导入
import originalName as alias from 'module.fsscript'
```

### 导入 Spring Bean

```javascript
// 导入整个 Bean
import myService from '@myServiceBean';

// 导入 Bean 的方法
import { getUserById, saveUser } from '@userService';

// 重命名
import { saveUser as save } from '@userService';
```

### 导入 Java 类

```javascript
// 导入静态方法
import { format } from 'java:java.lang.String';
import { now } from 'java:java.time.LocalDateTime';

// 导入整个类
import DateUtils from 'java:com.example.utils.DateUtils';
```

## 导出

### 导出变量

```javascript
export var x = 10;
export let y = 20;
export const z = 30;
```

### 导出函数

```javascript
export function myFunction() {
    // ...
}
```

### 默认导出

```javascript
export default value;
```

### 批量导出

```javascript
export { a, b, c };
```

## 模块化示例

```javascript
// utils.fsscript
export function formatMoney(value) {
    return value.toFixed(2) + ' 元';
}

export const TAX_RATE = 0.13;

// main.fsscript
import { formatMoney, TAX_RATE } from './utils.fsscript';

export let price = 100;
export let tax = price * TAX_RATE;
export let display = formatMoney(price + tax);
```
