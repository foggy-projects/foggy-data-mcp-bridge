import { computed, ref } from 'vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import type { BundleItem, DatasourceItem, ModelItem, RuntimeCapabilities } from './types'

interface BundleList {
  bundles?: BundleItem[]
}

interface DatasourceDiagnostics {
  datasources?: DatasourceItem[]
  namespaceBindings?: Record<string, string>
}

interface ModelCatalog {
  data?: {
    items?: ModelItem[]
  }
}

const loading = ref(false)
const errorMessage = ref('')
const bundles = ref<BundleItem[]>([])
const datasources = ref<DatasourceItem[]>([])
const namespaceBindings = ref<Record<string, string>>({})
const models = ref<ModelItem[]>([])
const capabilities = ref<RuntimeCapabilities | null>(null)
let loadVersion = 0

function canonicalNamespace(value?: string): string {
  return value?.trim() || ''
}

const discoveredNamespaces = computed(() => {
  const names = new Set<string>()
  Object.keys(namespaceBindings.value).forEach(name => names.add(canonicalNamespace(name)))
  bundles.value.forEach(bundle => names.add(canonicalNamespace(bundle.namespace)))
  return [...names].sort((left, right) => {
    if (!left && right) return 1
    if (left && !right) return -1
    return left.localeCompare(right)
  })
})

export function useNamespaceWorkspaceData() {
  async function load(): Promise<void> {
    const version = ++loadVersion
    loading.value = true
    errorMessage.value = ''
    try {
      const [bundleResult, datasourceResult, modelResult, capabilityResult] = await Promise.all([
        runtimeApi.get<BundleList>('bundles'),
        runtimeApi.get<DatasourceDiagnostics>('datasources/diagnostics'),
        runtimeApi.get<ModelCatalog>('models', { format: 'json', fieldLimit: 6 }),
        runtimeApi.get<RuntimeCapabilities>('capabilities')
      ])
      if (version !== loadVersion) return
      bundles.value = bundleResult.bundles || []
      datasources.value = datasourceResult.datasources || []
      namespaceBindings.value = datasourceResult.namespaceBindings || {}
      models.value = modelResult.data?.items || []
      capabilities.value = capabilityResult
    } catch (error) {
      if (version !== loadVersion) return
      errorMessage.value = error instanceof RuntimeRequestError
        ? error.message
        : '无法读取数据与模型空间。'
    } finally {
      if (version === loadVersion) loading.value = false
    }
  }

  return {
    loading,
    errorMessage,
    bundles,
    datasources,
    namespaceBindings,
    models,
    capabilities,
    discoveredNamespaces,
    load
  }
}
