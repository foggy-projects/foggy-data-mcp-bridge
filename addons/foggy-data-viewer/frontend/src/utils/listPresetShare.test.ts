import { describe, expect, it } from 'vitest'
import type { ListPresetDef } from '@/types'
import {
  assertListPresetShareCompatible,
  buildListPresetSharePackage,
  listPresetShareToViewState,
  parseListPresetSharePackage,
  serializeListPresetSharePackage
} from './listPresetShare'

const preset: ListPresetDef = {
  id: 'private-preset-001',
  model: 'FactSalesQueryModel',
  businessKey: 'sales-list',
  title: '昨日销售',
  description: '个人方案说明',
  columns: ['orderNo', 'amount'],
  columnSettings: [{ name: 'orderNo', visible: true, order: 0, width: 160 }],
  query: {
    slice: [{ field: 'orderDate', op: '=', value: { $relativeDate: 'YESTERDAY' } }],
    orderBy: [{ field: 'orderNo', order: 'desc' }]
  },
  pageSize: 30,
  visibility: 'PRIVATE',
  ownerId: 'user-a',
  isDefault: true,
  version: 1,
  createdAt: '2026-09-11T00:00:00.000Z',
  updatedAt: '2026-09-11T00:00:00.000Z'
}

describe('list preset share package', () => {
  it('exports only portable query content and round-trips JSON', () => {
    const json = serializeListPresetSharePackage(preset, {
      model: preset.model,
      menuId: 'tms.sales.list'
    })
    expect(json).toContain('custom-query-share.v1')
    expect(json).not.toContain('private-preset-001')
    expect(json).not.toContain('user-a')
    expect(json).not.toContain('PRIVATE')
    expect(parseListPresetSharePackage(json)).toMatchObject({
      context: { model: preset.model, menuId: 'tms.sales.list' },
      preset: { title: preset.title, columns: preset.columns }
    })
  })

  it('requires the same QM and optionally checks page context', () => {
    const sharePackage = buildListPresetSharePackage(preset, {
      model: preset.model,
      menuId: 'tms.sales.list'
    })
    expect(() => assertListPresetShareCompatible(sharePackage, { model: 'OtherQueryModel' }))
      .toThrow('QM')
    expect(() => assertListPresetShareCompatible(sharePackage, {
      model: preset.model,
      menuId: 'tms.shipment.list'
    })).toThrow('其他菜单')
    expect(() => assertListPresetShareCompatible(sharePackage, { model: preset.model })).not.toThrow()
  })

  it('converts imported content into a new user-owned draft state', () => {
    const sharePackage = parseListPresetSharePackage(JSON.stringify({
      schemaVersion: 'custom-query-share.v1',
      context: { model: preset.model },
      preset: {
        ...preset,
        ownerId: 'should-be-ignored',
        id: 'should-be-ignored'
      }
    }))
    const state = listPresetShareToViewState(sharePackage)
    expect(state).toMatchObject({
      columns: ['orderNo', 'amount'],
      pageSize: 30,
      orderBy: [{ field: 'orderNo', order: 'desc' }]
    })
    expect(state.slice[0]?.value).toEqual({ $relativeDate: 'YESTERDAY' })
  })
})
