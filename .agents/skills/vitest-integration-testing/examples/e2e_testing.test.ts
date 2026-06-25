/**
 * 端到端（E2E）测试示例
 *
 * 演示如何测试完整的用户流程和业务场景
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'

// 模拟一个简单的购物车系统
interface Product {
  id: number
  name: string
  price: number
  stock: number
}

interface CartItem {
  productId: number
  quantity: number
}

class ProductService {
  private products: Map<number, Product> = new Map()

  addProduct(product: Product) {
    this.products.set(product.id, product)
  }

  getProduct(id: number): Product | undefined {
    return this.products.get(id)
  }

  updateStock(id: number, quantity: number) {
    const product = this.products.get(id)
    if (product) {
      product.stock = quantity
    }
  }

  clear() {
    this.products.clear()
  }
}

class ShoppingCart {
  private items: CartItem[] = []

  constructor(private productService: ProductService) {}

  addItem(productId: number, quantity: number = 1) {
    const product = this.productService.getProduct(productId)
    if (!product) throw new Error('Product not found')
    if (product.stock < quantity) throw new Error('Insufficient stock')

    const existingItem = this.items.find(item => item.productId === productId)
    if (existingItem) {
      existingItem.quantity += quantity
    } else {
      this.items.push({ productId, quantity })
    }
  }

  removeItem(productId: number) {
    const index = this.items.findIndex(item => item.productId === productId)
    if (index !== -1) {
      this.items.splice(index, 1)
    }
  }

  updateQuantity(productId: number, quantity: number) {
    const item = this.items.find(item => item.productId === productId)
    if (!item) throw new Error('Item not in cart')

    const product = this.productService.getProduct(productId)
    if (!product) throw new Error('Product not found')
    if (product.stock < quantity) throw new Error('Insufficient stock')

    item.quantity = quantity
  }

  getTotal(): number {
    return this.items.reduce((total, item) => {
      const product = this.productService.getProduct(item.productId)
      return total + (product ? product.price * item.quantity : 0)
    }, 0)
  }

  getItems(): CartItem[] {
    return [...this.items]
  }

  clear() {
    this.items = []
  }
}

class OrderService {
  private orders: Array<{
    id: number
    items: CartItem[]
    total: number
    status: string
    createdAt: Date
  }> = []
  private nextOrderId = 1

  createOrder(cart: ShoppingCart, productService: ProductService) {
    const items = cart.getItems()
    if (items.length === 0) {
      throw new Error('Cart is empty')
    }

    // 检查库存并扣减
    for (const item of items) {
      const product = productService.getProduct(item.productId)
      if (!product) throw new Error(`Product ${item.productId} not found`)
      if (product.stock < item.quantity) {
        throw new Error(`Insufficient stock for ${product.name}`)
      }
      productService.updateStock(item.productId, product.stock - item.quantity)
    }

    const order = {
      id: this.nextOrderId++,
      items: [...items],
      total: cart.getTotal(),
      status: 'pending',
      createdAt: new Date()
    }

    this.orders.push(order)
    cart.clear()

    return order
  }

  getOrder(orderId: number) {
    return this.orders.find(order => order.id === orderId)
  }

  clear() {
    this.orders = []
    this.nextOrderId = 1
  }
}

describe('电商系统 E2E 测试', () => {
  let productService: ProductService
  let orderService: OrderService

  beforeAll(() => {
    productService = new ProductService()
    orderService = new OrderService()
  })

  beforeEach(() => {
    // 重置所有服务
    productService.clear()
    orderService.clear()

    // 初始化测试商品
    productService.addProduct({ id: 1, name: 'iPhone 15', price: 5999, stock: 10 })
    productService.addProduct({ id: 2, name: 'MacBook Pro', price: 12999, stock: 5 })
    productService.addProduct({ id: 3, name: 'AirPods Pro', price: 1999, stock: 20 })
  })

  afterAll(() => {
    productService.clear()
    orderService.clear()
  })

  describe('场景1: 用户购买单个商品', () => {
    it('应该成功完成购买流程', () => {
      // 1. 创建购物车
      const cart = new ShoppingCart(productService)

      // 2. 添加商品到购物车
      cart.addItem(1, 2) // 购买 2 台 iPhone

      // 3. 验证购物车总价
      expect(cart.getTotal()).toBe(5999 * 2)

      // 4. 创建订单
      const order = orderService.createOrder(cart, productService)

      // 5. 验证订单信息
      expect(order.id).toBe(1)
      expect(order.total).toBe(11998)
      expect(order.status).toBe('pending')
      expect(order.items).toHaveLength(1)

      // 6. 验证库存已扣减
      const product = productService.getProduct(1)
      expect(product?.stock).toBe(8) // 10 - 2 = 8
    })
  })

  describe('场景2: 用户购买多个商品', () => {
    it('应该正确计算多商品总价并扣减库存', () => {
      const cart = new ShoppingCart(productService)

      // 添加多个商品
      cart.addItem(1, 1) // 1 台 iPhone
      cart.addItem(2, 1) // 1 台 MacBook
      cart.addItem(3, 2) // 2 个 AirPods

      // 验证总价
      const expectedTotal = 5999 + 12999 + 1999 * 2
      expect(cart.getTotal()).toBe(expectedTotal)

      // 创建订单
      const order = orderService.createOrder(cart, productService)

      expect(order.items).toHaveLength(3)
      expect(order.total).toBe(expectedTotal)

      // 验证各商品库存
      expect(productService.getProduct(1)?.stock).toBe(9)
      expect(productService.getProduct(2)?.stock).toBe(4)
      expect(productService.getProduct(3)?.stock).toBe(18)
    })
  })

  describe('场景3: 处理库存不足', () => {
    it('应该在库存不足时拒绝添加商品', () => {
      const cart = new ShoppingCart(productService)

      // 尝试购买超过库存的数量
      expect(() => {
        cart.addItem(2, 10) // MacBook 只有 5 个库存
      }).toThrow('Insufficient stock')

      // 购物车应该为空
      expect(cart.getItems()).toHaveLength(0)
    })

    it('应该在下单时检测库存不足', () => {
      const cart = new ShoppingCart(productService)

      // 先添加商品
      cart.addItem(2, 3) // 添加 3 台 MacBook

      // 手动修改库存模拟并发购买
      productService.updateStock(2, 1) // 库存降到 1

      // 下单应该失败
      expect(() => {
        orderService.createOrder(cart, productService)
      }).toThrow('Insufficient stock')
    })
  })

  describe('场景4: 修改购物车', () => {
    it('应该支持更新商品数量', () => {
      const cart = new ShoppingCart(productService)

      cart.addItem(1, 1)
      expect(cart.getTotal()).toBe(5999)

      // 更新数量
      cart.updateQuantity(1, 3)
      expect(cart.getTotal()).toBe(5999 * 3)
    })

    it('应该支持删除商品', () => {
      const cart = new ShoppingCart(productService)

      cart.addItem(1, 2)
      cart.addItem(3, 1)
      expect(cart.getItems()).toHaveLength(2)

      // 删除商品
      cart.removeItem(1)
      expect(cart.getItems()).toHaveLength(1)
      expect(cart.getTotal()).toBe(1999)
    })
  })

  describe('场景5: 边界情况', () => {
    it('应该拒绝空购物车下单', () => {
      const cart = new ShoppingCart(productService)

      expect(() => {
        orderService.createOrder(cart, productService)
      }).toThrow('Cart is empty')
    })

    it('应该拒绝不存在的商品', () => {
      const cart = new ShoppingCart(productService)

      expect(() => {
        cart.addItem(999, 1)
      }).toThrow('Product not found')
    })

    it('应该支持同一商品多次添加', () => {
      const cart = new ShoppingCart(productService)

      cart.addItem(1, 1)
      cart.addItem(1, 2)

      const items = cart.getItems()
      expect(items).toHaveLength(1)
      expect(items[0].quantity).toBe(3)
    })
  })

  describe('场景6: 完整的用户旅程', () => {
    it('应该支持用户从浏览到完成订单的完整流程', () => {
      // 1. 用户浏览商品
      const iphone = productService.getProduct(1)
      const macbook = productService.getProduct(2)
      expect(iphone).toBeDefined()
      expect(macbook).toBeDefined()

      // 2. 创建购物车并添加商品
      const cart = new ShoppingCart(productService)
      cart.addItem(1, 1)
      cart.addItem(2, 1)

      // 3. 用户修改数量
      cart.updateQuantity(1, 2)

      // 4. 查看购物车总价
      const total = cart.getTotal()
      expect(total).toBe(5999 * 2 + 12999)

      // 5. 提交订单
      const order = orderService.createOrder(cart, productService)

      // 6. 验证订单
      expect(order.total).toBe(total)
      expect(order.status).toBe('pending')

      // 7. 验证购物车已清空
      expect(cart.getItems()).toHaveLength(0)

      // 8. 验证可以查询订单
      const retrievedOrder = orderService.getOrder(order.id)
      expect(retrievedOrder).toEqual(order)

      // 9. 验证库存已更新
      expect(productService.getProduct(1)?.stock).toBe(8)
      expect(productService.getProduct(2)?.stock).toBe(4)
    })
  })
})
