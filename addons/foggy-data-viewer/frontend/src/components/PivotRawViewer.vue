<script setup lang="ts">
import { computed } from 'vue'
import PivotViewer from './PivotViewer.vue'
import { toPivotViewModel } from '@/utils/pivotViewModelAdapter'
import type { PivotRawPayload, PivotViewModel } from '@/types'

interface Props {
  rawPayload: PivotRawPayload
  loading?: boolean
  error?: string | null
  height?: string | number
  emptyText?: string
  emptyCellText?: string
  showAxisPager?: boolean
  showEvidence?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
  height: '100%',
  emptyText: '暂无透视数据',
  emptyCellText: '-',
  showAxisPager: true,
  showEvidence: true
})

const adapted = computed<{ viewModel: PivotViewModel | null; error: string | null }>(() => {
  try {
    return {
      viewModel: toPivotViewModel(props.rawPayload),
      error: null
    }
  } catch (error) {
    return {
      viewModel: null,
      error: error instanceof Error ? error.message : '透视数据转换失败'
    }
  }
})

const displayError = computed(() => props.error ?? adapted.value.error)
</script>

<template>
  <PivotViewer
    v-if="adapted.viewModel"
    :view-model="adapted.viewModel"
    :loading="loading"
    :error="displayError"
    :height="height"
    :empty-text="emptyText"
    :empty-cell-text="emptyCellText"
    :show-axis-pager="showAxisPager"
    :show-evidence="showEvidence"
  >
    <template #empty>
      <slot name="empty" />
    </template>
  </PivotViewer>

  <section v-else class="pivot-raw-viewer-error" data-view-mode="pivotTable">
    {{ displayError }}
  </section>
</template>

<style scoped>
.pivot-raw-viewer-error {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  padding: 24px;
  color: #f56c6c;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
</style>
