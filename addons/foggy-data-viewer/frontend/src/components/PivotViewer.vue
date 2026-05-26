<script setup lang="ts">
import { computed } from 'vue'
import PivotAxisPager from './PivotAxisPager.vue'
import PivotEvidencePanel from './PivotEvidencePanel.vue'
import PivotGrid from './PivotGrid.vue'
import type { PivotViewModel } from '@/types'

interface Props {
  viewModel: PivotViewModel
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

const isGridShape = computed(() => props.viewModel.shape === 'grid')
const unsupportedShapeText = computed(() => `暂不支持 ${props.viewModel.shape} 透视视图`)
</script>

<template>
  <section class="pivot-viewer" data-view-mode="pivotTable">
    <header class="pivot-viewer-header">
      <div class="pivot-viewer-title">透视表</div>
      <PivotAxisPager
        v-if="showAxisPager"
        :row-axes="viewModel.rowAxes"
        :column-axes="viewModel.columnAxes"
        :row-pages="viewModel.axisPages?.rows"
        :column-pages="viewModel.axisPages?.columns"
      />
    </header>

    <div v-if="error" class="pivot-viewer-state pivot-viewer-error">
      {{ error }}
    </div>

    <div v-else-if="!isGridShape" class="pivot-viewer-state pivot-viewer-unsupported">
      {{ unsupportedShapeText }}
    </div>

    <div v-else class="pivot-viewer-main">
      <PivotGrid
        :view-model="viewModel"
        :loading="loading"
        :height="height"
        :empty-text="emptyText"
        :empty-cell-text="emptyCellText"
      >
        <template #empty>
          <slot name="empty">
            <div class="pivot-viewer-empty">{{ emptyText }}</div>
          </slot>
        </template>
      </PivotGrid>
    </div>

    <PivotEvidencePanel
      v-if="showEvidence && !error && isGridShape"
      :row-axes="viewModel.rowAxes"
      :column-axes="viewModel.columnAxes"
      :evidence="viewModel.evidence"
    />
  </section>
</template>

<style scoped>
.pivot-viewer {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 12px;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #fff;
}

.pivot-viewer-header {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.pivot-viewer-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.pivot-viewer-main {
  min-width: 0;
  min-height: 0;
}

.pivot-viewer-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  padding: 24px;
  color: #606266;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.pivot-viewer-error {
  color: #f56c6c;
}

.pivot-viewer-empty {
  padding: 24px;
  color: #909399;
  text-align: center;
}
</style>
