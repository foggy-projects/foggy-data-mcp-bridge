import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { PropType } from 'vue'
import PivotGrid from './PivotGrid.vue'
import { soaCandidatePivotFixture } from '@/__fixtures__/pivot/soaCandidatePivot'

interface TestGridColumn {
  field?: string
  title?: string
  fixed?: 'left' | 'right'
  align?: string
  children?: TestGridColumn[]
  meta?: {
    role?: string
    key?: string
    metricField?: string
  }
  slots?: {
    default?: (params: { row: Record<string, unknown>; column: TestGridColumn; cellValue: unknown }) => unknown
  }
}

const VxeGridRenderStub = defineComponent({
  name: 'vxe-grid',
  props: {
    columns: {
      type: Array as PropType<TestGridColumn[]>,
      default: () => []
    },
    data: {
      type: Array as PropType<Record<string, unknown>[]>,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  setup(props, { slots }) {
    const flatten = (columns: TestGridColumn[]): TestGridColumn[] => {
      return columns.flatMap(column => column.children?.length ? flatten(column.children) : [column])
    }

    return () => h('div', {
      class: 'vxe-grid-render-stub',
      'data-loading': String(props.loading),
      'data-column-count': String(props.columns.length)
    }, [
      h('div', { class: 'stub-top-columns' }, props.columns.map(column =>
        h('span', {
          class: 'stub-top-column',
          'data-title': column.title,
          'data-role': column.meta?.role,
          'data-fixed': column.fixed ?? ''
        }, column.title)
      )),
      h('div', { class: 'stub-leaf-columns' }, flatten(props.columns).map(column =>
        h('span', {
          class: 'stub-leaf-column',
          'data-field': column.field,
          'data-role': column.meta?.role,
          'data-fixed': column.fixed ?? '',
          'data-align': column.align ?? ''
        }, column.title)
      )),
      props.data.length === 0
        ? h('div', { class: 'stub-empty' }, slots.empty?.())
        : h('div', { class: 'stub-rows' }, props.data.map((row, rowIndex) =>
          h('div', { class: 'stub-row', 'data-row-index': String(rowIndex) }, flatten(props.columns)
            .filter(column => column.field)
            .map(column => h('div', {
              class: ['stub-cell', `stub-cell-${String(column.field)}`],
              'data-role': column.meta?.role
            }, column.slots?.default
              ? column.slots.default({ row, column, cellValue: row[column.field as string] })
              : String(row[column.field as string] ?? '')
            ))
          )
        ))
    ])
  }
})

function mountPivotGrid(extraProps: Record<string, unknown> = {}) {
  return mount(PivotGrid, {
    props: {
      viewModel: soaCandidatePivotFixture,
      ...extraProps
    },
    global: {
      stubs: {
        'vxe-grid': VxeGridRenderStub
      }
    }
  })
}

describe('PivotGrid', () => {
  it('passes grouped columns to vxe-grid', () => {
    const wrapper = mountPivotGrid()

    const topColumns = wrapper.findAll('.stub-top-column')
    expect(topColumns.map(column => column.attributes('data-title'))).toEqual([
      '开单组织',
      '运单',
      '运输费',
      '服务费'
    ])
    expect(topColumns[2].attributes('data-role')).toBe('columnAxisMember')
  })

  it('renders row axis leaf cells from flat items', () => {
    const wrapper = mountPivotGrid()

    expect(wrapper.find('.stub-cell-openingOrg').text()).toBe('华东')
    expect(wrapper.find('.stub-cell-orderId').text()).toBe('EO-001')
  })

  it('renders metric children under grouped column axis members', () => {
    const wrapper = mountPivotGrid()

    const metricLeaves = wrapper.findAll('.stub-leaf-column')
      .filter(column => column.attributes('data-role') === 'metric')

    expect(metricLeaves.map(column => column.attributes('data-field'))).toEqual([
      'subject_freight__noPaidValue',
      'subject_freight__paidValue',
      'subject_service__noPaidValue',
      'subject_service__paidValue'
    ])
    expect(metricLeaves.every(column => column.attributes('data-align') === 'right')).toBe(true)
  })

  it('fixes row axis columns on the left', () => {
    const wrapper = mountPivotGrid()

    const rowAxisLeaves = wrapper.findAll('.stub-leaf-column')
      .filter(column => column.attributes('data-role') === 'rowAxis')

    expect(rowAxisLeaves.map(column => column.attributes('data-fixed'))).toEqual(['left', 'left'])
  })

  it('formats money metric cells and renders zero values', () => {
    const wrapper = mountPivotGrid()

    expect(wrapper.find('.stub-cell-subject_freight__noPaidValue').text()).toBe('1,200.00')
    expect(wrapper.find('.stub-cell-subject_service__noPaidValue').text()).toBe('0.00')
  })

  it('renders configured empty cell text for missing metric values', () => {
    const viewModel = {
      ...soaCandidatePivotFixture,
      items: [
        {
          openingOrg: '华北',
          orderId: 'EO-003'
        }
      ]
    }

    const wrapper = mountPivotGrid({
      viewModel,
      emptyCellText: '-'
    })

    expect(wrapper.find('.stub-cell-subject_freight__noPaidValue').text()).toBe('-')
  })

  it('passes loading state through to vxe-grid', () => {
    const wrapper = mountPivotGrid({ loading: true })

    expect(wrapper.find('.vxe-grid-render-stub').attributes('data-loading')).toBe('true')
  })

  it('renders empty slot text when there are no items', () => {
    const wrapper = mountPivotGrid({
      viewModel: {
        ...soaCandidatePivotFixture,
        items: []
      },
      emptyText: '无透视结果'
    })

    expect(wrapper.find('.stub-empty').text()).toContain('无透视结果')
  })
})
