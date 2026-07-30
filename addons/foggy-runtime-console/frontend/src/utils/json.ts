export function parseJsonObject(source: string, label = 'JSON'): Record<string, unknown> {
  let value: unknown
  try {
    value = JSON.parse(source)
  } catch {
    throw new Error(`${label} 格式无效，尚未发送。`)
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} 必须是一个 JSON 对象。`)
  }
  return value as Record<string, unknown>
}

export function normalizeResultRows(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) {
    return value.map((item, index) =>
      item && typeof item === 'object' && !Array.isArray(item)
        ? item as Record<string, unknown>
        : { index, value: item }
    )
  }
  if (!value || typeof value !== 'object') {
    return value === undefined ? [] : [{ value }]
  }

  const record = value as Record<string, unknown>
  for (const key of ['items', 'rows', 'tables', 'columns', 'resources', 'savedResources']) {
    if (Array.isArray(record[key])) {
      return normalizeResultRows(record[key])
    }
  }
  return [record]
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2)
}
