import type { FetchDataParams } from '@/types'
import { cloneSliceTree } from './listPreset'

/**
 * Clone query parameters while retaining host-defined extension fields.
 *
 * FetchDataParams has a stable standard core, but query hooks historically
 * attached fields such as `slots` for backend request extensions. Treating
 * the params object as a closed DTO silently drops those fields when an
 * execution crosses a lifecycle boundary.
 */
export function cloneFetchDataParams<T extends FetchDataParams>(params: T): T {
  const cloned: Record<string, unknown> = {}

  for (const [key, value] of Object.entries(params)) {
    cloned[key] = cloneQueryValue(value)
  }

  cloned.columns = [...params.columns]
  cloned.slice = cloneSliceTree(params.slice)
  cloned.orderBy = params.orderBy.map(order => ({ ...order }))

  return cloned as unknown as T
}

function cloneQueryValue(value: unknown): unknown {
  if (value instanceof Date) return new Date(value.getTime())
  if (Array.isArray(value)) return value.map(cloneQueryValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneQueryValue(item)]))
  }
  return value
}
