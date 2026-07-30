export interface BundleResourceFileDraft {
  id: number
  path: string
  content: string
  baseSha256: string
}

export interface ResourceExportPayload {
  namespace: string
  bundle: string
  paths: string[]
  includeContent: boolean
}

export interface ResourceSavePayload {
  namespace: string
  bundle: string
  files: Array<{
    path: string
    content: string
    baseSha256: string | null
  }>
  validate: false
  refresh: false
}

export function normalizeResourcePaths(value: string): string[] {
  return [...new Set(value
    .split(/\r?\n/)
    .map(path => path.trim().replaceAll('\\', '/'))
    .filter(Boolean))]
}

export function resourcePathError(value: string): string {
  const path = value.trim().replaceAll('\\', '/')
  if (!path) return '资源路径不能为空。'
  if (path.startsWith('/') || /^[a-z]:\//i.test(path)) return '资源路径必须相对于 Bundle 根目录。'
  if (path.split('/').includes('..')) return '资源路径不能包含目录穿越（..）。'
  const filename = path.split('/').at(-1)?.toLowerCase() || ''
  const isModel = filename.endsWith('.tm') || filename.endsWith('.qm')
  const isModelList = (filename.includes('model-list') || filename.includes('modellist'))
    && ['.yml', '.yaml', '.json', '.txt'].some(extension => filename.endsWith(extension))
  if (!isModel && !isModelList) {
    return '仅支持 .tm、.qm 或 model-list 的 yml/yaml/json/txt 文件。'
  }
  return ''
}

export function buildExportPayload(
  namespace: string,
  bundle: string,
  pathsText: string,
  includeContent: boolean
): ResourceExportPayload {
  return {
    namespace,
    bundle,
    paths: normalizeResourcePaths(pathsText),
    includeContent
  }
}

export function buildSavePayload(
  namespace: string,
  bundle: string,
  files: BundleResourceFileDraft[]
): ResourceSavePayload {
  return {
    namespace,
    bundle,
    files: files.map(file => ({
      path: file.path.trim().replaceAll('\\', '/'),
      content: file.content,
      baseSha256: file.baseSha256.trim() || null
    })),
    validate: false,
    refresh: false
  }
}
