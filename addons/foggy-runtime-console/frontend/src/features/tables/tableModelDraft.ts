export interface InspectedColumn {
  name: string
  jdbcType?: string
  type?: string
  nullable?: boolean
}

export interface InspectedTable {
  dataSource: string
  schema: string
  table: string
  columns: InspectedColumn[]
  primaryKeyColumns: string[]
}

const safeIdentifier = /^[A-Za-z_][A-Za-z0-9_$]*$/

function assertSafeIdentifier(value: string, label: string): string {
  const normalized = value.trim()
  if (!safeIdentifier.test(normalized)) {
    throw new Error(`${label}包含无法安全生成 SQL 的标识符。`)
  }
  return normalized
}

export function buildSelectStatement(schema: string, table: string): string {
  const safeTable = assertSafeIdentifier(table, '表名')
  const safeSchema = schema.trim() ? assertSafeIdentifier(schema, 'Schema') : ''
  return `SELECT *\nFROM ${safeSchema ? `${safeSchema}.` : ''}${safeTable}`
}

export function foggyPropertyType(jdbcType: string): string {
  const normalized = jdbcType.trim().toUpperCase()
  if (normalized.includes('BIGINT')) return 'LONG'
  if (/(^|[^A-Z])(TINYINT|SMALLINT|INTEGER|INT)([^A-Z]|$)/.test(normalized)) return 'INTEGER'
  if (/(DECIMAL|NUMERIC|DOUBLE|FLOAT|REAL|NUMBER|MONEY)/.test(normalized)) return 'NUMBER'
  if (normalized === 'DATE') return 'DATE'
  if (/(TIMESTAMP|DATETIME|TIME)/.test(normalized)) return 'DATETIME'
  if (/(BOOLEAN|BOOL|BIT)/.test(normalized)) return 'BOOL'
  return 'STRING'
}

function modelName(table: string): string {
  const words = table
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
  const base = words.map(word => word[0].toUpperCase() + word.slice(1)).join('') || 'Table'
  return `${/^\d/.test(base) ? `Table${base}` : base}Model`
}

function jsString(value: string): string {
  return `'${value.replaceAll('\\', '\\\\').replaceAll('\'', '\\\'').replaceAll('\n', '\\n')}'`
}

export function buildTableModelDraft(source: InspectedTable): {
  modelName: string
  filename: string
  content: string
} {
  const generatedModelName = modelName(source.table)
  const idColumn = source.primaryKeyColumns.length === 1 ? source.primaryKeyColumns[0] : ''
  const properties = source.columns.map(column => [
    '        {',
    `            column: ${jsString(column.name)},`,
    `            caption: ${jsString(column.name)},`,
    `            type: ${jsString(foggyPropertyType(column.jdbcType || column.type || ''))}`,
    '        }'
  ].join('\n')).join(',\n')
  const schemaNote = source.schema ? ` * Source schema: ${source.schema}\n` : ''
  const datasourceNote = source.dataSource ? ` * Source datasource: ${source.dataSource}\n` : ''
  const idLine = idColumn ? `    idColumn: ${jsString(idColumn)},\n` : ''

  return {
    modelName: generatedModelName,
    filename: `${generatedModelName}.tm`,
    content: [
      '/**',
      ' * AUTO-GENERATED TM DRAFT — REVIEW REQUIRED',
      datasourceNote.trimEnd(),
      schemaNote.trimEnd(),
      ' * This draft is not validated, saved, registered, or refreshed.',
      ' * Add business captions, dimensions, measures, relationships, and descriptions manually.',
      ' */',
      'export const model = {',
      `    name: ${jsString(generatedModelName)},`,
      `    caption: ${jsString(source.table)},`,
      `    tableName: ${jsString(source.table)},`,
      idLine.trimEnd(),
      '    dimensions: [],',
      '    properties: [',
      properties,
      '    ],',
      '    measures: []',
      '};'
    ].filter(line => line !== '').join('\n')
  }
}
