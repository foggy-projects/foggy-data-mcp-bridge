import type {
  GlobalColumnRenderContext,
  GlobalColumnRenderResolution,
  GlobalColumnRenderer
} from '@/types'

interface RendererEntry {
  renderer: GlobalColumnRenderer
  order: number
}

let nextOrder = 0
const entries: RendererEntry[] = []

function sortEntries(left: RendererEntry, right: RendererEntry): number {
  const priorityDiff = (right.renderer.priority ?? 0) - (left.renderer.priority ?? 0)
  return priorityDiff !== 0 ? priorityDiff : left.order - right.order
}

function matchesRenderer(renderer: GlobalColumnRenderer, ctx: GlobalColumnRenderContext): boolean {
  return renderer.match ? renderer.match(ctx) : true
}

function findEntry(id: string): number {
  return entries.findIndex(entry => entry.renderer.id === id)
}

function removeEntry(renderer: GlobalColumnRenderer, order: number): void {
  const index = entries.findIndex(entry => entry.renderer === renderer && entry.order === order)
  if (index >= 0) {
    entries.splice(index, 1)
  }
}

function addRenderer(renderer: GlobalColumnRenderer): () => void {
  const entry: RendererEntry = {
    renderer,
    order: nextOrder++
  }
  entries.push(entry)
  return () => removeEntry(renderer, entry.order)
}

function resolveRenderer(ctx: GlobalColumnRenderContext): GlobalColumnRenderer | undefined {
  return entries
    .slice()
    .sort(sortEntries)
    .find(entry => matchesRenderer(entry.renderer, ctx))
    ?.renderer
}

/**
 * Global column renderer registry.
 *
 * Host applications register business-specific renderers once at startup.
 * DataTable keeps page-level slots and customRender ahead of this registry.
 */
export const globalColumnRenderers = {
  add(renderer: GlobalColumnRenderer): () => void {
    return addRenderer(renderer)
  },

  register(renderers: GlobalColumnRenderer | GlobalColumnRenderer[]): () => void {
    const list = Array.isArray(renderers) ? renderers : [renderers]
    const disposers = list.map(renderer => addRenderer(renderer))
    return () => disposers.forEach(dispose => dispose())
  },

  remove(id: string): void {
    let index = findEntry(id)
    while (index >= 0) {
      entries.splice(index, 1)
      index = findEntry(id)
    }
  },

  clear(): void {
    entries.splice(0, entries.length)
  },

  list(): GlobalColumnRenderer[] {
    return entries
      .slice()
      .sort(sortEntries)
      .map(entry => entry.renderer)
  },

  resolve(ctx: GlobalColumnRenderContext): GlobalColumnRenderer | undefined {
    return resolveRenderer(ctx)
  },

  render(ctx: GlobalColumnRenderContext): GlobalColumnRenderResolution | undefined {
    const renderer = resolveRenderer(ctx)
    if (!renderer) return undefined

    return {
      renderer,
      value: renderer.render(ctx)
    }
  }
}
