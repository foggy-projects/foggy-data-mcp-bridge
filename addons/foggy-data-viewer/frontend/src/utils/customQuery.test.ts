import { describe, expect, it } from 'vitest'
import type { SliceRequestDef } from '@/types'
import { normalizeUserSlice, prepareCustomQuery, relativeDateOptions, resolveRelativeDate, resolveRelativeDatesForDisplay } from './customQuery'

describe('custom query execution copy', () => {
  it('omits empty leaves/groups while retaining false, zero, null operators and duplicate fields', () => {
    const draft = [
      { field: 'amount', op: '>=', value: 0 },
      { field: 'amount', op: '<=', value: 500 },
      { field: 'active', op: '=', value: false },
      { field: 'missing', op: 'is null' },
      { field: 'other', op: 'is not null' },
      { $or: [{ field: 'empty', op: '=', value: '  ' }, { field: 'empty2', op: 'in', value: [] }] },
      { field: 'codes', op: 'in', value: ['', null, 0, 'OK'] },
      { $and: [{ field: 'empty', op: '=', value: undefined }, { field: 'id', op: '=', value: 1 }] }
    ] as SliceRequestDef[]
    const result = normalizeUserSlice(draft)
    expect(result).toEqual([
      ...draft.slice(0, 5),
      { field: 'codes', op: 'in', value: [0, 'OK'] },
      { $and: [{ field: 'id', op: '=', value: 1 }] }
    ])
    expect(draft[6].value).toEqual(['', null, 0, 'OK'])
    expect(draft[5].$or).toHaveLength(2)
  })

  it('rejects a half-filled range instead of silently losing the bound', () => {
    expect(() => normalizeUserSlice([{ field: 'date', op: '[)', value: ['2026-09-01', ''] }])).toThrow('完整')
    expect(normalizeUserSlice([{ field: 'date', op: '[)', value: ['', null] }])).toEqual([])
  })

  it('counts empty configuration rows before pruning and permits 20 repeated fields', () => {
    const state = { columns: Array.from({ length: 50 }, (_, i) => `f${i}`), orderBy: [],
      slice: Array.from({ length: 20 }, () => ({ field: 'same', op: '=', value: '' })) }
    expect(prepareCustomQuery(state).slice).toEqual([])
    expect(state.slice).toHaveLength(20)
    expect(() => prepareCustomQuery({ ...state, slice: [...state.slice, state.slice[0]] })).toThrow('20')
    expect(() => prepareCustomQuery({ ...state, columns: [...state.columns, 'overflow'] })).toThrow('50')
  })
})

describe('relative dates at execution time', () => {
  const options = { now: new Date('2026-09-09T17:00:00Z'), timeZone: 'Asia/Shanghai' }
  it.each([
    ['today', '2026-09-10', '2026-09-11'],
    ['yesterday', '2026-09-09', '2026-09-10'],
    ['last3days', '2026-09-08', '2026-09-11'],
    ['last7days', '2026-09-04', '2026-09-11'],
    ['thisweek', '2026-09-07', '2026-09-14'],
    ['lastweek', '2026-08-31', '2026-09-07'],
    ['last2weeks', '2026-08-28', '2026-09-11'],
    ['thismonth', '2026-09-01', '2026-10-01'],
    ['lastmonth', '2026-08-01', '2026-09-01'],
    ['last1month', '2026-08-11', '2026-09-11']
  ])('%s produces business-local half-open boundaries', (range, start, end) => {
    expect(resolveRelativeDate({ $relativeDate: range as any }, options)).toEqual([start, end])
  })

  it('re-evaluates saved tokens across midnight without changing the configuration', () => {
    const draft = [{ field: 'createdAt', op: '[)', value: { $relativeDate: 'today', dateTime: true } }]
    expect(normalizeUserSlice(draft, options)[0].value).toEqual(['2026-09-10 00:00:00', '2026-09-11 00:00:00'])
    expect(normalizeUserSlice(draft, { ...options, now: new Date('2026-09-10T17:00:00Z') })[0].value)
      .toEqual(['2026-09-11 00:00:00', '2026-09-12 00:00:00'])
    expect(draft[0].value.$relativeDate).toBe('today')
    expect(relativeDateOptions.map(option => option.value)).not.toContain('tomorrow')
  })

  it('projects relative dates to concrete display ranges without mutating saved tokens', () => {
    const draft = [{ field: 'openingTime', op: '[)', value: { $relativeDate: 'yesterday', dateTime: true } }]

    expect(resolveRelativeDatesForDisplay(draft, {
      now: new Date('2026-09-10T04:00:00Z'),
      timeZone: 'Asia/Shanghai'
    })).toEqual([{
      field: 'openingTime',
      op: '[)',
      value: ['2026-09-09 00:00:00', '2026-09-10 00:00:00']
    }])
    expect(draft[0].value).toEqual({ $relativeDate: 'yesterday', dateTime: true })
  })

  it('handles leap-month clamping and DST without changing calendar dates', () => {
    expect(resolveRelativeDate({ $relativeDate: 'last1month' }, { now: new Date('2024-03-30T12:00:00Z'), timeZone: 'UTC' }))
      .toEqual(['2024-02-29', '2024-03-31'])
    expect(resolveRelativeDate({ $relativeDate: 'today' }, { now: new Date('2026-03-08T18:00:00Z'), timeZone: 'America/New_York' }))
      .toEqual(['2026-03-08', '2026-03-09'])
  })
})
