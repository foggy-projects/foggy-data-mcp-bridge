import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PivotAxisPager from './PivotAxisPager.vue'
import { soaCandidatePivotFixture } from '@/__fixtures__/pivot/soaCandidatePivot'

describe('PivotAxisPager', () => {
  it('renders row and column axis page metadata', () => {
    const wrapper = mount(PivotAxisPager, {
      props: {
        rowAxes: soaCandidatePivotFixture.rowAxes,
        columnAxes: soaCandidatePivotFixture.columnAxes,
        rowPages: soaCandidatePivotFixture.axisPages?.rows,
        columnPages: soaCandidatePivotFixture.axisPages?.columns
      }
    })

    const pages = wrapper.findAll('.pivot-axis-page')

    expect(pages).toHaveLength(2)
    expect(pages[0].attributes('data-axis')).toBe('rows')
    expect(pages[0].attributes('data-field')).toBe('orderId')
    expect(pages[0].text()).toContain('运单')
    expect(pages[0].text()).toContain('0-50 / 2')
    expect(pages[1].attributes('data-axis')).toBe('columns')
    expect(pages[1].attributes('data-field')).toBe('subjectCode')
    expect(pages[1].text()).toContain('科目')
  })

  it('renders nothing when no axis pages exist', () => {
    const wrapper = mount(PivotAxisPager)

    expect(wrapper.find('.pivot-axis-pager').exists()).toBe(false)
  })

  it('marks per-parent pages and has-more state', () => {
    const wrapper = mount(PivotAxisPager, {
      props: {
        rowAxes: [{ field: 'orderId', title: '运单', role: 'rowAxis' }],
        rowPages: [{
          field: 'orderId',
          offset: 20,
          limit: 10,
          total: 80,
          hasMore: true,
          pageScope: 'perParent'
        }]
      }
    })

    const page = wrapper.find('.pivot-axis-page')

    expect(page.text()).toContain('20-30 / 80')
    expect(page.text()).toContain('父级内分页')
    expect(page.text()).toContain('还有更多')
  })
})
