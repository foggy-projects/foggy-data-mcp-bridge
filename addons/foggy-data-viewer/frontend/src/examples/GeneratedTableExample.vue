<script setup lang="ts">
/**
 * 三层接入示例：演示 generated → module → page 的完整链路
 *
 * 本示例模拟了基于 FactOrderQueryModel 的完整接入：
 * - Layer 1 (generated): schema + api + types
 * - Layer 2 (module): 业务包装（参数注入、列覆盖、hooks）
 * - Layer 3 (page): 页面布局
 *
 * 运行条件：后端需启动并有 FactOrderQueryModel 可用
 */
import { ref, computed } from 'vue'
import DataTableWithSearch from '@/components/DataTableWithSearch.vue'
import { fetchQueryDataDirect, fetchMemberOptions, fetchFrontendMeta } from '@/api/viewer'
import { calculateColumnWidth } from '@/utils/schemaHelper'
import type {
  EnhancedColumnSchema,
  TableSchema,
  FetchDataParams,
  FetchDataResult,
  SliceRequestDef,
  QueryHooks,
  MemberQueryRequest,
  MemberQueryResponse,
  FrontendMeta,
} from '@/types'
import type { QuerySchema } from '@/components/QueryPanel.vue'

// ══════════════════════════════════════
// Layer 1: Generated (模拟生成器产物)
// ══════════════════════════════════════

const QM_MODEL = 'FactOrderQueryModel'

// 加载状态
const metaLoaded = ref(false)
const metaError = ref<string | null>(null)
const tableSchema = ref<TableSchema>({ columns: [] })
const querySchema = ref<QuerySchema>({ fields: [], submitMode: 'manual' })

/** 从后端加载 frontend-meta 并构建 schema */
async function loadMeta() {
  try {
    const meta: FrontendMeta = await fetchFrontendMeta(QM_MODEL)

    // 构建表格列
    const columns: EnhancedColumnSchema[] = meta.fields
      .filter(f => f.uiHints?.visible !== false)
      .map(f => ({
        name: f.name,
        title: f.title,
        type: f.type,
        filterType: f.filterType as any,
        filterable: f.filterable,
        measure: f.measure,
        aggregatable: f.aggregatable,
        dictId: f.dictId,
        memberLookup: f.memberLookup,
        width: f.uiHints?.width ?? calculateColumnWidth(f.title, f.type),
      }))

    // 默认可见列
    const visibleNames = meta.defaults?.visibleColumns ?? columns.map(c => c.name)

    tableSchema.value = {
      columns: columns.filter(c => visibleNames.includes(c.name)),
      searchableFields: meta.defaults?.searchFields,
      pageSize: meta.defaults?.pageSize ?? 50,
      showFilters: true,
      showPager: true,
    }

    // 构建查询 schema（仅 filterable + form placement 字段）
    const qFields = meta.fields
      .filter(f => f.filterable && f.category !== 'dimension-id')
      .map(f => {
        const isMember = f.filterType === 'dimension' && f.memberLookup?.enabled
        const isDict = !!f.dictId
        const isDate = f.filterType === 'date' || f.filterType === 'datetime'
        const isNumber = f.filterType === 'number'

        return {
          key: f.name,
          label: f.title,
          sourceField: isMember ? f.memberLookup!.selectionFieldName : f.name,
          placement: (isDate || isNumber || isMember ? 'form' : 'column') as 'form' | 'column',
          component: isMember ? 'memberSelect'
            : isDict ? 'dictSelect'
            : isDate ? 'dateRange'
            : isNumber ? 'numberRange'
            : 'text' as any,
          defaultOperator: isMember ? 'in' : isDict ? 'in' : isDate ? '[]' : isNumber ? '[]' : 'like',
          dictId: f.dictId,
          lookupRef: isMember ? f.name : undefined,
          span: isDate || isNumber ? 2 : 1,
        }
      })

    querySchema.value = {
      fields: qFields.filter(f => f.placement === 'form'),
      submitMode: 'manual',
      collapsible: true,
      defaultExpanded: true,
      layout: {
        mode: 'grid',
        columns: { xs: 1, sm: 2, md: 3, lg: 4, xl: 4 },
        labelWidth: 100,
        actionAlign: 'right',
        collapsedRows: 1,
        gutter: 16,
      },
    }

    metaLoaded.value = true
  } catch (e: any) {
    metaError.value = e.message || '加载元数据失败'
  }
}

/** 查询数据（直连，无需 queryId） */
async function queryOrders(params: FetchDataParams): Promise<FetchDataResult> {
  const response = await fetchQueryDataDirect(QM_MODEL, {
    start: (params.page - 1) * params.pageSize,
    limit: params.pageSize,
    columns: params.columns,
    slice: params.slice,
    orderBy: params.orderBy,
  })
  return {
    items: response.items as Record<string, unknown>[],
    total: response.total ?? 0,
    totalData: response.totalData as Record<string, unknown> | undefined,
  }
}

// ══════════════════════════════════════
// Layer 2: Module (业务包装)
// ══════════════════════════════════════

/** 列覆盖 */
const columnOverrides: Record<string, Partial<EnhancedColumnSchema>> = {
  amount: { width: 120, customFormatter: (v: unknown) => v != null ? `¥${Number(v).toFixed(2)}` : '-' },
  payAmount: { width: 130, customFormatter: (v: unknown) => v != null ? `¥${Number(v).toFixed(2)}` : '-' },
}

/** 合并列覆盖后的 schema */
const mergedSchema = computed<TableSchema>(() => {
  const base = tableSchema.value
  const cols = base.columns.map(col => {
    const override = columnOverrides[col.name]
    return override ? { ...col, ...override } : col
  })
  return { ...base, columns: cols }
})

/** 查询钩子 */
const queryHooks: QueryHooks = {
  onBeforeQuery(ctx) {
    console.log(`[订单模块] 查询: ${ctx.trigger}, page=${ctx.params.page}, filters=${ctx.params.slice.length}`)
  },
  onAfterQuery(_ctx, result) {
    console.log(`[订单模块] 完成: ${result.total} 条`)
  },
}

// ══════════════════════════════════════
// Layer 3: Page
// ══════════════════════════════════════

// 启动时加载元数据
loadMeta()
</script>

<template>
  <div class="generated-table-example">
    <h2>三层接入示例：FactOrderQueryModel</h2>
    <p class="description">
      演示 frontend-meta v1 → 动态构建 schema → QueryPanel + DataTableWithSearch 的完整链路
    </p>

    <div v-if="metaError" class="error-box">
      加载失败: {{ metaError }}
    </div>

    <div v-else-if="!metaLoaded" class="loading-box">
      加载元数据中...
    </div>

    <DataTableWithSearch
      v-else
      :schema="mergedSchema"
      :fetch-data="queryOrders"
      :query-schema="querySchema"
      :show-query-panel="true"
      :qm-model="QM_MODEL"
      :filter-member-loader="fetchMemberOptions"
      :query-hooks="queryHooks"
    >
      <template #toolbar>
        <button style="padding: 6px 12px; font-size: 12px; cursor: pointer;">
          导出（示例）
        </button>
      </template>
    </DataTableWithSearch>
  </div>
</template>

<style scoped>
.generated-table-example {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.description {
  margin: 0 0 24px;
  color: #909399;
  font-size: 14px;
}

.error-box {
  padding: 16px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 4px;
  border: 1px solid #fde2e2;
}

.loading-box {
  padding: 40px;
  text-align: center;
  color: #909399;
}
</style>
