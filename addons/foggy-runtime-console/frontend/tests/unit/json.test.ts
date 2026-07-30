import { describe, expect, it } from 'vitest'
import { normalizeResultRows, parseJsonObject } from '@/utils/json'

describe('JSON workbench helpers', () => {
  it('accepts JSON objects and rejects malformed or non-object payloads', () => {
    expect(parseJsonObject('{"columns":["id"]}')).toEqual({ columns: ['id'] })
    expect(() => parseJsonObject('{')).toThrow('格式无效')
    expect(() => parseJsonObject('[]')).toThrow('JSON 对象')
  })

  it('normalizes common Runtime result shapes', () => {
    expect(normalizeResultRows({ items: [{ id: 1 }] })).toEqual([{ id: 1 }])
    expect(normalizeResultRows({ rows: [{ id: 2 }] })).toEqual([{ id: 2 }])
    expect(normalizeResultRows(['value'])).toEqual([{ index: 0, value: 'value' }])
  })
})
