import { prettyJson } from '@/utils/json'

type UnknownRecord = Record<string, unknown>

export type ModelFieldKind = 'dimension' | 'measure' | 'calculated'

export interface StructuredModelField {
  id: string
  name: string
  caption: string
  kind: ModelFieldKind
  type: string
  aggregation: string
  sourceColumn: string
  filterable?: boolean
  description: string
  usage: string
}

export interface StructuredPhysicalTable {
  table: string
  role: string
}

export interface StructuredModelDetail {
  modelInfo: UnknownRecord
  source: UnknownRecord
  fields: StructuredModelField[]
  physicalTables: StructuredPhysicalTable[]
  examples: unknown[]
  modelErrors: unknown[]
  version: string
  prompt: string
  rawText: string
  hasStructuredContent: boolean
}

function isRecord(value: unknown): value is UnknownRecord {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function stringValue(value: unknown): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

function parseContent(value: unknown): UnknownRecord | null {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    const parsed: unknown = JSON.parse(value)
    return isRecord(parsed) ? parsed : null
  } catch {
    return null
  }
}

function modelFieldDescription(field: UnknownRecord, modelName: string): {
  description: string
  usage: string
} {
  const models = isRecord(field.models) ? field.models : {}
  const modelMetadata = isRecord(models[modelName]) ? models[modelName] : {}
  return {
    description: stringValue(modelMetadata.description)
      || stringValue(field.description)
      || stringValue(field.meta),
    usage: stringValue(modelMetadata.usage) || stringValue(field.usage)
  }
}

function normalizeField(field: UnknownRecord, key: string, modelName: string): StructuredModelField {
  const semantic = modelFieldDescription(field, modelName)
  const name = stringValue(field.fieldName) || key
  const calculated = field.calculated === true || field.predefined === true
  return {
    id: name || key,
    name: name || key,
    caption: stringValue(field.name) || name || key,
    kind: calculated ? 'calculated' : field.measure === true ? 'measure' : 'dimension',
    type: stringValue(field.type) || 'UNKNOWN',
    aggregation: stringValue(field.aggregation),
    sourceColumn: stringValue(field.sourceColumn),
    filterable: typeof field.filterable === 'boolean' ? field.filterable : undefined,
    description: semantic.description,
    usage: semantic.usage
  }
}

function normalizeFields(value: unknown, modelName: string): StructuredModelField[] {
  if (Array.isArray(value)) {
    return value
      .filter(isRecord)
      .map((field, index) => normalizeField(
        field,
        stringValue(field.fieldName) || stringValue(field.name) || `field-${index + 1}`,
        modelName
      ))
  }
  if (!isRecord(value)) return []
  return Object.entries(value)
    .filter((entry): entry is [string, UnknownRecord] => isRecord(entry[1]))
    .map(([key, field]) => normalizeField(field, key, modelName))
}

function normalizePhysicalTables(value: unknown): StructuredPhysicalTable[] {
  if (!Array.isArray(value)) return []
  return value.flatMap(item => {
    if (typeof item === 'string') return [{ table: item, role: '' }]
    if (!isRecord(item)) return []
    const table = stringValue(item.table) || stringValue(item.name)
    return table ? [{ table, role: stringValue(item.role) }] : []
  })
}

export function normalizeModelDetail(
  response: UnknownRecord,
  modelName: string
): StructuredModelDetail {
  const responseData = isRecord(response.data) ? response.data : null
  const parsedContent = parseContent(response.content)
  const payload = responseData || parsedContent || {}
  const models = isRecord(payload.models) ? payload.models : {}
  const modelInfo = isRecord(models[modelName])
    ? models[modelName]
    : isRecord(payload.model) ? payload.model : {}
  const fields = normalizeFields(payload.fields, modelName)
  const physicalTables = normalizePhysicalTables(payload.physicalTables)
  const examples = Array.isArray(payload.examples)
    ? payload.examples
    : Array.isArray(modelInfo.examples) ? modelInfo.examples : []
  const modelErrors = Array.isArray(payload.modelErrors) ? payload.modelErrors : []
  const source = isRecord(payload.modelSource) ? payload.modelSource : {}
  const rawText = responseData
    ? prettyJson(responseData)
    : typeof response.content === 'string' && response.content.trim()
      ? response.content
      : prettyJson(response)

  return {
    modelInfo,
    source,
    fields,
    physicalTables,
    examples,
    modelErrors,
    version: stringValue(payload.version),
    prompt: stringValue(payload.prompt),
    rawText,
    hasStructuredContent: Boolean(
      Object.keys(modelInfo).length
      || fields.length
      || physicalTables.length
      || examples.length
      || modelErrors.length
      || Object.keys(source).length
    )
  }
}
