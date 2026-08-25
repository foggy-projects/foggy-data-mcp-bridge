import { describe, expect, it } from 'vitest'
import {
  ANALYTICS_THEME_STORAGE_KEY,
  applyAnalyticsTheme,
  normalizeAnalyticsTheme,
  persistAnalyticsTheme,
  readAnalyticsTheme
} from './theme'

describe('analytics theme preference', () => {
  it('uses the simple theme for first visits and unknown stored values', () => {
    expect(normalizeAnalyticsTheme(null)).toBe('simple')
    expect(normalizeAnalyticsTheme('')).toBe('simple')
    expect(normalizeAnalyticsTheme('dark')).toBe('simple')
  })

  it('restores the professional theme from browser storage', () => {
    const storage = { getItem: () => 'professional', setItem: () => undefined }
    expect(readAnalyticsTheme(storage)).toBe('professional')
  })

  it('persists the selected theme under the versioned console key', () => {
    const values = new Map<string, string>()
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => { values.set(key, value) }
    }
    persistAnalyticsTheme('professional', storage)
    expect(values.get(ANALYTICS_THEME_STORAGE_KEY)).toBe('professional')
  })

  it('applies a theme without requiring a reload', () => {
    const root = { dataset: {} as DOMStringMap }
    applyAnalyticsTheme('professional', root)
    expect(root.dataset.theme).toBe('professional')
    applyAnalyticsTheme('simple', root)
    expect(root.dataset.theme).toBe('simple')
  })

  it('falls back safely when storage access is blocked', () => {
    const storage = {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') }
    }
    expect(readAnalyticsTheme(storage)).toBe('simple')
    expect(() => persistAnalyticsTheme('professional', storage)).not.toThrow()
  })
})
