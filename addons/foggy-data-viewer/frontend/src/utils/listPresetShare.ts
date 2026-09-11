import type {
  ColumnViewSetting,
  ListPresetDef,
  ListPresetShareContext,
  ListViewState,
  QueryConditionPreset
} from '@/types'
import { cloneSliceTree, validateListPresetLimits } from './listPreset'

/** 标准化查询方案分享包版本。 */
export const LIST_PRESET_SHARE_SCHEMA_VERSION = 'custom-query-share.v1' as const

/** 分享包中允许跨用户传播的方案内容。 */
export interface ListPresetSharePreset {
  title: string
  description?: string
  columns: string[]
  columnSettings?: ColumnViewSetting[]
  query: QueryConditionPreset
  pageSize?: number
}

/** 可通过复制、文件或 TMS 分享接口传递的标准 JSON 包。 */
export interface ListPresetSharePackage {
  schemaVersion: typeof LIST_PRESET_SHARE_SCHEMA_VERSION
  context: ListPresetShareContext
  preset: ListPresetSharePreset
}

export interface ListPresetShareTargetContext extends ListPresetShareContext {}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function cloneJsonValue<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function cleanContext(context: ListPresetShareContext): ListPresetShareContext {
  const model = context.model?.trim()
  if (!model) throw new Error('分享方案缺少 QM model')
  return {
    model,
    ...(context.menuId?.trim() ? { menuId: context.menuId.trim() } : {}),
    ...(context.url?.trim() ? { url: context.url.trim() } : {})
  }
}

function cleanPreset(preset: ListPresetSharePreset): ListPresetSharePreset {
  const title = preset.title?.trim()
  if (!title) throw new Error('分享方案缺少方案名称')
  if (!Array.isArray(preset.columns)) throw new Error('分享方案的展示字段格式不正确')
  if (!isRecord(preset.query) || !Array.isArray(preset.query.slice) || !Array.isArray(preset.query.orderBy)) {
    throw new Error('分享方案的查询条件格式不正确')
  }

  const normalized: ListPresetSharePreset = {
    title,
    ...(preset.description?.trim() ? { description: preset.description.trim() } : {}),
    columns: [...new Set(preset.columns.filter(field => typeof field === 'string' && field.trim()).map(field => field.trim()))],
    ...(preset.columnSettings ? { columnSettings: cloneJsonValue(preset.columnSettings) } : {}),
    query: {
      slice: cloneSliceTree(preset.query.slice),
      orderBy: cloneJsonValue(preset.query.orderBy)
    },
    ...(preset.pageSize !== undefined ? { pageSize: preset.pageSize } : {})
  }

  validateListPresetLimits({
    columns: normalized.columns,
    columnSettings: normalized.columnSettings,
    slice: normalized.query.slice
  })
  return normalized
}

/** 从引擎返回的方案构建可分享内容，自动排除 owner、权限、ID、时间戳等字段。 */
export function buildListPresetSharePackage(
  preset: ListPresetDef,
  context: ListPresetShareContext
): ListPresetSharePackage {
  const normalizedContext = cleanContext({ ...context, model: context.model || preset.model })
  if (preset.model && preset.model !== normalizedContext.model) {
    throw new Error('方案 QM model 与当前页面不一致，无法分享')
  }

  return {
    schemaVersion: LIST_PRESET_SHARE_SCHEMA_VERSION,
    context: normalizedContext,
    preset: cleanPreset({
      title: preset.title,
      description: preset.description,
      columns: preset.columns,
      columnSettings: preset.columnSettings,
      query: preset.query,
      pageSize: preset.pageSize
    })
  }
}

/** 序列化为可直接复制或通过文件/接口传递的 JSON。 */
export function serializeListPresetSharePackage(
  preset: ListPresetDef,
  context: ListPresetShareContext
): string {
  return JSON.stringify(buildListPresetSharePackage(preset, context), null, 2)
}

/** 校验并解析分享 JSON；不接受带有用户归属或权限字段的非标准结构。 */
export function parseListPresetSharePackage(payload: unknown): ListPresetSharePackage {
  let value = payload
  if (typeof payload === 'string') {
    try {
      value = JSON.parse(payload)
    } catch {
      throw new Error('分享 JSON 格式错误，请粘贴完整的标准分享内容')
    }
  }

  if (!isRecord(value) || value.schemaVersion !== LIST_PRESET_SHARE_SCHEMA_VERSION) {
    throw new Error(`不支持的分享包版本，请使用 ${LIST_PRESET_SHARE_SCHEMA_VERSION}`)
  }
  const context = value.context
  const preset = value.preset
  if (!isRecord(context) || typeof context.model !== 'string') {
    throw new Error('分享包缺少 QM model 上下文')
  }
  if (!isRecord(preset)) throw new Error('分享包缺少查询方案内容')

  return {
    schemaVersion: LIST_PRESET_SHARE_SCHEMA_VERSION,
    context: cleanContext({
      model: context.model,
      menuId: typeof context.menuId === 'string' ? context.menuId : undefined,
      url: typeof context.url === 'string' ? context.url : undefined
    }),
    preset: cleanPreset({
      title: typeof preset.title === 'string' ? preset.title : '',
      description: typeof preset.description === 'string' ? preset.description : undefined,
      columns: Array.isArray(preset.columns) ? preset.columns.filter((field): field is string => typeof field === 'string') : [],
      columnSettings: Array.isArray(preset.columnSettings) ? cloneJsonValue(preset.columnSettings) : undefined,
      query: isRecord(preset.query)
        ? {
            slice: Array.isArray(preset.query.slice) ? preset.query.slice : [],
            orderBy: Array.isArray(preset.query.orderBy) ? preset.query.orderBy : []
          }
        : { slice: [], orderBy: [] },
      pageSize: typeof preset.pageSize === 'number' ? preset.pageSize : undefined
    })
  }
}

/**
 * 校验分享包是否能导入当前页面。
 * QM model 始终必须一致；TMS 传入 menuId/url 时，再对已携带的对应上下文做精确校验。
 */
export function assertListPresetShareCompatible(
  sharePackage: ListPresetSharePackage,
  target: ListPresetShareTargetContext
): void {
  const source = cleanContext(sharePackage.context)
  const current = cleanContext(target)
  if (source.model !== current.model) {
    throw new Error(`分享方案来自 QM「${source.model}」，当前页面是「${current.model}」，不允许导入`)
  }
  if (source.menuId && current.menuId && source.menuId !== current.menuId) {
    throw new Error('分享方案来自其他菜单，已阻止导入以避免跨页面误用')
  }
  if (source.url && current.url && source.url !== current.url) {
    throw new Error('分享方案来自其他页面 URL，已阻止导入以避免跨页面误用')
  }
}

/** 将分享包内容转换为当前用户重新保存时使用的视图状态。 */
export function listPresetShareToViewState(sharePackage: ListPresetSharePackage): ListViewState {
  return {
    columns: [...sharePackage.preset.columns],
    columnSettings: sharePackage.preset.columnSettings ? cloneJsonValue(sharePackage.preset.columnSettings) : undefined,
    slice: cloneSliceTree(sharePackage.preset.query.slice),
    orderBy: cloneJsonValue(sharePackage.preset.query.orderBy),
    pageSize: sharePackage.preset.pageSize
  }
}
