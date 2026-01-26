/**
 * API 集成测试示例
 *
 * 演示如何测试 API 服务、数据库交互和外部依赖
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'

// 模拟的数据库连接
class Database {
  private data: Map<number, any> = new Map()
  private nextId = 1

  async insert(table: string, record: any) {
    const id = this.nextId++
    this.data.set(id, { ...record, id })
    return { ...record, id }
  }

  async findById(table: string, id: number) {
    return this.data.get(id) || null
  }

  async findAll(table: string) {
    return Array.from(this.data.values())
  }

  async update(table: string, id: number, updates: any) {
    const existing = this.data.get(id)
    if (!existing) throw new Error('Record not found')
    const updated = { ...existing, ...updates }
    this.data.set(id, updated)
    return updated
  }

  async delete(table: string, id: number) {
    return this.data.delete(id)
  }

  clear() {
    this.data.clear()
    this.nextId = 1
  }
}

// 用户服务
class UserService {
  constructor(private db: Database) {}

  async createUser(userData: { name: string; email: string }) {
    // 验证邮箱唯一性
    const existing = await this.db.findAll('users')
    if (existing.some((u: any) => u.email === userData.email)) {
      throw new Error('Email already exists')
    }

    return this.db.insert('users', {
      ...userData,
      createdAt: new Date().toISOString()
    })
  }

  async getUserById(id: number) {
    const user = await this.db.findById('users', id)
    if (!user) throw new Error('User not found')
    return user
  }

  async updateUser(id: number, updates: Partial<{ name: string; email: string }>) {
    return this.db.update('users', id, updates)
  }

  async deleteUser(id: number) {
    return this.db.delete('users', id)
  }

  async getAllUsers() {
    return this.db.findAll('users')
  }
}

describe('UserService 集成测试', () => {
  let db: Database
  let userService: UserService

  beforeAll(() => {
    // 初始化测试数据库
    db = new Database()
    userService = new UserService(db)
  })

  beforeEach(() => {
    // 每个测试前清空数据
    db.clear()
  })

  afterAll(() => {
    // 清理资源
    db.clear()
  })

  describe('createUser', () => {
    it('应该成功创建用户', async () => {
      const userData = { name: '张三', email: 'zhangsan@test.com' }

      const user = await userService.createUser(userData)

      expect(user.id).toBeDefined()
      expect(user.name).toBe('张三')
      expect(user.email).toBe('zhangsan@test.com')
      expect(user.createdAt).toBeDefined()
    })

    it('应该拒绝重复的邮箱', async () => {
      await userService.createUser({ name: '用户1', email: 'duplicate@test.com' })

      await expect(
        userService.createUser({ name: '用户2', email: 'duplicate@test.com' })
      ).rejects.toThrow('Email already exists')
    })
  })

  describe('getUserById', () => {
    it('应该返回存在的用户', async () => {
      const created = await userService.createUser({ name: '李四', email: 'lisi@test.com' })

      const user = await userService.getUserById(created.id)

      expect(user.name).toBe('李四')
      expect(user.email).toBe('lisi@test.com')
    })

    it('应该在用户不存在时抛出错误', async () => {
      await expect(userService.getUserById(999)).rejects.toThrow('User not found')
    })
  })

  describe('updateUser', () => {
    it('应该更新用户信息', async () => {
      const user = await userService.createUser({ name: '王五', email: 'wangwu@test.com' })

      const updated = await userService.updateUser(user.id, { name: '王五改名' })

      expect(updated.name).toBe('王五改名')
      expect(updated.email).toBe('wangwu@test.com') // 邮箱未改变
    })
  })

  describe('deleteUser', () => {
    it('应该删除用户', async () => {
      const user = await userService.createUser({ name: '赵六', email: 'zhaoliu@test.com' })

      const deleted = await userService.deleteUser(user.id)

      expect(deleted).toBe(true)
      await expect(userService.getUserById(user.id)).rejects.toThrow('User not found')
    })
  })

  describe('getAllUsers', () => {
    it('应该返回所有用户', async () => {
      await userService.createUser({ name: '用户1', email: 'user1@test.com' })
      await userService.createUser({ name: '用户2', email: 'user2@test.com' })
      await userService.createUser({ name: '用户3', email: 'user3@test.com' })

      const users = await userService.getAllUsers()

      expect(users).toHaveLength(3)
      expect(users.map((u: any) => u.name)).toEqual(['用户1', '用户2', '用户3'])
    })
  })
})

// Mock 外部 API 示例
describe('外部 API Mock 示例', () => {
  it('应该正确 Mock fetch 请求', async () => {
    // Mock fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: 1, name: 'Mock User' })
    })

    const response = await fetch('https://api.example.com/users/1')
    const data = await response.json()

    expect(data.name).toBe('Mock User')
    expect(fetch).toHaveBeenCalledWith('https://api.example.com/users/1')
  })

  it('应该处理 API 错误', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found'
    })

    const response = await fetch('https://api.example.com/users/999')

    expect(response.ok).toBe(false)
    expect(response.status).toBe(404)
  })
})
