# Testing Library 速查表

用于 Vue/React 组件测试的 Testing Library 常用 API。

## 安装

```bash
# Vue
npm install -D @vue/test-utils @testing-library/vue @testing-library/jest-dom

# React
npm install -D @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

## Vue Test Utils (@vue/test-utils)

### 挂载组件

```typescript
import { mount, shallowMount } from '@vue/test-utils'
import MyComponent from '@/components/MyComponent.vue'

// 完整挂载（包括子组件）
const wrapper = mount(MyComponent, {
  props: { msg: 'Hello' },
  slots: { default: '<p>Slot content</p>' },
  global: {
    plugins: [router, store],
    stubs: ['router-link']
  }
})

// 浅挂载（不渲染子组件）
const wrapper = shallowMount(MyComponent)
```

### 查询元素

```typescript
// CSS 选择器
wrapper.find('.class')
wrapper.find('#id')
wrapper.find('button')

// 查找所有
wrapper.findAll('li')

// 查找组件
wrapper.findComponent(ChildComponent)
wrapper.findAllComponents(ChildComponent)

// 判断是否存在
wrapper.find('.class').exists()
```

### 获取内容

```typescript
// 文本内容
wrapper.text()
wrapper.find('p').text()

// HTML
wrapper.html()

// 属性
wrapper.attributes('href')
wrapper.classes()
wrapper.classes('active')

// Props
wrapper.props('msg')

// 数据
wrapper.vm.someData
```

### 触发事件

```typescript
// 原生事件
await wrapper.trigger('click')
await wrapper.trigger('submit')
await wrapper.find('input').trigger('input')

// 自定义事件
await wrapper.vm.$emit('custom-event', payload)

// 检查事件
wrapper.emitted('click')
wrapper.emitted('custom-event')?.[0][0] // 第一次调用的第一个参数
```

### 更新组件

```typescript
// 更新 props
await wrapper.setProps({ msg: 'New message' })

// 更新 data
await wrapper.setData({ count: 5 })

// 输入值
await wrapper.find('input').setValue('text')

// 选择框
await wrapper.find('select').setValue('option2')

// 复选框
await wrapper.find('input[type="checkbox"]').setValue(true)

// 等待 DOM 更新
await wrapper.vm.$nextTick()
```

### 测试插槽

```typescript
const wrapper = mount(MyComponent, {
  slots: {
    default: '<p>Default slot</p>',
    named: '<p>Named slot</p>',
    scoped: '<template #scoped="{ data }">{{ data }}</template>'
  }
})
```

### 测试组合式 API

```typescript
import { ref } from 'vue'

const MyComponent = {
  setup() {
    const count = ref(0)
    const increment = () => count.value++
    return { count, increment }
  }
}

const wrapper = mount(MyComponent)
expect(wrapper.vm.count).toBe(0)
wrapper.vm.increment()
expect(wrapper.vm.count).toBe(1)
```

## Testing Library for Vue

### 渲染组件

```typescript
import { render, screen } from '@testing-library/vue'
import MyComponent from '@/components/MyComponent.vue'

const { container, getByText, queryByText, findByText } = render(MyComponent, {
  props: { msg: 'Hello' }
})
```

### 查询 API

#### getBy* - 期望元素存在（不存在会抛错）

```typescript
import { screen } from '@testing-library/vue'

// 按文本
screen.getByText('Submit')
screen.getByText(/submit/i) // 正则，忽略大小写

// 按角色
screen.getByRole('button', { name: 'Submit' })
screen.getByRole('textbox', { name: 'Email' })

// 按标签
screen.getByLabelText('Email')

// 按占位符
screen.getByPlaceholderText('Enter email')

// 按 alt 文本
screen.getByAltText('Profile picture')

// 按 title
screen.getByTitle('Close')

// 按 test-id
screen.getByTestId('submit-btn')
```

#### queryBy* - 可能不存在（返回 null）

```typescript
const button = screen.queryByText('Submit')
expect(button).toBeNull()
```

#### findBy* - 异步查询（返回 Promise）

```typescript
const button = await screen.findByText('Loaded data')
```

#### *AllBy* - 查询多个

```typescript
const buttons = screen.getAllByRole('button')
expect(buttons).toHaveLength(3)
```

### 用户交互

```typescript
import { fireEvent } from '@testing-library/vue'

// 点击
await fireEvent.click(screen.getByText('Submit'))

// 输入
const input = screen.getByLabelText('Email')
await fireEvent.update(input, 'test@example.com')

// 其他事件
await fireEvent.focus(element)
await fireEvent.blur(element)
await fireEvent.submit(form)
```

### 等待

```typescript
import { waitFor, waitForElementToBeRemoved } from '@testing-library/vue'

// 等待条件满足
await waitFor(() => {
  expect(screen.getByText('Success')).toBeInTheDocument()
})

// 等待元素移除
await waitForElementToBeRemoved(() => screen.queryByText('Loading...'))

// 带选项
await waitFor(
  () => {
    expect(screen.getByText('Done')).toBeInTheDocument()
  },
  { timeout: 3000, interval: 100 }
)
```

### 清理

```typescript
import { cleanup } from '@testing-library/vue'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})
```

## Testing Library for React

### 渲染组件

```typescript
import { render, screen } from '@testing-library/react'

const MyComponent = ({ name }) => <div>Hello, {name}!</div>

render(<MyComponent name="World" />)
```

### User Event（推荐使用）

```typescript
import userEvent from '@testing-library/user-event'

// 创建用户实例
const user = userEvent.setup()

// 点击
await user.click(screen.getByText('Submit'))

// 输入
await user.type(screen.getByLabelText('Email'), 'test@example.com')

// 清空输入
await user.clear(screen.getByLabelText('Email'))

// 选择
await user.selectOptions(screen.getByRole('combobox'), 'option1')

// 复制粘贴
await user.copy()
await user.paste('pasted text')

// 键盘
await user.keyboard('{Enter}')
await user.keyboard('{Shift>}A{/Shift}') // Shift+A
```

## jest-dom 匹配器

```typescript
import '@testing-library/jest-dom'

// 文档中
expect(element).toBeInTheDocument()
expect(element).not.toBeInTheDocument()

// 可见性
expect(element).toBeVisible()
expect(element).not.toBeVisible()

// 启用/禁用
expect(button).toBeEnabled()
expect(button).toBeDisabled()

// 表单
expect(input).toHaveValue('text')
expect(checkbox).toBeChecked()
expect(select).toHaveDisplayValue('Option 1')

// 文本内容
expect(element).toHaveTextContent('Hello')
expect(element).toHaveTextContent(/hello/i)

// 属性
expect(element).toHaveAttribute('href', '/home')
expect(element).toHaveClass('active')
expect(element).toHaveStyle('color: red')

// 焦点
expect(input).toHaveFocus()
```

## 常见模式

### 表单测试

```typescript
import { render, screen } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'

it('提交表单', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()

  render(MyForm, {
    props: { onSubmit }
  })

  // 填写表单
  await user.type(screen.getByLabelText('Name'), 'John')
  await user.type(screen.getByLabelText('Email'), 'john@example.com')

  // 提交
  await user.click(screen.getByRole('button', { name: 'Submit' }))

  // 验证
  expect(onSubmit).toHaveBeenCalledWith({
    name: 'John',
    email: 'john@example.com'
  })
})
```

### 异步内容测试

```typescript
it('加载数据', async () => {
  render(MyComponent)

  // 等待加载完成
  expect(screen.getByText('Loading...')).toBeInTheDocument()

  // 等待数据显示
  const data = await screen.findByText('Loaded data')
  expect(data).toBeInTheDocument()

  // 加载提示消失
  expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
})
```

### 测试路由

```typescript
import { createMemoryHistory, createRouter } from 'vue-router'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/about', component: About }
  ]
})

render(App, {
  global: {
    plugins: [router]
  }
})

await router.push('/about')
await router.isReady()

expect(screen.getByText('About Page')).toBeInTheDocument()
```

## 调试技巧

```typescript
import { screen } from '@testing-library/vue'

// 打印 DOM 树
screen.debug()

// 打印特定元素
screen.debug(screen.getByText('Hello'))

// 查看所有角色
screen.logTestingPlaygroundURL()

// 显示可访问性树
console.log(prettyDOM(container))
```

## 最佳实践

1. **优先级顺序**：
   - `getByRole` > `getByLabelText` > `getByPlaceholderText` > `getByText` > `getByTestId`

2. **避免使用**：
   - CSS 类选择器
   - 内部实现细节

3. **推荐使用**：
   - 语义化查询（role, label）
   - 用户行为模拟（userEvent）

4. **异步处理**：
   - 使用 `findBy*` 或 `waitFor`
   - 避免使用 `act()` 除非必要
