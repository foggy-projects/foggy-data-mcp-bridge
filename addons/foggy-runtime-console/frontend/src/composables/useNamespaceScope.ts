import { computed } from 'vue'
import { useRuntimeSession } from '@/stores/session'

export interface NamespaceScopeSnapshot {
  namespace: string
  revision: number
}

export function useNamespaceScope() {
  const session = useRuntimeSession()
  const label = computed(() => session.namespace.value || '空 Namespace')

  function snapshot(): NamespaceScopeSnapshot {
    return {
      namespace: session.namespace.value,
      revision: session.namespaceRevision.value
    }
  }

  function isCurrent(candidate: NamespaceScopeSnapshot): boolean {
    return candidate.namespace === session.namespace.value
      && candidate.revision === session.namespaceRevision.value
  }

  return {
    namespace: session.namespace,
    revision: session.namespaceRevision,
    label,
    snapshot,
    isCurrent
  }
}
