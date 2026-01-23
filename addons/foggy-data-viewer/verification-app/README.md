# Foggy Data Viewer - 验证项目

这是一个独立的验证项目，用于测试 `foggy-data-viewer` 组件库的功能。

## 项目说明

本项目使用 `file:` 协议引用本地未发布的 `foggy-data-viewer` 包，实现了以下功能验证：

- ✅ DataTable 组件的基本渲染
- ✅ 列配置（固定列、自定义宽度）
- ✅ 分页功能
- ✅ 排序功能
- ✅ 筛选功能
- ✅ 汇总行显示
- ✅ 行点击/双击事件
- ✅ buildTableColumns 工具函数

## 如何运行

### 1. 构建组件库（如果还没有构建）

```bash
cd ../frontend
npm run build:lib
```

### 2. 启动验证项目

```bash
cd verification-app
npm install
npm run dev
```

### 3. 访问应用

打开浏览器访问：http://localhost:5174/

## 项目结构

```
verification-app/
├── src/
│   ├── App.vue          # 验证应用主组件
│   ├── main.ts          # 入口文件（注册 vxe-table）
│   └── ...
├── package.json         # 依赖配置
└── README.md           # 本文件
```

## 关键配置

### package.json 依赖

```json
{
  "dependencies": {
    "vue": "^3.5.24",
    "foggy-data-viewer": "file:../frontend",  // 使用本地包
    "vxe-table": "^4.7.0",
    "vxe-pc-ui": "^4.2.0"
  }
}
```

### App.vue 使用示例

```vue
<script setup lang="ts">
import { DataTable, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema } from 'foggy-data-viewer'

// 1. 定义 QM Schema（模拟从服务器获取）
const qmSchema = [
  { name: 'id', type: 'INTEGER', title: 'ID' },
  { name: 'orderNo', type: 'TEXT', title: '订单号' },
  // ...
]

// 2. 使用 buildTableColumns 构建列配置
const columns = buildTableColumns(qmSchema, {
  visibleColumns: ['id', 'orderNo', 'customerName'],
  customizations: [
    { name: 'id', width: 80, fixed: 'left' },
    // ...
  ]
})
</script>

<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
    @page-change="handlePageChange"
    @sort-change="handleSortChange"
  />
</template>
```

## 测试功能

在验证应用中可以测试以下功能：

1. **列配置测试**
   - 固定列（ID 列固定在左侧）
   - 自定义列宽

2. **分页测试**
   - 点击页码切换页面
   - 每页显示 50 条数据
   - 总共 150 条数据（3页）

3. **排序测试**
   - 点击列头进行排序
   - 支持升序/降序/取消排序

4. **筛选测试**
   - 打开筛选面板
   - 添加筛选条件
   - 查看筛选结果

5. **汇总行测试**
   - 选中某些行
   - 查看底部汇总行的数据变化
   - 显示选中行和全量汇总

6. **事件测试**
   - 单击行查看控制台输出
   - 双击行查看弹窗详情

## 发布准备

当组件库准备发布到 npm 时：

1. 更新 `../frontend/package.json` 的版本号
2. 运行 `npm run build:lib` 构建库
3. 运行 `npm publish` 发布到 npm
4. 在使用项目中将依赖改为：`"foggy-data-viewer": "^1.0.0"`

## 相关链接

- [foggy-data-viewer 组件库](../frontend)
- [后端服务](../src/main/java)
- [测试用例](../frontend/src/components)
