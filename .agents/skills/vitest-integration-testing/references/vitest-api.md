# Vitest API 快速参考

## 测试结构

### describe / it / test

```typescript
import { describe, it, test, expect } from 'vitest'

describe('测试套件名称', () => {
  it('测试用例描述', () => {
    expect(true).toBe(true)
  })

  test('test 是 it 的别名', () => {
    expect(1 + 1).toBe(2)
  })
})
```

### 测试钩子

```typescript
import { beforeAll, afterAll, beforeEach, afterEach } from 'vitest'

beforeAll(() => {
  // 在所有测试前执行一次
})

afterAll(() => {
  // 在所有测试后执行一次
})

beforeEach(() => {
  // 在每个测试前执行
})

afterEach(() => {
  // 在每个测试后执行
})
```

### 测试修饰符

```typescript
// 只运行这个测试
it.only('只运行这个', () => {})

// 跳过这个测试
it.skip('跳过这个', () => {})

// 标记为待办
it.todo('未实现的测试')

// 并发运行
it.concurrent('并发测试', async () => {})

// 多次运行
it.each([1, 2, 3])('测试 %i', (num) => {
  expect(num).toBeGreaterThan(0)
})
```

## 断言 (Expect)

### 基础匹配器

```typescript
// 相等性
expect(value).toBe(expected)           // ===
expect(value).toEqual(expected)        // 深度相等
expect(value).toStrictEqual(expected)  // 严格相等（类型也要匹配）

// 真值
expect(value).toBeTruthy()
expect(value).toBeFalsy()
expect(value).toBeDefined()
expect(value).toBeUndefined()
expect(value).toBeNull()

// 数字
expect(num).toBeGreaterThan(3)
expect(num).toBeGreaterThanOrEqual(3)
expect(num).toBeLessThan(5)
expect(num).toBeLessThanOrEqual(5)
expect(num).toBeCloseTo(0.3, 5) // 浮点数比较

// 字符串
expect(str).toMatch(/pattern/)
expect(str).toContain('substring')

// 数组/可迭代对象
expect(arr).toContain(item)
expect(arr).toHaveLength(3)
expect(arr).toContainEqual(obj)

// 对象
expect(obj).toHaveProperty('key')
expect(obj).toHaveProperty('key', value)
expect(obj).toMatchObject({ key: value })
```

### 异步匹配器

```typescript
// Promise
await expect(promise).resolves.toBe(value)
await expect(promise).rejects.toThrow()

// 异步函数
await expect(async () => {
  await doSomething()
}).rejects.toThrow('Error message')
```

### 函数/类匹配器

```typescript
// 函数
expect(fn).toThrow()
expect(fn).toThrow('Error message')
expect(fn).toThrow(ErrorClass)

// Mock 函数
expect(mockFn).toHaveBeenCalled()
expect(mockFn).toHaveBeenCalledTimes(2)
expect(mockFn).toHaveBeenCalledWith(arg1, arg2)
expect(mockFn).toHaveBeenLastCalledWith(arg)
expect(mockFn).toHaveReturnedWith(value)
```

### 快照

```typescript
expect(value).toMatchSnapshot()
expect(value).toMatchInlineSnapshot()
expect(value).toMatchSnapshot('snapshot name')
```

### 否定

```typescript
expect(value).not.toBe(unexpected)
```

## Mock API

### vi.fn() - Mock 函数

```typescript
import { vi } from 'vitest'

// 创建 Mock 函数
const mockFn = vi.fn()

// 设置返回值
mockFn.mockReturnValue(42)
mockFn.mockReturnValueOnce('first').mockReturnValue('default')

// 异步返回
mockFn.mockResolvedValue(42)
mockFn.mockRejectedValue(new Error('fail'))

// 自定义实现
mockFn.mockImplementation((a, b) => a + b)
mockFn.mockImplementationOnce(() => 'once')

// 清理
mockFn.mockClear()      // 清除调用历史
mockFn.mockReset()      // 清除调用历史和实现
mockFn.mockRestore()    // 恢复原始实现（仅对 spy）
```

### vi.spyOn() - 监视方法

```typescript
const obj = {
  method: () => 'original'
}

// 创建 spy
const spy = vi.spyOn(obj, 'method')

// 修改实现
spy.mockReturnValue('mocked')

// 恢复原始实现
spy.mockRestore()
```

### vi.mock() - Mock 模块

```typescript
// Mock 整个模块
vi.mock('./module', () => ({
  default: vi.fn(),
  namedExport: vi.fn()
}))

// 自动 Mock
vi.mock('./module')

// 取消 Mock
vi.unmock('./module')

// 动态 Mock
vi.mock('./module', async () => {
  const actual = await vi.importActual('./module')
  return {
    ...actual,
    someFunction: vi.fn()
  }
})
```

### 时间 Mock

```typescript
// 使用假时间
vi.useFakeTimers()

// 恢复真实时间
vi.useRealTimers()

// 设置系统时间
vi.setSystemTime(new Date('2024-01-01'))

// 快进时间
vi.advanceTimersByTime(1000)

// 运行所有定时器
vi.runAllTimers()

// 运行待处理的定时器
vi.runOnlyPendingTimers()
```

### 全局 Mock

```typescript
// Mock 全局对象
vi.stubGlobal('globalVar', mockValue)

// 恢复全局对象
vi.unstubAllGlobals()

// Mock 环境变量
vi.stubEnv('NODE_ENV', 'production')

// 恢复环境变量
vi.unstubAllEnvs()
```

## 配置 API

### vitest.config.ts

```typescript
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    // 环境
    environment: 'node', // 'node' | 'jsdom' | 'happy-dom'

    // 全局 API
    globals: true,

    // 设置文件
    setupFiles: ['./tests/setup.ts'],

    // 覆盖率
    coverage: {
      provider: 'v8', // 'v8' | 'istanbul'
      reporter: ['text', 'json', 'html'],
      exclude: ['node_modules/', 'tests/'],
      all: true
    },

    // 并发
    threads: true,
    maxThreads: 4,

    // 超时
    testTimeout: 10000,

    // 包含/排除
    include: ['**/*.{test,spec}.{js,ts}'],
    exclude: ['node_modules', 'dist']
  }
})
```

## 测试上下文

```typescript
import { it } from 'vitest'

it('使用上下文', ({ expect }) => {
  expect(1).toBe(1)
})

it.extend({
  myFixture: async ({}, use) => {
    // 设置
    const fixture = createFixture()
    await use(fixture)
    // 清理
    await fixture.cleanup()
  }
})('使用自定义 fixture', ({ myFixture }) => {
  // 测试代码
})
```

## 实用工具

```typescript
import { vi, expect } from 'vitest'

// 等待条件
await vi.waitFor(() => {
  expect(element).toBeVisible()
})

// 等待超时
await vi.waitFor(() => {
  expect(loaded).toBe(true)
}, { timeout: 5000 })

// 动态导入
const module = await import('./module')

// 导入实际模块
const actual = await vi.importActual('./module')

// 重置模块
vi.resetModules()
```

## 常用模式

### AAA 模式

```typescript
it('计算总和', () => {
  // Arrange - 准备
  const a = 1
  const b = 2

  // Act - 执行
  const result = add(a, b)

  // Assert - 断言
  expect(result).toBe(3)
})
```

### 参数化测试

```typescript
it.each([
  { a: 1, b: 1, expected: 2 },
  { a: 1, b: 2, expected: 3 },
  { a: 2, b: 1, expected: 3 }
])('add($a, $b) = $expected', ({ a, b, expected }) => {
  expect(add(a, b)).toBe(expected)
})
```

### 异步测试

```typescript
it('异步操作', async () => {
  const result = await fetchData()
  expect(result).toBeDefined()
})

it('Promise 拒绝', async () => {
  await expect(failingPromise()).rejects.toThrow()
})
```

## 调试

```typescript
// 打印调试信息
console.log('debug:', value)

// 只运行特定测试
it.only('调试这个', () => {})

// 跳过其他测试
describe.skip('暂时跳过', () => {})

// 查看快照
expect(value).toMatchInlineSnapshot()
```

## 性能

```typescript
import { bench } from 'vitest'

// 基准测试
bench('sort', () => {
  [1, 2, 3].sort()
})

// 比较性能
bench('map', () => {
  [1, 2, 3].map(x => x * 2)
})

bench('forEach', () => {
  [1, 2, 3].forEach(x => x * 2)
})
```
