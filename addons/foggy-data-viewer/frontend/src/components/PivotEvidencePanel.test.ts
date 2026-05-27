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

  it('renders parentShare and baselineRatio evidence as readable derived metric rows', () => {
    const wrapper = mount(PivotEvidencePanel, {
      props: {
        evidence: {
          parentShareEvidence: [
            {
              metric: 'paymentShare',
              of: 'salesAmount',
              axis: 'rows',
              level: 'paymentMethod',
              parentLevel: 'orderStatus',
              denominatorScope: 'prePageParent',
              prePageRows: 6,
              parentGroups: 2,
              source: 'preTopNParentAggIndex'
            }
          ],
          baselineRatioEvidence: [
            {
              metric: 'salesIndex',
              of: 'salesAmount',
              axis: 'columns',
              baseline: 'first',
              baselineScope: 'prePageAxisDomain',
              columnField: 'salesDate$dayOfWeek',
              baselineColumnKey: 1,
              baselineColumnVisible: false,
              prePageAxisDomainSize: 4,
              visibleAxisDomainSize: 2,
              baselineRows: 1,
              source: 'auxiliaryBaselineRelation'
            }
          ]
        }
      }
    })

    expect(wrapper.text()).toContain('派生指标证据')
    expect(wrapper.text()).toContain('parentShare paymentShare')
    expect(wrapper.text()).toContain('scope=prePageParent')
    expect(wrapper.text()).toContain('parentGroups=2')
    expect(wrapper.text()).toContain('source=preTopNParentAggIndex')
    expect(wrapper.text()).toContain('baselineRatio salesIndex')
    expect(wrapper.text()).toContain('scope=prePageAxisDomain')
    expect(wrapper.text()).toContain('baselineColumnVisible=false')
    expect(wrapper.text()).toContain('source=auxiliaryBaselineRelation')
    expect(wrapper.text()).not.toContain('parentShareEvidence')
    expect(wrapper.text()).not.toContain('baselineRatioEvidence')
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
