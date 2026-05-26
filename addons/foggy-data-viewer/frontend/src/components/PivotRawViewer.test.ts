import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { PropType } from 'vue'
import PivotRawViewer from './PivotRawViewer.vue'
import { soaCandidatePivotRawFixture } from '@/__fixtures__/pivot/soaCandidatePivotRaw'
import type { PivotRawPayload, PivotViewModel } from '@/types'

const PivotViewerStub = defineComponent({
  name: 'PivotViewer',
  props: {
    viewModel: {
      type: Object as PropType<PivotViewModel>,
      required: true
    },
    loading: Boolean,
    error: String,
    height: [String, Number],
    emptyText: String,
    emptyCellText: String,
    showAxisPager: Boolean,
    showEvidence: Boolean
  },
  setup(props, { slots }) {
    return () => h('div', {
      class: 'pivot-viewer-stub',
      'data-loading': String(props.loading),
      'data-error': props.error ?? '',
      'data-height': String(props.height),
      'data-empty-cell-text': props.emptyCellText ?? '',
      'data-show-axis-pager': String(props.showAxisPager),
      'data-show-evidence': String(props.showEvidence),
      'data-item-count': String(props.viewModel.items.length),
      'data-header-count': String(props.viewModel.headerTree.length)
    }, props.viewModel.items.length ? 'pivot-viewer' : slots.empty?.())
  }
})

function mountPivotRawViewer(extraProps: Record<string, unknown> = {}) {
  return mount(PivotRawViewer, {
    props: {
      rawPayload: soaCandidatePivotRawFixture,
      ...extraProps
    },
    global: {
      stubs: {
        PivotViewer: PivotViewerStub
      }
    }
  })
}

describe('PivotRawViewer', () => {
  it('adapts raw payload and renders PivotViewer', () => {
    const wrapper = mountPivotRawViewer()
    const viewer = wrapper.find('.pivot-viewer-stub')

    expect(viewer.exists()).toBe(true)
    expect(viewer.attributes('data-item-count')).toBe('2')
    expect(viewer.attributes('data-header-count')).toBe('4')
  })

  it('passes viewer options through', () => {
    const wrapper = mountPivotRawViewer({
      loading: true,
      height: 360,
      emptyCellText: 'N/A',
      showAxisPager: false,
      showEvidence: false
    })
    const viewer = wrapper.find('.pivot-viewer-stub')

    expect(viewer.attributes('data-loading')).toBe('true')
    expect(viewer.attributes('data-height')).toBe('360')
    expect(viewer.attributes('data-empty-cell-text')).toBe('N/A')
    expect(viewer.attributes('data-show-axis-pager')).toBe('false')
    expect(viewer.attributes('data-show-evidence')).toBe('false')
  })

  it('passes external errors through to PivotViewer after successful adaptation', () => {
    const wrapper = mountPivotRawViewer({ error: '查询失败' })

    expect(wrapper.find('.pivot-viewer-stub').attributes('data-error')).toBe('查询失败')
  })

  it('fails closed when raw payload cannot be adapted', () => {
    const rawPayload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      shape: 'tree'
    }

    const wrapper = mountPivotRawViewer({ rawPayload })

    expect(wrapper.find('.pivot-viewer-stub').exists()).toBe(false)
    expect(wrapper.find('.pivot-raw-viewer-error').text()).toContain('Unsupported pivot shape "tree"')
  })

  it('renders the empty slot through PivotViewer', () => {
    const rawPayload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      rowMembers: [],
      cells: []
    }

    const wrapper = mount(PivotRawViewer, {
      props: { rawPayload },
      slots: {
        empty: '<div class="custom-empty">暂无候选行</div>'
      },
      global: {
        stubs: {
          PivotViewer: PivotViewerStub
        }
      }
    })

    expect(wrapper.find('.custom-empty').text()).toBe('暂无候选行')
  })
})
