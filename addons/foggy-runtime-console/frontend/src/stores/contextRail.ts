import { computed, reactive } from 'vue'

export interface ContextRailItem {
  id: string
  label: string
  meta?: string
  badge?: string
  active?: boolean
  action?: () => void
}

export interface ContextRailSection {
  id: string
  label?: string
  items: ContextRailItem[]
}

export interface ContextRailState {
  route: string
  eyebrow: string
  title: string
  description: string
  loading: boolean
  filterable: boolean
  emptyText: string
  sections: ContextRailSection[]
}

const state = reactive<ContextRailState>({
  route: '',
  eyebrow: 'Workspace',
  title: '运行上下文',
  description: '当前工作台的资源与快捷入口。',
  loading: false,
  filterable: false,
  emptyText: '暂无可用资源。',
  sections: []
})

const query = reactive({ value: '' })

const filteredSections = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return state.sections
  return state.sections
    .map(section => ({
      ...section,
      items: section.items.filter(item =>
        [item.label, item.meta, item.badge].some(value => value?.toLowerCase().includes(keyword))
      )
    }))
    .filter(section => section.items.length)
})

export function useContextRail() {
  function setContext(next: Partial<ContextRailState> & Pick<ContextRailState, 'route'>): void {
    if (state.route !== next.route) query.value = ''
    Object.assign(state, {
      eyebrow: 'Workspace',
      title: '运行上下文',
      description: '当前工作台的资源与快捷入口。',
      loading: false,
      filterable: false,
      emptyText: '暂无可用资源。',
      sections: [],
      ...next
    })
  }

  function clearContext(route: string): void {
    if (state.route === route) {
      state.route = ''
      state.sections = []
      query.value = ''
    }
  }

  return {
    state,
    query,
    filteredSections,
    setContext,
    clearContext
  }
}
