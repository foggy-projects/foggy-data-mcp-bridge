# DataTable 组件发布指南

## 准备工作

### 1. 创建独立的 npm 包结构

```bash
# 创建新的包目录
mkdir foggy-datatable
cd foggy-datatable

# 初始化 package.json
npm init -y
```

### 2. 配置 package.json

```json
{
  "name": "@foggy/datatable",
  "version": "1.0.0",
  "description": "Enhanced DataTable component based on vxe-table with QM schema support",
  "main": "dist/index.js",
  "module": "dist/index.esm.js",
  "types": "dist/index.d.ts",
  "files": [
    "dist",
    "README.md",
    "LICENSE"
  ],
  "scripts": {
    "build": "vite build && vue-tsc --declaration --emitDeclarationOnly",
    "test": "vitest",
    "prepublishOnly": "npm run test && npm run build"
  },
  "keywords": [
    "vue",
    "vue3",
    "datatable",
    "vxe-table",
    "table",
    "grid",
    "qm-schema"
  ],
  "author": "Your Name <your.email@example.com>",
  "license": "MIT",
  "repository": {
    "type": "git",
    "url": "https://github.com/yourusername/foggy-datatable.git"
  },
  "bugs": {
    "url": "https://github.com/yourusername/foggy-datatable/issues"
  },
  "homepage": "https://github.com/yourusername/foggy-datatable#readme",
  "peerDependencies": {
    "vue": "^3.4.0",
    "vxe-table": "^4.6.0",
    "xe-utils": "^3.5.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "@vitest/ui": "^1.0.0",
    "typescript": "^5.3.0",
    "vite": "^5.0.0",
    "vitest": "^1.0.0",
    "vue": "^3.4.0",
    "vue-tsc": "^1.8.0",
    "vxe-table": "^4.6.0",
    "xe-utils": "^3.5.0"
  }
}
```

### 3. 组织文件结构

```
foggy-datatable/
├── src/
│   ├── components/
│   │   ├── DataTable.vue
│   │   ├── filters/
│   │   │   ├── index.ts
│   │   │   ├── TextFilter.vue
│   │   │   ├── NumberRangeFilter.vue
│   │   │   ├── DateRangeFilter.vue
│   │   │   ├── SelectFilter.vue
│   │   │   └── BoolFilter.vue
│   │   └── composables/
│   │       ├── index.ts
│   │       ├── useTableSelection.ts
│   │       └── useTableSummary.ts
│   ├── types/
│   │   └── index.ts
│   ├── utils/
│   │   └── schemaHelper.ts
│   └── index.ts  # 主入口文件
├── tests/
│   ├── DataTable.test.ts
│   └── schemaHelper.test.ts
├── vite.config.ts
├── tsconfig.json
├── package.json
├── README.md
├── LICENSE
└── CHANGELOG.md
```

### 4. 创建主入口文件

```typescript
// src/index.ts
import DataTable from './components/DataTable.vue'
import { buildTableColumns } from './utils/schemaHelper'

// 导出组件
export { DataTable }

// 导出工具函数
export { buildTableColumns }

// 导出类型
export type {
  ColumnSchema,
  EnhancedColumnSchema,
  ColumnCustomization,
  TableConfig,
  SliceRequestDef,
  OrderRequestDef,
  FilterOption,
  PaginationState,
  SortState
} from './types'

// 导出过滤器组件
export {
  TextFilter,
  NumberRangeFilter,
  DateRangeFilter,
  SelectFilter,
  BoolFilter
} from './components/filters'

// 默认导出
export default DataTable
```

### 5. 配置 Vite 构建

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'FoggyDataTable',
      fileName: (format) => `index.${format === 'es' ? 'esm' : format}.js`
    },
    rollupOptions: {
      // 确保外部化处理那些你不想打包进库的依赖
      external: ['vue', 'vxe-table', 'xe-utils'],
      output: {
        // 在 UMD 构建模式下为这些外部化的依赖提供一个全局变量
        globals: {
          vue: 'Vue',
          'vxe-table': 'VXETable',
          'xe-utils': 'XEUtils'
        }
      }
    }
  }
})
```

### 6. 配置 TypeScript

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "declaration": true,
    "declarationDir": "./dist",
    "outDir": "./dist",
    "moduleResolution": "node",
    "strict": true,
    "jsx": "preserve",
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": false
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "tests"]
}
```

## 测试策略

### 1. 安装测试依赖

```bash
npm install -D vitest @vitest/ui @vue/test-utils happy-dom
```

### 2. 配置 Vitest

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html']
    }
  }
})
```

### 3. 编写单元测试

```typescript
// tests/schemaHelper.test.ts
import { describe, it, expect } from 'vitest'
import { buildTableColumns } from '../src/utils/schemaHelper'
import type { ColumnSchema, TableConfig } from '../src/types'

describe('schemaHelper', () => {
  const mockQMSchema: ColumnSchema[] = [
    { name: 'id', type: 'INTEGER', title: 'ID' },
    { name: 'name', type: 'TEXT', title: '名称' },
    { name: 'amount', type: 'MONEY', title: '金额' }
  ]

  it('should throw error when neither visibleColumns nor showAll is provided', () => {
    const config: TableConfig = {}
    expect(() => buildTableColumns(mockQMSchema, config)).toThrow()
  })

  it('should return all columns when showAll is true', () => {
    const config: TableConfig = { showAll: true }
    const result = buildTableColumns(mockQMSchema, config)
    expect(result).toHaveLength(3)
    expect(result[0].name).toBe('id')
  })

  it('should return columns in specified order', () => {
    const config: TableConfig = {
      visibleColumns: ['amount', 'name']
    }
    const result = buildTableColumns(mockQMSchema, config)
    expect(result).toHaveLength(2)
    expect(result[0].name).toBe('amount')
    expect(result[1].name).toBe('name')
  })

  it('should apply customizations', () => {
    const config: TableConfig = {
      visibleColumns: ['id', 'name'],
      customizations: [
        { name: 'id', width: 150, fixed: 'left' },
        { name: 'name', formatter: (v) => String(v).toUpperCase() }
      ]
    }
    const result = buildTableColumns(mockQMSchema, config)
    expect(result[0].width).toBe(150)
    expect(result[0].fixed).toBe('left')
    expect(result[1].customFormatter).toBeDefined()
  })

  it('should warn when column not found in schema', () => {
    const config: TableConfig = {
      visibleColumns: ['id', 'nonexistent']
    }
    const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const result = buildTableColumns(mockQMSchema, config)
    expect(result).toHaveLength(1)
    expect(consoleSpy).toHaveBeenCalledWith(
      expect.stringContaining('nonexistent')
    )
    consoleSpy.mockRestore()
  })
})
```

```typescript
// tests/DataTable.test.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DataTable from '../src/components/DataTable.vue'
import type { EnhancedColumnSchema } from '../src/types'

describe('DataTable', () => {
  const mockColumns: EnhancedColumnSchema[] = [
    { name: 'id', type: 'INTEGER', title: 'ID', width: 100 },
    { name: 'name', type: 'TEXT', title: '名称', width: 150 }
  ]

  const mockData = [
    { id: 1, name: 'Test 1' },
    { id: 2, name: 'Test 2' }
  ]

  it('should render table with columns and data', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        total: 2,
        loading: false
      }
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('should emit page-change event', async () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        total: 100,
        loading: false
      }
    })

    // 模拟分页变化
    await wrapper.vm.$emit('page-change', 2, 50)
    expect(wrapper.emitted('page-change')).toBeTruthy()
    expect(wrapper.emitted('page-change')?.[0]).toEqual([2, 50])
  })

  it('should apply custom formatter', () => {
    const columnsWithFormatter: EnhancedColumnSchema[] = [
      {
        name: 'amount',
        type: 'MONEY',
        title: '金额',
        customFormatter: (v) => `¥${v}`
      }
    ]

    const wrapper = mount(DataTable, {
      props: {
        columns: columnsWithFormatter,
        data: [{ amount: 100 }],
        total: 1,
        loading: false
      }
    })

    expect(wrapper.exists()).toBe(true)
  })
})
```

### 4. 运行测试

```bash
# 运行测试
npm test

# 运行测试并生成覆盖率报告
npm test -- --coverage

# 运行测试 UI
npm test -- --ui
```

## 发布流程

### 1. 发布前检查清单

- [ ] 所有测试通过
- [ ] 代码覆盖率 > 80%
- [ ] 文档完整（README.md, CHANGELOG.md）
- [ ] 版本号正确（遵循语义化版本）
- [ ] LICENSE 文件存在
- [ ] .npmignore 配置正确

### 2. 创建 .npmignore

```
# .npmignore
src/
tests/
*.test.ts
*.spec.ts
.vscode/
.idea/
node_modules/
coverage/
.env
.env.*
tsconfig.json
vite.config.ts
vitest.config.ts
```

### 3. 本地测试包

```bash
# 构建包
npm run build

# 本地测试（在另一个项目中）
npm link

# 在测试项目中
npm link @foggy/datatable
```

### 4. 发布到 npm

```bash
# 登录 npm（首次）
npm login

# 发布（首次）
npm publish --access public

# 发布新版本
npm version patch  # 或 minor, major
npm publish
```

### 5. 发布到私有 npm 仓库

如果使用私有仓库（如 Verdaccio）：

```bash
# 配置 registry
npm config set registry http://your-registry.com

# 或在 package.json 中配置
{
  "publishConfig": {
    "registry": "http://your-registry.com"
  }
}

# 发布
npm publish
```

## 版本管理

遵循语义化版本（Semver）：

- **MAJOR** (1.0.0): 不兼容的 API 变更
- **MINOR** (0.1.0): 向后兼容的功能新增
- **PATCH** (0.0.1): 向后兼容的问题修正

```bash
# 更新版本
npm version patch  # 1.0.0 -> 1.0.1
npm version minor  # 1.0.0 -> 1.1.0
npm version major  # 1.0.0 -> 2.0.0
```

## 持续集成（CI/CD）

### GitHub Actions 示例

```yaml
# .github/workflows/publish.yml
name: Publish Package

on:
  push:
    tags:
      - 'v*'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      - run: npm ci
      - run: npm test
      - run: npm run build

  publish:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
          registry-url: 'https://registry.npmjs.org'
      - run: npm ci
      - run: npm run build
      - run: npm publish --access public
        env:
          NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}
```

## 使用已发布的包

```bash
# 安装
npm install @foggy/datatable

# 或
yarn add @foggy/datatable
```

```vue
<script setup>
import { DataTable, buildTableColumns } from '@foggy/datatable'
import '@foggy/datatable/dist/style.css'  // 如果有样式文件

// 使用组件...
</script>
```

## 维护建议

1. **定期更新依赖**：每月检查并更新依赖
2. **监控问题**：及时响应 GitHub Issues
3. **版本发布说明**：每次发布更新 CHANGELOG.md
4. **文档维护**：保持文档与代码同步
5. **安全审计**：定期运行 `npm audit`

## 常见问题

### Q: 如何撤销已发布的版本？

```bash
npm unpublish @foggy/datatable@1.0.0
```

注意：只能撤销 72 小时内发布的版本。

### Q: 如何发布 beta 版本？

```bash
npm version prerelease --preid=beta
npm publish --tag beta
```

### Q: 如何查看包的下载统计？

访问：https://npm-stat.com/charts.html?package=@foggy/datatable
