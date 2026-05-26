import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PivotEvidencePanel from './PivotEvidencePanel.vue'
import { soaCandidatePivotFixture } from '@/__fixtures__/pivot/soaCandidatePivot'

describe('PivotEvidencePanel', () => {
  it('renders axis scope metadata', () => {
    const wrapper = mount(PivotEvidencePanel, {
      props: {
        rowAxes: soaCandidatePivotFixture.rowAxes,
        columnAxes: soaCandidatePivotFixture.columnAxes
      }
    })

    expect(wrapper.text()).toContain('行轴 运单')
    expect(wrapper.text()).toContain('field=orderId')
    expect(wrapper.text()).toContain('offset=0')
    expect(wrapper.text()).toContain('limit=50')
    expect(wrapper.text()).toContain('orderBy=-noPaidValue')
    expect(wrapper.text()).toContain('domainSlice')
    expect(wrapper.text()).toContain('列轴 科目')
  })

  it('renders calculation evidence as stable JSON text', () => {
    const wrapper = mount(PivotEvidencePanel, {
      props: {
        evidence: soaCandidatePivotFixture.evidence
      }
    })

    expect(wrapper.text()).toContain('cellCalculationScope')
    expect(wrapper.text()).toContain('global_slice_and_surviving_axes')
    expect(wrapper.text()).toContain('globalSlice')
    expect(wrapper.text()).toContain('"field":"rpDir"')
    expect(wrapper.text()).toContain('"value":2')
  })

  it('omits nullish evidence values', () => {
    const wrapper = mount(PivotEvidencePanel, {
      props: {
        evidence: {
          visible: true,
          ignored: null,
          missing: undefined
        }
      }
    })

    expect(wrapper.text()).toContain('visible')
    expect(wrapper.text()).not.toContain('ignored')
    expect(wrapper.text()).not.toContain('missing')
  })
})
