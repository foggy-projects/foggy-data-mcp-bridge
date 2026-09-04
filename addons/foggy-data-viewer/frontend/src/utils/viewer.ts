import type {
  ColumnSchema,
  MoneyViewerConfig,
  SliceRequestDef,
  ViewerConfig
} from '@/types'

export const MONEY_VIEWER = Object.freeze({
  format: 'money',
  rawUnit: 'minor',
  displayUnit: 'CNY',
  scaleFactor: 100,
  precision: 2
} as const satisfies MoneyViewerConfig)

interface DecimalValue {
  coefficient: bigint
  scale: number
}

const POWERS_OF_TEN: bigint[] = [1n]

function powerOfTen(exponent: number): bigint {
  while (POWERS_OF_TEN.length <= exponent) {
    POWERS_OF_TEN.push(POWERS_OF_TEN[POWERS_OF_TEN.length - 1] * 10n)
  }
  return POWERS_OF_TEN[exponent]
}

function parseDecimal(value: unknown): DecimalValue | null {
  if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'bigint') {
    return null
  }
  if (typeof value === 'number' && !Number.isFinite(value)) {
    return null
  }

  const text = String(value).replace(/,/g, '').trim()
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(text)
  if (!match) return null

  const sign = match[1] === '-' ? -1n : 1n
  const fraction = match[3] || ''
  const exponent = Number(match[4] || 0)
  if (!Number.isSafeInteger(exponent)) return null

  let coefficient = BigInt(`${match[2]}${fraction}` || '0') * sign
  let scale = fraction.length - exponent
  if (scale < 0) {
    coefficient *= powerOfTen(-scale)
    scale = 0
  }

  while (scale > 0 && coefficient % 10n === 0n) {
    coefficient /= 10n
    scale--
  }
  return { coefficient, scale }
}

function decimalToCanonical(value: DecimalValue): string {
  const negative = value.coefficient < 0n
  const digits = (negative ? -value.coefficient : value.coefficient).toString()
  if (value.scale === 0) return `${negative ? '-' : ''}${digits}`

  const padded = digits.padStart(value.scale + 1, '0')
  const splitAt = padded.length - value.scale
  const fraction = padded.slice(splitAt).replace(/0+$/, '')
  const integer = padded.slice(0, splitAt)
  return `${negative ? '-' : ''}${integer}${fraction ? `.${fraction}` : ''}`
}

function normalizeDecimal(value: DecimalValue): DecimalValue {
  let { coefficient, scale } = value
  while (scale > 0 && coefficient % 10n === 0n) {
    coefficient /= 10n
    scale--
  }
  return { coefficient, scale }
}

function toTransportNumberOrString(value: DecimalValue): number | string {
  const normalized = normalizeDecimal(value)
  const canonical = decimalToCanonical(normalized)
  if (normalized.scale === 0) {
    const integer = Number(canonical)
    return Number.isSafeInteger(integer) ? integer : canonical
  }
  return canonical
}

function divideToFixed(raw: DecimalValue, factor: DecimalValue, precision: number): string {
  const negative = (raw.coefficient < 0n) !== (factor.coefficient < 0n)
  const rawCoefficient = raw.coefficient < 0n ? -raw.coefficient : raw.coefficient
  const factorCoefficient = factor.coefficient < 0n ? -factor.coefficient : factor.coefficient
  const numerator = rawCoefficient * powerOfTen(factor.scale + precision)
  const denominator = factorCoefficient * powerOfTen(raw.scale)
  let quotient = numerator / denominator
  const remainder = numerator % denominator

  // Round half away from zero without converting through IEEE-754 floating point.
  if (remainder * 2n >= denominator) {
    quotient += 1n
  }

  const digits = quotient.toString().padStart(precision + 1, '0')
  const integer = precision === 0 ? digits : digits.slice(0, -precision)
  const fraction = precision === 0 ? '' : digits.slice(-precision)
  const sign = negative && quotient !== 0n ? '-' : ''
  return `${sign}${integer}${fraction ? `.${fraction}` : ''}`
}

function groupFixedDecimal(value: string): string {
  const negative = value.startsWith('-')
  const unsigned = negative ? value.slice(1) : value
  const [integer, fraction] = unsigned.split('.')
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${negative ? '-' : ''}${grouped}${fraction == null ? '' : `.${fraction}`}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

export function resolveMoneyViewer(viewer: ViewerConfig | unknown): MoneyViewerConfig | null {
  if (!isRecord(viewer) || viewer.format !== 'money') return null
  if (viewer.rawUnit !== 'minor' || viewer.displayUnit !== 'CNY') return null

  const scaleFactor = typeof viewer.scaleFactor === 'number'
    ? viewer.scaleFactor
    : Number(viewer.scaleFactor)
  if (!Number.isFinite(scaleFactor) || scaleFactor <= 0) {
    throw new RangeError('money viewer scaleFactor must be a finite number greater than 0')
  }

  const precision = viewer.precision == null ? 2 : Number(viewer.precision)
  if (!Number.isInteger(precision) || precision < 0 || precision > 20) {
    throw new RangeError('money viewer precision must be an integer between 0 and 20')
  }

  return {
    format: 'money',
    rawUnit: 'minor',
    displayUnit: 'CNY',
    scaleFactor,
    precision
  }
}

export function getColumnMoneyViewer(column?: Pick<ColumnSchema, 'extData'> | null): MoneyViewerConfig | null {
  return resolveMoneyViewer(column?.extData?.viewer)
}

export function formatViewerValue(value: unknown, column?: Pick<ColumnSchema, 'extData'> | null): string | null {
  const viewer = getColumnMoneyViewer(column)
  if (!viewer) return null
  if (value == null || value === '') return ''

  const raw = parseDecimal(value)
  const factor = parseDecimal(viewer.scaleFactor)
  if (!raw || !factor) return String(value)
  return groupFixedDecimal(divideToFixed(raw, factor, viewer.precision ?? 2))
}

function moneyRawToDisplayFilterValue(value: unknown, viewer: MoneyViewerConfig): unknown {
  if (value == null || value === '') return value
  const raw = parseDecimal(value)
  const factor = parseDecimal(viewer.scaleFactor)
  if (!raw || !factor) return value

  const fixed = divideToFixed(raw, factor, viewer.precision ?? 2)
  const canonical = fixed.replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
  const numberValue = Number(canonical)
  return Number.isFinite(numberValue) ? numberValue : canonical
}

function moneyDisplayToRawFilterValue(value: unknown, viewer: MoneyViewerConfig): unknown {
  if (value == null || value === '') return value
  const display = parseDecimal(value)
  const factor = parseDecimal(viewer.scaleFactor)
  if (!display || !factor) return value

  return toTransportNumberOrString({
    coefficient: display.coefficient * factor.coefficient,
    scale: display.scale + factor.scale
  })
}

function transformSliceValue(value: unknown, transform: (item: unknown) => unknown): unknown {
  return Array.isArray(value) ? value.map(item => transformSliceValue(item, transform)) : transform(value)
}

function transformSlice(
  slice: SliceRequestDef,
  columns: readonly ColumnSchema[],
  direction: 'raw-to-display' | 'display-to-raw'
): SliceRequestDef {
  const column = columns.find(item => item.name === slice.field)
  const viewer = getColumnMoneyViewer(column)
  const transform = viewer
    ? direction === 'raw-to-display'
      ? (value: unknown) => moneyRawToDisplayFilterValue(value, viewer)
      : (value: unknown) => moneyDisplayToRawFilterValue(value, viewer)
    : (value: unknown) => value

  return {
    ...slice,
    ...(slice.value === undefined ? {} : { value: transformSliceValue(slice.value, transform) }),
    ...(slice.children
      ? { children: slice.children.map(child => transformSlice(child, columns, direction)) }
      : {})
  }
}

export function viewerSlicesToRaw(
  slices: readonly SliceRequestDef[],
  columns: readonly ColumnSchema[]
): SliceRequestDef[] {
  return slices.map(slice => transformSlice(slice, columns, 'display-to-raw'))
}

export function viewerSlicesToDisplay(
  slices: readonly SliceRequestDef[],
  columns: readonly ColumnSchema[]
): SliceRequestDef[] {
  return slices.map(slice => transformSlice(slice, columns, 'raw-to-display'))
}
