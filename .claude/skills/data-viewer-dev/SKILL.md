---
name: data-viewer-dev
description: 开发和维护 foggy-data-viewer Vue 3 组件库（DataTable、Composables、工具函数）。当用户需要开发新组件、修改现有组件、编写测试、更新文档或构建发布时使用。
---

# Foggy Data Viewer 组件开发

开发和维护 foggy-data-viewer Vue 3 组件库的核心组件、Composables 和工具函数。

## 使用场景

当用户需要以下操作时激活此技能：
- 开发新的 Vue 3 数据表格组件或功能
- 修改或增强 DataTable、DataViewer、SearchToolbar、DataTableWithSearch 组件
- 开发或修改 Composables（useTableSummary、useTableSelection）
- 编写或更新单元测试用例
- 管理 TypeScript 类型定义
- 更新组件 API 文档
- 修复组件样式问题（过滤器、表头布局等）
- 构建组件库或准备发布到 npm
- 在验证项目中测试组件功能

## 工作目录

**主工作目录**：`addons/foggy-data-viewer/frontend`

**关键文件路径**：
- `src/components/DataTable.vue` - 主表格组件
- `src/components/DataViewer.vue` - 高层封装组件（集成 API 调用）
- `src/components/SearchToolbar.vue` - 独立搜索工具栏组件
- `src/components/DataTableWithSearch.vue` - 组合组件（SearchToolbar + DataTable）
- `src/components/composables/` - 可复用逻辑（useTableSummary.ts、useTableSelection.ts）
- `src/components/filters/` - 筛选器组件（TextFilter、NumberRangeFilter、DateRangeFilter、SelectFilter、BoolFilter）
- `src/utils/schemaHelper.ts` - 列配置构建工具（buildTableColumns）
- `src/types/index.ts` - 类型定义
- `src/index.ts` - 组件库导出入口
- `package.json` - 包配置和依赖
- `vite.config.ts` - 构建配置（lib 模式）
- `vitest.config.ts` - 测试配置

**验证项目**：`addons/foggy-data-viewer/verification-app`

## 执行流程

1. **分析需求**
   - 读取用户需求描述
   - 读取相关的现有组件代码
   - 确定需要修改的文件范围

2. **规划任务**
   - 使用 TodoWrite 工具创建任务列表
   - 分解为：开发、测试、文档、构建等子任务
   - 标记当前任务为 in_progress

3. **开发/修改组件**
   - 使用 Read 工具读取现有代码
   - 使用 Edit 或 Write 工具修改代码
   - 遵循 Vue 3 Composition API `<script setup>` 风格
   - 确保 TypeScript 类型安全
   - 使用 defineProps、defineEmits 定义接口

4. **编写或更新测试**
   - 在对应的 `.test.ts` 文件中编写测试
   - 使用 Vitest + @vue/test-utils
   - 对 vxe-table 组件使用 stub：`global: { stubs: { 'vxe-grid': true } }`
   - 确保测试覆盖新增/修改的代码

5. **更新类型定义**
   - 如有新类型，添加到 `src/types/index.ts`
   - 确保所有导出类型有明确的 interface 或 type 定义
   - 在 `src/index.ts` 中导出新类型

6. **运行测试**
   - 执行 `cd addons/foggy-data-viewer/frontend && npm test -- --run --coverage`
   - 检查测试通过率和覆盖率
   - 如有失败，修复代码或测试

7. **更新文档**（如需要）
   - 更新 `README.md` 中的 API 文档
   - 添加使用示例
   - 更新属性、事件、方法列表

8. **验证功能**（如需要）
   - 在 verification-app 中添加或修改演示代码
   - 运行 `cd verification-app && npm run dev`
   - 在浏览器中手动测试功能

9. **构建库**（如需要）
   - 执行 `cd addons/foggy-data-viewer/frontend && npm run build:lib`
   - 检查 dist/ 目录生成的文件
   - 验证构建产物大小和结构

10. **完成任务**
    - 使用 TodoWrite 标记任务为 completed
    - 向用户报告完成情况和测试结果

## 输入要求

用户需提供：
- **需求描述**（必需）：要开发/修改的功能或要修复的问题
- **目标组件**（可选）：如未指定，根据需求推断
- **是否需要测试**（默认：是）：如用户要求"快速原型"可跳过
- **是否需要构建**（默认：否）：仅在准备发布时构建

## 输出格式

**任务开始时**：
```
正在处理 foggy-data-viewer 组件开发任务...

📋 任务规划：
1. [pending] {任务1}
2. [pending] {任务2}
3. [pending] {任务3}

开始执行...
```

**任务完成时**：
```
✅ 任务完成

📊 测试结果：
- 测试文件: {数量} 个
- 测试用例: {通过数}/{总数}
- 覆盖率: {百分比}%
  - 核心组件: {百分比}%
  - Composables: {百分比}%

📦 构建结果（如有）：
- 产物大小: {大小} kB
- 输出目录: dist/

📝 修改的文件：
- {文件1}
- {文件2}
```

## 约束条件

### 代码规范
- 必须使用 TypeScript 严格模式
- 必须使用 Vue 3 Composition API `<script setup lang="ts">`
- 组件必须使用 `defineProps` 和 `defineEmits` 定义接口
- 导出的类型必须在 `src/types/` 中定义
- 禁止使用 `any` 类型，使用 `unknown` 或具体类型

### 测试要求
- 核心组件（DataTable、Composables）覆盖率必须 ≥90%
- 所有新增功能必须有对应测试用例
- 测试必须使用 stub 处理 vxe-table 组件
- 测试命名遵循：`should {预期行为} when {条件}`

### 构建要求
- 库构建必须使用 `npm run build:lib`（而非 `npm run build`）
- 外部依赖（vue、vxe-table、axios）必须标记为 external
- 构建产物必须包含：index.js、index.d.ts、style.css

### 文档要求
- 所有公开 API 必须在 README.md 中记录
- Props、Events、Methods 必须有类型和说明
- 使用示例必须是可运行的完整代码

## 决策规则

### 组件开发场景
- 如果修改 DataTable.vue → 必须同时更新 DataTable.test.ts
- 如果修改 SearchToolbar.vue → 必须同时更新 SearchToolbar.test.ts（待创建）
- 如果修改 DataTableWithSearch.vue → 必须同时更新 DataTableWithSearch.test.ts（待创建）
- 如果添加新的 Composable → 在 `src/components/composables/` 创建，并在 index.ts 导出
- 如果修改 Props → 更新 README.md 或对应文档（如 docs/SearchToolbar.md）
- 如果添加新的过滤器组件 → 在 `src/components/filters/` 创建
- 如果修改过滤器样式 → 检查是否影响 DataTable 表头布局，确保不出现换行或溢出

### 测试编写场景
- 如果测试涉及 vxe-table 组件 → 使用 `global: { stubs: { 'vxe-grid': true } }`
- 如果测试需要模拟数据 → 创建 mockData 在测试文件顶部
- 如果覆盖率下降 → 必须添加测试用例直到恢复

### 类型定义场景
- 如果类型仅在单个组件内使用 → 在组件文件内定义
- 如果类型需要导出给用户使用 → 在 `src/types/index.ts` 定义并导出
- 如果修改导出类型 → 更新 `src/index.ts` 的 type 导出

### 构建发布场景
- 如果用户要求"准备发布" → 运行测试、构建库、检查 package.json 版本
- 如果用户要求"构建" → 仅运行 `npm run build:lib`
- 如果构建失败 → 检查 vite.config.ts 的 external 配置

### 验证测试场景
- 如果用户要求"看看效果" → 在 verification-app 中添加演示并启动 dev server
- 如果需要测试复杂交互 → 在 verification-app 而非单元测试中验证
- 如果验证项目报错 → 先检查是否需要重新 `npm run build:lib`

## 技术栈参考

**核心依赖**：
- Vue 3.4+ （Composition API）
- TypeScript 5.3+
- vxe-table 4.6+ （表格组件）
- vxe-pc-ui（UI 组件）
- xe-utils（工具库）

**开发依赖**：
- Vite 5.0+ （构建工具）
- Vitest 1.0+ （测试框架）
- @vue/test-utils 2.4+ （Vue 测试工具）
- happy-dom（测试环境）
- @vitest/coverage-v8（覆盖率）

**组件库架构**：
```
foggy-data-viewer/
├── DataTable.vue              # 核心表格组件（表头过滤器、分页、排序、汇总）
├── DataViewer.vue             # 高层封装（集成 API 调用、元数据管理）
├── SearchToolbar.vue          # 独立搜索工具栏（字段级快速筛选）
├── DataTableWithSearch.vue    # 组合组件（SearchToolbar + DataTable + 属性透传）
├── composables/
│   ├── useTableSummary        # 汇总行逻辑（选中汇总、全量汇总）
│   └── useTableSelection      # 选择行逻辑（checkbox、行选中状态）
├── filters/                   # 筛选器组件（可复用的过滤控件）
│   ├── TextFilter             # 文本筛选（等于、左匹配、批量）
│   ├── SelectFilter           # 选择筛选（单选/多选、搜索）
│   ├── DateRangeFilter        # 日期范围筛选（依赖 Element Plus）
│   ├── NumberRangeFilter      # 数字范围筛选（区间、单边界）
│   └── BoolFilter             # 布尔筛选（是/否/全部）
└── utils/
    └── schemaHelper           # buildTableColumns 工具（QM Schema → 列配置）
```

## 常见模式

### 创建新 Composable
```typescript
// src/components/composables/useMyFeature.ts
import { ref, computed } from 'vue'

export function useMyFeature() {
  const state = ref(initialValue)

  const computedValue = computed(() => {
    // logic
  })

  function method() {
    // logic
  }

  return {
    state,
    computedValue,
    method
  }
}
```

### 测试 Composable
```typescript
// src/components/composables/useMyFeature.test.ts
import { describe, it, expect } from 'vitest'
import { useMyFeature } from './useMyFeature'

describe('useMyFeature', () => {
  it('should initialize with default value', () => {
    const { state } = useMyFeature()
    expect(state.value).toBe(expectedValue)
  })
})
```

### 测试 DataTable 组件
```typescript
const globalConfig = {
  global: {
    stubs: {
      'vxe-grid': true
    }
  }
}

const wrapper = mount(DataTable, {
  props: { columns, data, total, loading },
  ...globalConfig
})
```

### 导出新类型
```typescript
// src/types/index.ts
export interface MyNewType {
  field1: string
  field2: number
}

// src/index.ts
export type { MyNewType } from './types'
```
