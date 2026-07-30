<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ResourceCard from '@/components/resources/ResourceCard.vue'
import ModelDetailDrawer from './ModelDetailDrawer.vue'
import ModelLifecycleCenter from './ModelLifecycleCenter.vue'
import type { ModelItem } from '@/features/namespace/types'

const props = defineProps<{
  namespace: string
  models: ModelItem[]
  loading: boolean
}>()
const emit = defineEmits<{ reload: [] }>()
const search = ref('')
const bundleFilter = ref('')
const selected = ref<string[]>([])
const detailModel = ref<ModelItem | null>(null)
const detailOpen = ref(false)

const bundleOptions = computed(() =>
  [...new Set(props.models.map(item => item.bundleName || '').filter(Boolean))].sort()
)

const filteredModels = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return props.models.filter(item => {
    const matchesBundle = !bundleFilter.value || item.bundleName === bundleFilter.value
    const matchesSearch = !keyword || [
      item.model,
      item.caption,
      item.description,
      item.bundleName,
      item.primaryTimeField
    ].some(value => value?.toLowerCase().includes(keyword))
    return matchesBundle && matchesSearch
  })
})

function toggleModel(name: string): void {
  selected.value = selected.value.includes(name)
    ? selected.value.filter(item => item !== name)
    : [...selected.value, name]
}

function openDetail(item: ModelItem): void {
  detailModel.value = item
  detailOpen.value = true
}

watch(() => props.models, models => {
  selected.value = selected.value.filter(name => models.some(item => item.model === name))
})
</script>

<template>
  <section aria-labelledby="model-catalog-title">
    <div class="catalog-intro">
      <div>
        <span class="console-panel-kicker">QM CATALOG / NAMESPACE SCOPED</span>
        <h2 id="model-catalog-title">分析模型（QM）</h2>
        <p>Bundle 表示模型资源来源；QM 与 TM 是可复用的依赖关系，不是级联所有权。</p>
      </div>
    </div>

    <div class="toolbar">
      <label class="console-field">
        <span class="console-label">搜索模型</span>
        <input v-model="search" class="console-input" type="search" placeholder="名称、说明、Bundle">
      </label>
      <label class="console-field">
        <span class="console-label">Bundle 来源</span>
        <select v-model="bundleFilter" class="console-select">
          <option value="">全部来源</option>
          <option v-for="bundle in bundleOptions" :key="bundle" :value="bundle">{{ bundle }}</option>
        </select>
      </label>
      <div class="toolbar-spacer" />
      <span class="status-chip">{{ filteredModels.length }} QM</span>
      <span class="status-chip">{{ selected.length }} SELECTED</span>
    </div>

    <ModelLifecycleCenter
      :namespace="namespace"
      :selected-models="selected"
      :total-models="models.length"
      @reload="emit('reload')"
    />

    <div class="resource-grid" aria-live="polite">
      <ResourceCard
        v-for="item in filteredModels"
        :key="item.model"
        code="QM"
        :title="item.caption || item.model"
        :caption="item.model"
        :description="item.description || '此模型暂未提供语义描述。'"
        :selected="detailOpen && detailModel?.model === item.model"
      >
        <template #status>
          <span class="status-chip" :class="{ warning: !item.sourceKnown }">
            {{ item.sourceKnown ? 'SOURCE KNOWN' : 'SOURCE UNKNOWN' }}
          </span>
        </template>
        <template #meta>
          <span>{{ item.fieldCount ?? '—' }} fields</span>
          <span>{{ item.bundleName || '来源未知' }}</span>
          <span>{{ item.primaryTimeField || 'no primary time' }}</span>
        </template>
        <template #actions>
          <label class="card-check">
            <input
              type="checkbox"
              :checked="selected.includes(item.model)"
              :aria-label="`选择 ${item.model}`"
              @change="toggleModel(item.model)"
            >
            <span>选择</span>
          </label>
          <button class="console-button compact primary" type="button" @click="openDetail(item)">查看详情</button>
        </template>
      </ResourceCard>
    </div>

    <div v-if="!loading && !filteredModels.length" class="empty-state console-panel">
      <div><strong>没有可见 QM</strong>检查当前空间、Bundle 或模型权限后重新读取。</div>
    </div>

    <ModelDetailDrawer
      v-model:open="detailOpen"
      :model="detailModel"
      :namespace="namespace"
    />
  </section>
</template>

<style scoped>
.catalog-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.catalog-intro h2 {
  margin: 7px 0 6px;
  font-size: 24px;
}

.catalog-intro p {
  max-width: 720px;
  margin: 0;
  color: var(--console-muted);
  font-size: 13px;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.card-check {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--console-muted);
  font: 11px/1 var(--console-mono);
}

@media (max-width: 760px) {
  .catalog-intro {
    align-items: stretch;
    flex-direction: column;
  }

}
</style>
