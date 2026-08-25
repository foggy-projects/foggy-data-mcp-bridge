import { readonly, ref } from 'vue'

export const ANALYTICS_THEME_STORAGE_KEY = 'foggy.analytics-console.theme.v1'

export type AnalyticsTheme = 'simple' | 'professional'

export const DEFAULT_ANALYTICS_THEME: AnalyticsTheme = 'simple'

type ThemeStorage = Pick<Storage, 'getItem' | 'setItem'>
type ThemeRoot = Pick<HTMLElement, 'dataset'>

export const normalizeAnalyticsTheme = (value: string | null | undefined): AnalyticsTheme =>
  value === 'professional' ? 'professional' : DEFAULT_ANALYTICS_THEME

export const readAnalyticsTheme = (storage?: ThemeStorage | null): AnalyticsTheme => {
  if (!storage) return DEFAULT_ANALYTICS_THEME
  try {
    return normalizeAnalyticsTheme(storage.getItem(ANALYTICS_THEME_STORAGE_KEY))
  } catch {
    return DEFAULT_ANALYTICS_THEME
  }
}

export const persistAnalyticsTheme = (
  theme: AnalyticsTheme,
  storage?: ThemeStorage | null
) => {
  if (!storage) return
  try {
    storage.setItem(ANALYTICS_THEME_STORAGE_KEY, theme)
  } catch {
    // The selected theme still applies when browser storage is unavailable.
  }
}

export const applyAnalyticsTheme = (theme: AnalyticsTheme, root?: ThemeRoot | null) => {
  if (root) root.dataset.theme = theme
}

const activeTheme = ref<AnalyticsTheme>(DEFAULT_ANALYTICS_THEME)

export const initializeAnalyticsTheme = () => {
  const theme = readAnalyticsTheme(typeof window === 'undefined' ? null : window.localStorage)
  activeTheme.value = theme
  applyAnalyticsTheme(theme, typeof document === 'undefined' ? null : document.documentElement)
  return theme
}

export const useAnalyticsTheme = () => {
  const selectTheme = (theme: AnalyticsTheme) => {
    activeTheme.value = theme
    applyAnalyticsTheme(theme, typeof document === 'undefined' ? null : document.documentElement)
    persistAnalyticsTheme(theme, typeof window === 'undefined' ? null : window.localStorage)
  }

  return { theme: readonly(activeTheme), selectTheme }
}
