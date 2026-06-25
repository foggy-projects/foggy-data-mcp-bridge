import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BoolFilter from './BoolFilter.vue'

describe('BoolFilter', () => {
  it('uses compact all label for table header filters', () => {
    const wrapper = mount(BoolFilter, {
      props: {
        field: 'enabled',
        modelValue: null
      }
    })

    const buttons = wrapper.findAll('button')
    expect(buttons.map(button => button.text())).toEqual(['全', '是', '否'])
  })
})
