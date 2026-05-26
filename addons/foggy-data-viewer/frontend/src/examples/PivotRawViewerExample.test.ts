import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { PropType } from 'vue'
import PivotRawViewerExample from './PivotRawViewerExample.vue'
import type { PivotRawPayload } from '@/types'

const PivotRawViewerStub = defineComponent({
  name: 'PivotRawViewer',
  props: {
    rawPayload: {
      type: Object as PropType<PivotRawPayload>,
      required: true
    },
    height: [String, Number],
    emptyCellText: String
  },
  setup(props) {
    return () => h('div', {
      class: 'pivot-raw-viewer-stub',
      'data-height': String(props.height),
      'data-empty-cell-text': props.emptyCellText ?? '',
      'data-row-members': String(props.rawPayload.rowMembers.length),
      'data-column-members': String(props.rawPayload.columnMembers.length),
      'data-cell-count': String(props.rawPayload.cells.length)
    })
  }
})

describe('PivotRawViewerExample', () => {
  it('renders the SOA raw pivot fixture through PivotRawViewer', () => {
    const wrapper = mount(PivotRawViewerExample, {
      global: {
        stubs: {
          PivotRawViewer: PivotRawViewerStub
        }
      }
    })

    const viewer = wrapper.find('.pivot-raw-viewer-stub')

    expect(wrapper.text()).toContain('TMS X6 SOA 应付核销候选透视表')
    expect(viewer.exists()).toBe(true)
    expect(viewer.attributes('data-height')).toBe('420')
    expect(viewer.attributes('data-empty-cell-text')).toBe('-')
    expect(viewer.attributes('data-row-members')).toBe('2')
    expect(viewer.attributes('data-column-members')).toBe('2')
    expect(viewer.attributes('data-cell-count')).toBe('8')
  })

  it('keeps the example inside a scrollable viewport shell', () => {
    const wrapper = mount(PivotRawViewerExample, {
      global: {
        stubs: {
          PivotRawViewer: PivotRawViewerStub
        }
      }
    })

    expect(wrapper.find('.pivot-raw-viewer-example').exists()).toBe(true)
    expect(wrapper.find('.example-main').exists()).toBe(true)
  })
})
