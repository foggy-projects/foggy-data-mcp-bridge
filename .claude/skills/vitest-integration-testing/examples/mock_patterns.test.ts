/**
 * Mock 模式示例
 *
 * 演示 Vitest 中常见的 Mock 技巧和最佳实践
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// ==================== 模式 1: Mock 函数 ====================

describe('Mock 函数', () => {
  it('应该创建并验证 Mock 函数调用', () => {
    const mockFn = vi.fn()

    mockFn('hello')
    mockFn('world')

    expect(mockFn).toHaveBeenCalledTimes(2)
    expect(mockFn).toHaveBeenCalledWith('hello')
    expect(mockFn).toHaveBeenLastCalledWith('world')
  })

  it('应该设置 Mock 函数的返回值', () => {
    const mockFn = vi.fn()
      .mockReturnValueOnce('first')
      .mockReturnValueOnce('second')
      .mockReturnValue('default')

    expect(mockFn()).toBe('first')
    expect(mockFn()).toBe('second')
    expect(mockFn()).toBe('default')
    expect(mockFn()).toBe('default')
  })

  it('应该 Mock 异步函数', async () => {
    const mockAsyncFn = vi.fn()
      .mockResolvedValueOnce({ id: 1, name: 'First' })
      .mockResolvedValue({ id: 2, name: 'Second' })

    const result1 = await mockAsyncFn()
    const result2 = await mockAsyncFn()

    expect(result1.name).toBe('First')
    expect(result2.name).toBe('Second')
  })

  it('应该 Mock 拒绝的 Promise', async () => {
    const mockFn = vi.fn().mockRejectedValue(new Error('Network error'))

    await expect(mockFn()).rejects.toThrow('Network error')
  })
})

// ==================== 模式 2: Mock 模块 ====================

// 假设有一个 API 模块
const userApi = {
  fetchUser: async (id: number) => {
    // 实际会调用 API
    throw new Error('Should be mocked')
  },
  createUser: async (data: any) => {
    throw new Error('Should be mocked')
  }
}

describe('Mock 模块', () => {
  beforeEach(() => {
    // Mock 整个模块
    vi.spyOn(userApi, 'fetchUser').mockResolvedValue({
      id: 1,
      name: 'Mock User',
      email: 'mock@test.com'
    })

    vi.spyOn(userApi, 'createUser').mockResolvedValue({
      id: 2,
      name: 'New User',
      email: 'new@test.com'
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('应该使用 Mock 的 API 调用', async () => {
    const user = await userApi.fetchUser(1)

    expect(user.name).toBe('Mock User')
    expect(userApi.fetchUser).toHaveBeenCalledWith(1)
  })

  it('应该 Mock 创建用户', async () => {
    const newUser = await userApi.createUser({ name: 'Test', email: 'test@test.com' })

    expect(newUser.id).toBe(2)
    expect(userApi.createUser).toHaveBeenCalledWith({ name: 'Test', email: 'test@test.com' })
  })
})

// ==================== 模式 3: Mock 类 ====================

class Database {
  async query(sql: string): Promise<any[]> {
    throw new Error('Should be mocked')
  }

  async execute(sql: string): Promise<void> {
    throw new Error('Should be mocked')
  }
}

describe('Mock 类', () => {
  it('应该 Mock 类的方法', async () => {
    const db = new Database()

    vi.spyOn(db, 'query').mockResolvedValue([
      { id: 1, name: 'User 1' },
      { id: 2, name: 'User 2' }
    ])

    const results = await db.query('SELECT * FROM users')

    expect(results).toHaveLength(2)
    expect(db.query).toHaveBeenCalledWith('SELECT * FROM users')
  })

  it('应该 Mock 类的构造函数', () => {
    // 创建一个完整的 Mock 类
    const MockDatabase = vi.fn().mockImplementation(() => ({
      query: vi.fn().mockResolvedValue([]),
      execute: vi.fn().mockResolvedValue(undefined)
    }))

    const db = new MockDatabase()

    expect(MockDatabase).toHaveBeenCalled()
    expect(db.query).toBeDefined()
  })
})

// ==================== 模式 4: Mock 时间 ====================

describe('Mock 时间', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('应该控制 Date.now()', () => {
    const now = new Date('2024-01-01T00:00:00.000Z')
    vi.setSystemTime(now)

    expect(Date.now()).toBe(now.getTime())
    expect(new Date().toISOString()).toBe('2024-01-01T00:00:00.000Z')
  })

  it('应该快进时间', () => {
    const start = Date.now()

    vi.advanceTimersByTime(1000)

    expect(Date.now() - start).toBe(1000)
  })

  it('应该测试 setTimeout', () => {
    const callback = vi.fn()

    setTimeout(callback, 1000)

    expect(callback).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1000)

    expect(callback).toHaveBeenCalledTimes(1)
  })

  it('应该测试 setInterval', () => {
    const callback = vi.fn()

    setInterval(callback, 100)

    vi.advanceTimersByTime(250)

    expect(callback).toHaveBeenCalledTimes(2)

    vi.advanceTimersByTime(100)

    expect(callback).toHaveBeenCalledTimes(3)
  })
})

// ==================== 模式 5: 部分 Mock ====================

const userService = {
  validateEmail: (email: string) => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  },
  hashPassword: (password: string) => {
    return `hashed_${password}`
  },
  createUser: async (data: { email: string; password: string }) => {
    if (!userService.validateEmail(data.email)) {
      throw new Error('Invalid email')
    }
    const hashedPassword = userService.hashPassword(data.password)
    // 实际会调用数据库
    return { id: 1, email: data.email, password: hashedPassword }
  }
}

describe('部分 Mock', () => {
  it('应该只 Mock 部分方法', async () => {
    // 只 Mock hashPassword，保留其他方法的真实实现
    vi.spyOn(userService, 'hashPassword').mockReturnValue('mock_hash')

    const user = await userService.createUser({
      email: 'test@test.com',
      password: 'secret'
    })

    expect(user.password).toBe('mock_hash')
    expect(userService.hashPassword).toHaveBeenCalledWith('secret')

    // validateEmail 仍然是真实实现
    await expect(
      userService.createUser({ email: 'invalid', password: 'secret' })
    ).rejects.toThrow('Invalid email')

    vi.restoreAllMocks()
  })
})

// ==================== 模式 6: Mock 环境变量 ====================

describe('Mock 环境变量', () => {
  const originalEnv = process.env

  beforeEach(() => {
    vi.resetModules()
    process.env = { ...originalEnv }
  })

  afterEach(() => {
    process.env = originalEnv
  })

  it('应该 Mock 环境变量', () => {
    process.env.NODE_ENV = 'production'
    process.env.API_URL = 'https://api.production.com'

    expect(process.env.NODE_ENV).toBe('production')
    expect(process.env.API_URL).toBe('https://api.production.com')
  })
})

// ==================== 模式 7: Mock 全局对象 ====================

describe('Mock 全局对象', () => {
  it('应该 Mock fetch', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ message: 'Success' })
    })

    const response = await fetch('https://api.example.com/data')
    const data = await response.json()

    expect(data.message).toBe('Success')
    expect(fetch).toHaveBeenCalledWith('https://api.example.com/data')
  })

  it('应该 Mock localStorage', () => {
    const localStorageMock = {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    }

    global.localStorage = localStorageMock as any

    localStorage.setItem('key', 'value')
    expect(localStorageMock.setItem).toHaveBeenCalledWith('key', 'value')
  })

  it('应该 Mock console', () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {})

    console.log('test message')

    expect(consoleSpy).toHaveBeenCalledWith('test message')

    consoleSpy.mockRestore()
  })
})

// ==================== 模式 8: Mock 实现 ====================

describe('Mock 实现', () => {
  it('应该使用自定义实现', () => {
    const mockFn = vi.fn((a: number, b: number) => a + b)

    expect(mockFn(2, 3)).toBe(5)
    expect(mockFn).toHaveBeenCalledWith(2, 3)
  })

  it('应该改变 Mock 实现', () => {
    const mockFn = vi.fn()

    mockFn.mockImplementation(() => 'first')
    expect(mockFn()).toBe('first')

    mockFn.mockImplementation(() => 'second')
    expect(mockFn()).toBe('second')
  })

  it('应该使用一次性实现', () => {
    const mockFn = vi.fn()
      .mockImplementationOnce(() => 'once')
      .mockImplementation(() => 'default')

    expect(mockFn()).toBe('once')
    expect(mockFn()).toBe('default')
    expect(mockFn()).toBe('default')
  })
})

// ==================== 模式 9: Spy 模式 ====================

describe('Spy 模式', () => {
  it('应该监视真实函数调用', () => {
    const obj = {
      method: (x: number) => x * 2
    }

    const spy = vi.spyOn(obj, 'method')

    const result = obj.method(5)

    expect(result).toBe(10) // 真实实现仍然执行
    expect(spy).toHaveBeenCalledWith(5)

    spy.mockRestore()
  })

  it('应该在 Spy 后修改实现', () => {
    const obj = {
      calculate: (x: number) => x * 2
    }

    const spy = vi.spyOn(obj, 'calculate').mockReturnValue(100)

    expect(obj.calculate(5)).toBe(100)
    expect(spy).toHaveBeenCalledWith(5)

    spy.mockRestore()
  })
})

// ==================== 模式 10: 清理 Mock ====================

describe('清理 Mock', () => {
  it('应该清除 Mock 调用历史', () => {
    const mockFn = vi.fn()

    mockFn('first')
    expect(mockFn).toHaveBeenCalledTimes(1)

    mockFn.mockClear()

    expect(mockFn).toHaveBeenCalledTimes(0)

    mockFn('second')
    expect(mockFn).toHaveBeenCalledTimes(1)
  })

  it('应该重置 Mock 实现', () => {
    const mockFn = vi.fn().mockReturnValue('mocked')

    expect(mockFn()).toBe('mocked')

    mockFn.mockReset()

    expect(mockFn()).toBeUndefined()
  })

  it('应该恢复原始实现', () => {
    const obj = {
      method: () => 'original'
    }

    const spy = vi.spyOn(obj, 'method').mockReturnValue('mocked')

    expect(obj.method()).toBe('mocked')

    spy.mockRestore()

    expect(obj.method()).toBe('original')
  })
})
