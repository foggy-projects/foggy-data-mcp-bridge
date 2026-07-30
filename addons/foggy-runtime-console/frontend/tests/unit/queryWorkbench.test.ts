import { describe, expect, it } from 'vitest'
import {
  queryRowsToCsv,
  summarizeQueryPayload
} from '@/features/query/queryWorkbench'

describe('query workbench helpers', () => {
  it('summarizes the governed DSL shape', () => {
    expect(summarizeQueryPayload({
      columns: ['customer', 'sum(amount)'],
      slice: [{ field: 'status' }],
      groupBy: [{ field: 'customer' }],
      orderBy: [{ field: 'amount' }],
      page: { start: 20, limit: 10 }
    })).toEqual({
      columns: 2,
      slices: 1,
      groups: 1,
      ordering: 1,
      page: '20 / 10'
    })
  })

  it('exports the union of result columns with CSV escaping', () => {
    expect(queryRowsToCsv([
      { customer: 'Alice, Inc.', amount: 10 },
      { customer: 'Bob "B"', note: 'line 1\nline 2' }
    ])).toBe(
      'customer,amount,note\r\n'
      + '"Alice, Inc.",10,\r\n'
      + '"Bob ""B""",,"line 1\nline 2"'
    )
  })

  it('guards spreadsheet formula injection and supports empty results', () => {
    expect(queryRowsToCsv([{ value: '=HYPERLINK("bad")' }, { value: '  +1' }]))
      .toBe('value\r\n"\'=HYPERLINK(""bad"")"\r\n\'  +1')
    expect(queryRowsToCsv([])).toBe('')
  })
})
