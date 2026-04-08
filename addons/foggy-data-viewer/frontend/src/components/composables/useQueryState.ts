import { ref, computed, type ComputedRef, type Ref } from 'vue'
import type { SliceRequestDef } from '@/types'

/**
 * 查询状态管理 composable
 *
 * 管理 draft（用户输入中的草稿）和 applied（已提交的有效条件）双态。
 * QueryPanel 和 ColumnFilters 共享同一份 draft，显式提交后才进入 applied。
 *
 * 规则：
 * - 服务端查询始终基于 applied
 * - 本地预览过滤可基于 draft（仅列筛选）
 * - 查询区字段修改不触发本地预览
 */

export interface QueryStateField {
  key: string
  sourceField: string
  op: string
  value: unknown
}

export interface UseQueryStateReturn {
  /** 用户输入中的草稿 */
  draft: Ref<Record<string, unknown>>
  /** 已提交的有效条件 */
  applied: Ref<Record<string, unknown>>
  /** 将 draft 提交为 applied */
  commitDraft: () => SliceRequestDef[]
  /** 清空 draft + applied */
  resetAll: () => void
  /** 更新单个字段的 draft 值 */
  patchDraft: (key: string, value: unknown) => void
  /** 获取当前 applied 编译后的 slices */
  appliedSlices: ComputedRef<SliceRequestDef[]>
}

/**
 * 创建查询状态管理实例
 *
 * @param fieldDefs 字段定义，用于 draft→slice 编译
 */
export function useQueryState(
  fieldDefs?: Array<{ key: string; sourceField: string; defaultOperator?: string }>
): UseQueryStateReturn {
  const draft = ref<Record<string, unknown>>({})
  const applied = ref<Record<string, unknown>>({})

  /** 更新单个字段的草稿值 */
  function patchDraft(key: string, value: unknown) {
    if (value === null || value === undefined || value === '') {
      const next = { ...draft.value }
      delete next[key]
      draft.value = next
    } else {
      draft.value = { ...draft.value, [key]: value }
    }
  }

  /** 将草稿提交为生效条件，返回编译后的 slices */
  function commitDraft(): SliceRequestDef[] {
    applied.value = { ...draft.value }
    return compileSlices(applied.value)
  }

  /** 清空全部 */
  function resetAll() {
    draft.value = {}
    applied.value = {}
  }

  /** 编译 applied → SliceRequestDef[] */
  function compileSlices(state: Record<string, unknown>): SliceRequestDef[] {
    const slices: SliceRequestDef[] = []

    for (const [key, rawValue] of Object.entries(state)) {
      if (rawValue === null || rawValue === undefined || rawValue === '') continue

      // 如果值已经是 SliceRequestDef 数组（来自子过滤器组件直接产出的 DSL）
      if (Array.isArray(rawValue) && rawValue.length > 0 && rawValue[0]?.field) {
        slices.push(...(rawValue as SliceRequestDef[]))
        continue
      }

      // 否则按字段定义编译
      const def = fieldDefs?.find(f => f.key === key)
      if (!def) continue

      const field = def.sourceField
      const op = def.defaultOperator || '='

      // 范围值 [min, max]
      if (op === '[]' && Array.isArray(rawValue) && rawValue.length === 2) {
        const [min, max] = rawValue
        if (min != null || max != null) {
          slices.push({ field, op: '[]', value: [min, max] })
        }
        continue
      }

      // 数组值 → in
      if (Array.isArray(rawValue)) {
        if (rawValue.length > 0) {
          slices.push({ field, op: 'in', value: rawValue })
        }
        continue
      }

      // 标量值
      slices.push({ field, op, value: rawValue })
    }

    return slices
  }

  const appliedSlices = computed(() => compileSlices(applied.value))

  return {
    draft,
    applied,
    commitDraft,
    resetAll,
    patchDraft,
    appliedSlices,
  }
}
