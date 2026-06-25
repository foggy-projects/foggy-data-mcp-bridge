/**
 * Vue 3 组件测试示例
 *
 * 演示如何使用 Vitest + @vue/test-utils 测试 Vue 组件
 */

import { describe, it, expect, beforeEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'

// 假设的组件（实际使用时替换为真实组件）
const TodoItem = {
  props: ['todo', 'index'],
  template: `
    <li class="todo-item" :class="{ completed: todo.completed }">
      <input
        type="checkbox"
        :checked="todo.completed"
        @change="$emit('toggle', index)"
      />
      <span class="todo-text">{{ todo.text }}</span>
      <button class="delete-btn" @click="$emit('delete', index)">删除</button>
    </li>
  `
}

describe('TodoItem 组件', () => {
  let wrapper: VueWrapper<any>

  beforeEach(() => {
    // 每个测试前重新挂载组件
    wrapper = mount(TodoItem, {
      props: {
        todo: { text: '学习 Vitest', completed: false },
        index: 0
      }
    })
  })

  it('应该正确渲染待办事项文本', () => {
    expect(wrapper.find('.todo-text').text()).toBe('学习 Vitest')
  })

  it('应该根据 completed 状态添加 CSS 类', async () => {
    expect(wrapper.classes()).not.toContain('completed')

    await wrapper.setProps({
      todo: { text: '学习 Vitest', completed: true }
    })

    expect(wrapper.classes()).toContain('completed')
  })

  it('应该在复选框改变时触发 toggle 事件', async () => {
    const checkbox = wrapper.find('input[type="checkbox"]')

    await checkbox.trigger('change')

    expect(wrapper.emitted('toggle')).toBeTruthy()
    expect(wrapper.emitted('toggle')?.[0]).toEqual([0])
  })

  it('应该在点击删除按钮时触发 delete 事件', async () => {
    const deleteBtn = wrapper.find('.delete-btn')

    await deleteBtn.trigger('click')

    expect(wrapper.emitted('delete')).toBeTruthy()
    expect(wrapper.emitted('delete')?.[0]).toEqual([0])
  })

  it('应该正确设置复选框的选中状态', async () => {
    let checkbox = wrapper.find('input[type="checkbox"]')
    expect(checkbox.element.checked).toBe(false)

    await wrapper.setProps({
      todo: { text: '学习 Vitest', completed: true }
    })

    checkbox = wrapper.find('input[type="checkbox"]')
    expect(checkbox.element.checked).toBe(true)
  })
})

// React 组件测试示例（使用 @testing-library/react）
describe('React 组件测试示例', () => {
  it('示例：使用 Testing Library', () => {
    // import { render, screen, fireEvent } from '@testing-library/react'
    //
    // const Counter = ({ initialCount = 0 }) => {
    //   const [count, setCount] = useState(initialCount)
    //   return (
    //     <div>
    //       <span data-testid="count">{count}</span>
    //       <button onClick={() => setCount(count + 1)}>增加</button>
    //     </div>
    //   )
    // }
    //
    // render(<Counter initialCount={5} />)
    //
    // expect(screen.getByTestId('count')).toHaveTextContent('5')
    //
    // fireEvent.click(screen.getByText('增加'))
    //
    // expect(screen.getByTestId('count')).toHaveTextContent('6')

    expect(true).toBe(true) // 占位测试
  })
})
