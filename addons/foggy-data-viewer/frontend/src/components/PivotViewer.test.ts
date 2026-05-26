import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { PropType } from 'vue'
import PivotViewer from './PivotViewer.vue'
import { soaCandidatePivotFixture } from '@/__fixtures__/pivot/soaCandidatePivot'
import type { PivotViewModel } from '@/types'

const PivotGridStub = defineComponent({
  name: 'PivotGrid',
  props: {
    viewModel: {
      type: Object as PropType<PivotViewModel>,
      required: true
    },
    loading: Boolean,
    height: [String, Number],
    emptyText: String,
    emptyCellText: String
  },
  setup(props, { slots }) {
    return () => h('div', {
      class: 'pivot-grid-stub',
      'data-loading': String(props.loading),
      'data-height': String(props.height),
      'data-empty-cell-text': props.emptyCellText
    }, props.viewModel.items.length ? 'grid' : slots.empty?.())
  }
})

function mountPivotViewer(extraProps: Record<string, unknown> = {}) {
  return mount(PivotViewer, {
    props: {
      viewModel: soaCandidatePivotFixture,
      ...extraProps
    },
    global: {
      stubs: {
        PivotGrid: PivotGridStub
      }
    }
  })
}

describe('PivotViewer', () => {
  it('renders grid shape through PivotGrid', () => {
    const wrapper = mountPivotViewer({
      loading: true,
      height: 420,
      emptyCellText: 'N/A'
    })

    const grid = wrapper.find('.pivot-grid-stub')

    expect(wrapper.attributes('data-view-mode')).toBe('pivotTable')
    expect(grid.exists()).toBe(true)
    expect(grid.attributes('data-loading')).toBe('true')
    expect(grid.attributes('data-height')).toBe('420')
    expect(grid.attributes('data-empty-cell-text')).toBe('N/A')
  })

  it('renders axis pager and evidence panel by default', () => {
    const wrapper = mountPivotViewer()

    expect(wrapper.find('.pivot-axis-pager').exists()).toBe(true)
    expect(wrapper.text()).toContain('运单')
    expect(wrapper.find('.pivot-evidence-panel').exists()).toBe(true)
    expect(wrapper.text()).toContain('global_slice_and_surviving_axes')
  })

  it('can hide axis pager and evidence panel', () => {
    const wrapper = mountPivotViewer({
      showAxisPager: false,
      showEvidence: false
    })

    expect(wrapper.find('.pivot-axis-pager').exists()).toBe(false)
    expect(wrapper.find('.pivot-evidence-panel').exists()).toBe(false)
  })

  it('renders error state instead of grid and evidence', () => {
    const wrapper = mountPivotViewer({ error: '查询失败' })

    expect(wrapper.find('.pivot-viewer-error').text()).toBe('查询失败')
    expect(wrapper.find('.pivot-grid-stub').exists()).toBe(false)
    expect(wrapper.find('.pivot-evidence-panel').exists()).toBe(false)
  })

  it('fails closed for unsupported shapes', () => {
    const wrapper = mountPivotViewer({
      viewModel: {
        ...soaCandidatePivotFixture,
        shape: 'tree'
      }
    })

    expect(wrapper.find('.pivot-viewer-unsupported').text()).toContain('暂不支持 tree 透视视图')
    expect(wrapper.find('.pivot-grid-stub').exists()).toBe(false)
  })

  it('renders empty slot through PivotGrid', () => {
    const wrapper = mount(PivotViewer, {
      props: {
        viewModel: {
          ...soaCandidatePivotFixture,
          items: []
        },
        emptyText: '无透视结果'
      },
      slots: {
        empty: '<div class="custom-empty">暂无候选行</div>'
      },
      global: {
        stubs: {
          PivotGrid: PivotGridStub
        }
      }
    })

    expect(wrapper.find('.custom-empty').text()).toBe('暂无候选行')
  })
})
