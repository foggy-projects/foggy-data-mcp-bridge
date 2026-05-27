<script setup lang="ts">
import { computed } from 'vue'
import type { PivotAxisField } from '@/types'

interface Props {
  evidence?: Record<string, unknown>
  rowAxes?: PivotAxisField[]
  columnAxes?: PivotAxisField[]
}

const props = withDefaults(defineProps<Props>(), {
  evidence: () => ({}),
  rowAxes: () => [],
  columnAxes: () => []
})

interface EvidenceItem {
  label: string
  value: string
}

const axisItems = computed<EvidenceItem[]>(() => [
  ...buildAxisItems('行轴', props.rowAxes),
  ...buildAxisItems('列轴', props.columnAxes)
])

const derivedMetricItems = computed<EvidenceItem[]>(() => [
  ...buildParentShareEvidenceItems(props.evidence.parentShareEvidence),
  ...buildBaselineRatioEvidenceItems(props.evidence.baselineRatioEvidence)
])

const evidenceItems = computed<EvidenceItem[]>(() => {
  return Object.entries(props.evidence)
    .filter(([key]) => key !== 'parentShareEvidence' && key !== 'baselineRatioEvidence')
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => ({
      label: key,
      value: formatEvidenceValue(value)
    }))
})

function buildAxisItems(prefix: string, axes: PivotAxisField[]): EvidenceItem[] {
  return axes.map(axis => ({
    label: `${prefix} ${axis.title ?? axis.field}`,
    value: formatAxisValue(axis)
  }))
}

function formatAxisValue(axis: PivotAxisField): string {
  const parts = [
    `field=${axis.field}`,
    axis.start === undefined ? undefined : `start=${axis.start}`,
    axis.offset === undefined ? undefined : `offset=${axis.offset}`,
    axis.limit === undefined ? undefined : `limit=${axis.limit}`,
    axis.orderBy?.length ? `orderBy=${axis.orderBy.join(',')}` : undefined,
    axis.domainSliceEnabled ? 'domainSlice' : undefined,
    axis.havingEnabled ? 'having' : undefined
  ].filter((part): part is string => Boolean(part))

  return parts.join(' | ')
}

function buildParentShareEvidenceItems(value: unknown): EvidenceItem[] {
  return normalizeEvidenceArray(value).map((item, index) => ({
    label: `parentShare ${formatEvidenceField(item.metric, index + 1)}`,
    value: formatKeyValueParts([
      ['of', item.of],
      ['scope', item.denominatorScope],
      ['axis', item.axis],
      ['level', item.level],
      ['parentLevel', item.parentLevel],
      ['prePageRows', item.prePageRows],
      ['parentGroups', item.parentGroups],
      ['source', item.source]
    ])
  }))
}

function buildBaselineRatioEvidenceItems(value: unknown): EvidenceItem[] {
  return normalizeEvidenceArray(value).map((item, index) => ({
    label: `baselineRatio ${formatEvidenceField(item.metric, index + 1)}`,
    value: formatKeyValueParts([
      ['of', item.of],
      ['scope', item.baselineScope],
      ['axis', item.axis],
      ['baseline', item.baseline],
      ['columnField', item.columnField],
      ['baselineColumnKey', item.baselineColumnKey],
      ['baselineColumnVisible', item.baselineColumnVisible],
      ['prePageAxisDomainSize', item.prePageAxisDomainSize],
      ['visibleAxisDomainSize', item.visibleAxisDomainSize],
      ['baselineRows', item.baselineRows],
      ['source', item.source]
    ])
  }))
}

function normalizeEvidenceArray(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.filter(isRecord)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function formatEvidenceField(value: unknown, fallbackIndex: number): string {
  if (typeof value === 'string' && value.length > 0) {
    return value
  }

  return `#${fallbackIndex}`
}

function formatKeyValueParts(parts: Array<[string, unknown]>): string {
  return parts
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${key}=${formatEvidenceValue(value)}`)
    .join(' | ')
}

function formatEvidenceValue(value: unknown): string {
  if (typeof value === 'string') {
    return value
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  return JSON.stringify(value)
}
</script>

<template>
  <section class="pivot-evidence-panel" aria-label="Pivot evidence">
    <div v-if="axisItems.length" class="pivot-evidence-section">
      <div class="pivot-evidence-heading">轴域范围</div>
      <dl class="pivot-evidence-list">
        <template v-for="item in axisItems" :key="item.label">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </template>
      </dl>
    </div>

    <div v-if="derivedMetricItems.length" class="pivot-evidence-section">
      <div class="pivot-evidence-heading">派生指标证据</div>
      <dl class="pivot-evidence-list">
        <template v-for="item in derivedMetricItems" :key="item.label">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </template>
      </dl>
    </div>

    <div v-if="evidenceItems.length" class="pivot-evidence-section">
      <div class="pivot-evidence-heading">计算证据</div>
      <dl class="pivot-evidence-list">
        <template v-for="item in evidenceItems" :key="item.label">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </template>
      </dl>
    </div>
  </section>
</template>

<style scoped>
.pivot-evidence-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  font-size: 12px;
  color: #303133;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.pivot-evidence-heading {
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}

.pivot-evidence-list {
  display: grid;
  grid-template-columns: minmax(120px, max-content) minmax(0, 1fr);
  gap: 6px 12px;
  margin: 0;
}

.pivot-evidence-list dt {
  min-width: 0;
  overflow: hidden;
  color: #606266;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pivot-evidence-list dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
