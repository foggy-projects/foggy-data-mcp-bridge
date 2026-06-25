---
name: vitest-integration-testing
description: 使用 Vitest 进行集成测试的工具包。支持组件测试、API测试、端到端测试、Mock配置、测试覆盖率分析。当用户需要编写集成测试、调试测试用例、配置测试环境时使用。
license: Complete terms in LICENSE.txt
---

# Vitest 集成测试

使用 Vitest 编写高质量的集成测试，支持组件测试、API测试和端到端测试。

**辅助脚本：**
- `scripts/run_tests_with_coverage.sh` - 运行测试并生成覆盖率报告
- `scripts/setup_test_db.js` - 初始化测试数据库

**始终先使用 `--help` 运行脚本**，查看使用方法。除非绝对必要，否则不要读取脚本源码，以避免污染上下文窗口。这些脚本设计为黑盒工具，可直接调用。

## 决策树：选择测试方法

```
用户任务 → 测试什么？
    ├─ 组件测试（Vue/React组件）
    │   ├─ 需要用户交互？
    │   │   ├─ 是 → 使用 @testing-library 模拟事件
    │   │   └─ 否 → 直接挂载组件并断言渲染结果
    │   └─ 参考：examples/component_testing.test.ts
    │
    ├─ API 测试（接口/服务）
    │   ├─ 需要真实数据库？
    │   │   ├─ 是 → 使用 scripts/setup_test_db.js 初始化
    │   │   └─ 否 → 使用 vi.mock() 模拟数据层
    │   └─ 参考：examples/api_testing.test.ts
    │
    └─ 端到端测试（完整流程）
        ├─ 需要启动服务？
        │   ├─ 是 → 使用 beforeAll 启动服务
        │   └─ 否 → 直接测试业务逻辑
        └─ 参考：examples/e2e_testing.test.ts
```

## 核心原则

### 1. 测试隔离
每个测试用例必须独立运行，不依赖其他测试的执行顺序或状态。

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest'

describe('用户服务测试', () => {
  let testData: any

  beforeEach(() => {
    // 每个测试前重置数据
    testData = { id: 1, name: 'Test User' }
  })

  afterEach(() => {
    // 每个测试后清理资源
    testData = null
  })

  it('应该创建用户', () => {
    // 测试逻辑...
  })
})
```

### 2. Mock 策略

**何时使用 Mock：**
- 外部 API 调用（避免依赖外部服务）
- 数据库操作（使用内存数据库或 Mock）
- 文件系统操作
- 时间相关函数（Date.now()）

```typescript
import { vi } from 'vitest'

// Mock 外部模块
vi.mock('../api/userApi', () => ({
  fetchUser: vi.fn().mockResolvedValue({ id: 1, name: 'Mock User' })
}))

// Mock 时间
vi.useFakeTimers()
vi.setSystemTime(new Date('2024-01-01'))
```

### 3. 测试覆盖率目标

- **语句覆盖率**：≥80%
- **分支覆盖率**：≥75%
- **函数覆盖率**：≥80%
- **行覆盖率**：≥80%

运行覆盖率报告：
```bash
bash scripts/run_tests_with_coverage.sh
```

## 常见模式

### 组件测试（Vue 3）

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MyComponent from '@/components/MyComponent.vue'

describe('MyComponent', () => {
  it('应该渲染正确的文本', () => {
    const wrapper = mount(MyComponent, {
      props: { message: 'Hello' }
    })

    expect(wrapper.text()).toContain('Hello')
  })

  it('应该响应点击事件', async () => {
    const wrapper = mount(MyComponent)

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('click')).toBeTruthy()
  })
})
```

### API 测试

```typescript
import { describe, it, expect, beforeAll } from 'vitest'
import { createTestDatabase } from '../scripts/setup_test_db'
import { UserService } from '@/services/UserService'

describe('UserService 集成测试', () => {
  let userService: UserService

  beforeAll(async () => {
    // 初始化测试数据库
    await createTestDatabase()
    userService = new UserService()
  })

  it('应该创建并查询用户', async () => {
    const user = await userService.create({ name: 'Test' })
    expect(user.id).toBeDefined()

    const found = await userService.findById(user.id)
    expect(found.name).toBe('Test')
  })
})
```

### 异步测试

```typescript
import { describe, it, expect, vi } from 'vitest'

describe('异步操作测试', () => {
  it('应该等待 Promise 完成', async () => {
    const result = await fetchData()
    expect(result).toBeDefined()
  })

  it('应该处理异步错误', async () => {
    await expect(fetchInvalidData()).rejects.toThrow('Invalid data')
  })

  it('应该超时失败', async () => {
    vi.useFakeTimers()

    const promise = longRunningTask()
    vi.advanceTimersByTime(5000)

    await expect(promise).rejects.toThrow('Timeout')

    vi.useRealTimers()
  })
})
```

## 常见陷阱

### ❌ 不要：在测试中使用真实的外部依赖
```typescript
it('获取用户数据', async () => {
  const data = await fetch('https://api.example.com/users') // 坏实践
  expect(data).toBeDefined()
})
```

### ✅ 要：使用 Mock 隔离外部依赖
```typescript
import { vi } from 'vitest'

vi.mock('node-fetch', () => ({
  default: vi.fn().mockResolvedValue({
    json: () => Promise.resolve({ id: 1, name: 'Test' })
  })
}))

it('获取用户数据', async () => {
  const data = await fetchUserData()
  expect(data.name).toBe('Test')
})
```

### ❌ 不要：测试之间共享可变状态
```typescript
let sharedData = { count: 0 }

it('测试1', () => {
  sharedData.count++
  expect(sharedData.count).toBe(1)
})

it('测试2', () => {
  // 依赖测试1的执行顺序，会导致不稳定
  expect(sharedData.count).toBe(1)
})
```

### ✅ 要：每个测试独立初始化数据
```typescript
describe('计数器测试', () => {
  let data: any

  beforeEach(() => {
    data = { count: 0 }
  })

  it('测试1', () => {
    data.count++
    expect(data.count).toBe(1)
  })

  it('测试2', () => {
    expect(data.count).toBe(0) // 独立的初始状态
  })
})
```

## 最佳实践

1. **使用描述性测试名称**
   - ✅ `it('应该在用户名为空时返回验证错误', ...)`
   - ❌ `it('测试1', ...)`

2. **遵循 AAA 模式**（Arrange-Act-Assert）
   ```typescript
   it('应该计算总价', () => {
     // Arrange: 准备测试数据
     const cart = { items: [{ price: 10 }, { price: 20 }] }

     // Act: 执行操作
     const total = calculateTotal(cart)

     // Assert: 验证结果
     expect(total).toBe(30)
   })
   ```

3. **一个测试只验证一个行为**
   - 不要在一个 `it()` 中测试多个不相关的功能

4. **使用快照测试时要谨慎**
   - 仅用于稳定的输出（如组件渲染结构）
   - 避免对动态数据使用快照

5. **合理使用测试钩子**
   - `beforeAll` / `afterAll` - 整个测试套件前后执行
   - `beforeEach` / `afterEach` - 每个测试前后执行

## 配置参考

### vitest.config.ts 基础配置

```typescript
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'tests/',
        '**/*.d.ts',
        '**/*.config.*',
      ],
      all: true,
      lines: 80,
      functions: 80,
      branches: 75,
      statements: 80
    }
  }
})
```

### tests/setup.ts 示例

```typescript
import { expect, afterEach } from 'vitest'
import { cleanup } from '@testing-library/vue'
import matchers from '@testing-library/jest-dom/matchers'

// 扩展 expect 匹配器
expect.extend(matchers)

// 每个测试后清理 DOM
afterEach(() => {
  cleanup()
})
```

## 参考文件

- **examples/component_testing.test.ts** - Vue/React 组件测试示例
- **examples/api_testing.test.ts** - API 集成测试示例
- **examples/e2e_testing.test.ts** - 端到端测试示例
- **examples/mock_patterns.test.ts** - 常见 Mock 模式
- **references/vitest-api.md** - Vitest API 快速参考
- **references/testing-library-cheatsheet.md** - Testing Library 速查表

## 调试技巧

### 使用 only 和 skip

```typescript
// 只运行这个测试
it.only('调试这个测试', () => {
  // ...
})

// 跳过这个测试
it.skip('暂时跳过', () => {
  // ...
})
```

### 查看测试输出

```typescript
import { expect } from 'vitest'

it('调试数据结构', () => {
  const data = complexFunction()

  // 打印到控制台
  console.log('数据:', data)

  // 使用 debug 匹配器
  expect(data).toMatchInlineSnapshot()
})
```

### 运行单个测试文件

```bash
npx vitest run path/to/specific.test.ts
```

### 监听模式（开发时使用）

```bash
npx vitest watch
```

## 性能优化

1. **并行运行测试**（Vitest 默认行为）
2. **使用内存数据库**（如 SQLite `:memory:`）
3. **复用昂贵的设置**（使用 `beforeAll` 而不是 `beforeEach`）
4. **合理使用 Mock 减少 I/O 操作**

## 故障排查

### 测试不稳定（Flaky Tests）
- 检查是否有异步操作未正确等待
- 检查是否使用了真实的时间/随机数
- 使用 `vi.useFakeTimers()` 控制时间

### 覆盖率不准确
- 确保 `vitest.config.ts` 中的 `coverage.all: true`
- 检查 `exclude` 配置是否正确

### Mock 不生效
- 确保 `vi.mock()` 在导入语句之前
- 使用 `vi.unmock()` 清除之前的 Mock
- 检查模块路径是否正确
