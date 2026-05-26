import type { PivotHeaderNode } from '@/types/pivot'

export interface PivotHeaderValidationResult {
  valid: boolean
  errors: string[]
}

export interface PivotGridColumn {
  field?: string
  title: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  align?: 'left' | 'center' | 'right'
  headerAlign?: 'left' | 'center' | 'right'
  children?: PivotGridColumn[]
  meta?: {
    role: PivotHeaderNode['role']
    key?: string
    axisValue?: unknown
    metricField?: string
  }
}

export interface BuildPivotGridColumnsOptions {
  defaultRowAxisFixed?: 'left' | 'right' | false
  defaultMinWidth?: number
  metricAlign?: 'left' | 'center' | 'right'
  rowAxisAlign?: 'left' | 'center' | 'right'
}

const DEFAULT_MIN_WIDTH = 120

export function validatePivotHeaderTree(
  headerTree: PivotHeaderNode[]
): PivotHeaderValidationResult {
  const errors: string[] = []
  const leafFields = new Set<string>()

  if (!Array.isArray(headerTree) || headerTree.length === 0) {
    return {
      valid: false,
      errors: ['headerTree must contain at least one node']
    }
  }

  const visit = (node: PivotHeaderNode, path: string): void => {
    if (!node || typeof node !== 'object') {
      errors.push(`${path}: node must be an object`)
      return
    }

    if (!node.title || typeof node.title !== 'string') {
      errors.push(`${path}: title is required`)
    }

    if (!node.role) {
      errors.push(`${path}: role is required`)
    }

    if (node.children !== undefined) {
      if (!Array.isArray(node.children) || node.children.length === 0) {
        errors.push(`${path}: children must contain at least one node`)
        return
      }

      node.children.forEach((child, index) => {
        visit(child, `${path}.children[${index}]`)
      })
      return
    }

    if (!node.field || typeof node.field !== 'string') {
      errors.push(`${path}: leaf node field is required`)
      return
    }

    if (leafFields.has(node.field)) {
      errors.push(`${path}: duplicate leaf field "${node.field}"`)
      return
    }

    leafFields.add(node.field)
  }

  headerTree.forEach((node, index) => visit(node, `headerTree[${index}]`))

  return {
    valid: errors.length === 0,
    errors
  }
}

export function flattenPivotLeafNodes(headerTree: PivotHeaderNode[]): PivotHeaderNode[] {
  const leaves: PivotHeaderNode[] = []

  const visit = (node: PivotHeaderNode): void => {
    if (node.children?.length) {
      node.children.forEach(visit)
      return
    }

    leaves.push(node)
  }

  headerTree.forEach(visit)
  return leaves
}

export function buildPivotGridColumns(
  headerTree: PivotHeaderNode[],
  options: BuildPivotGridColumnsOptions = {}
): PivotGridColumn[] {
  const validation = validatePivotHeaderTree(headerTree)
  if (!validation.valid) {
    throw new Error(`Invalid pivot header tree: ${validation.errors.join('; ')}`)
  }

  const defaultMinWidth = options.defaultMinWidth ?? DEFAULT_MIN_WIDTH
  const defaultRowAxisFixed = options.defaultRowAxisFixed ?? 'left'
  const metricAlign = options.metricAlign ?? 'right'
  const rowAxisAlign = options.rowAxisAlign ?? 'left'

  const build = (
    node: PivotHeaderNode,
    inheritedFixed?: 'left' | 'right'
  ): PivotGridColumn => {
    const fixed = node.fixed ?? inheritedFixed

    if (node.children?.length) {
      return {
        title: node.title,
        fixed,
        children: node.children.map(child => build(child, fixed)),
        meta: buildMeta(node)
      }
    }

    const leafFixed = fixed ?? (
      node.role === 'rowAxis' && defaultRowAxisFixed
        ? defaultRowAxisFixed
        : undefined
    )

    return {
      field: node.field,
      title: node.title,
      width: node.width,
      minWidth: node.minWidth ?? defaultMinWidth,
      fixed: leafFixed,
      align: node.role === 'rowAxis' ? rowAxisAlign : metricAlign,
      headerAlign: 'center',
      meta: buildMeta(node)
    }
  }

  return headerTree.map(node => build(node))
}

function buildMeta(node: PivotHeaderNode): PivotGridColumn['meta'] {
  return {
    role: node.role,
    key: node.key,
    axisValue: node.axisValue,
    metricField: node.metricField
  }
}
