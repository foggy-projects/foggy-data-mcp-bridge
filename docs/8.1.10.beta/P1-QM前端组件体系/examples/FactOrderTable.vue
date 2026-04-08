<!--
  FactOrderQueryModel - 自动生成的表格组件
  ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改

  用法：
    业务系统不应直接使用此组件，
    请通过 modules/order/OrderListModule.vue 包装后使用。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { DataTableWithSearch, buildTableColumns } from 'foggy-data-viewer'
import type {
  SliceRequestDef,
  QueryHooks,
  TableConfig,
  QuerySchema,
  EnhancedColumnSchema,
} from 'foggy-data-viewer'
import { tableSchema, allColumns } from './FactOrder.table.schema'
import { querySchema as defaultQuerySchema } from './FactOrder.query.schema'
import { queryOrders } from './FactOrder.api'
import { FACT_ORDER_QM_MODEL } from './FactOrder.types'

interface BusinessColumnOverride {
  title?: string
  width?: number | string
  hidden?: boolean
  order?: number
  formatter?: (value: unknown) => string
  uiConfig?: Record<string, unknown>
}

const props = withDefaults(
  defineProps<{
    globalParams?: Record<string, unknown>
    customParams?: Record<string, unknown>
    initialSlices?: SliceRequestDef[]
    tableOverrides?: Partial<TableConfig>
    columnOverrides?: Record<string, BusinessColumnOverride>
    queryOverrides?: Partial<QuerySchema>
    queryHooks?: QueryHooks
    showQueryPanel?: boolean
  }>(),
  {
    showQueryPanel: false,
  }
)

/** 合并列覆盖 */
const mergedSchema = computed(() => {
  let columns: EnhancedColumnSchema[] = [...tableSchema.columns]

  if (props.columnOverrides) {
    columns = columns
      .map((col) => {
        const override = props.columnOverrides?.[col.name]
        if (!override) return col
        if (override.hidden) return null
        return {
          ...col,
          ...(override.title && { title: override.title }),
          ...(override.width && { width: override.width }),
          ...(override.formatter && { customFormatter: override.formatter }),
          ...(override.uiConfig && {
            uiConfig: { ...col.uiConfig, ...override.uiConfig },
          }),
        }
      })
      .filter(Boolean) as EnhancedColumnSchema[]
  }

  return {
    ...tableSchema,
    columns,
    ...(props.tableOverrides?.qmModel && {
      qmModel: props.tableOverrides.qmModel,
    }),
  }
})

/** 合并查询 schema 覆盖 */
const mergedQuerySchema = computed(() => {
  if (!props.queryOverrides) return defaultQuerySchema
  return {
    ...defaultQuerySchema,
    ...props.queryOverrides,
    fields: props.queryOverrides.fields ?? defaultQuerySchema.fields,
  }
})
</script>

<template>
  <DataTableWithSearch
    :schema="mergedSchema"
    :fetch-data="queryOrders"
    :query-schema="mergedQuerySchema"
    :show-query-panel="showQueryPanel"
    :query-hooks="queryHooks"
    :initial-slice="initialSlices"
  >
    <!-- 工具栏扩展位 -->
    <template v-if="$slots.toolbar" #toolbar>
      <slot name="toolbar" />
    </template>

    <!-- 行操作扩展位 -->
    <template v-if="$slots['row-actions']" #row-actions="scope">
      <slot name="row-actions" v-bind="scope" />
    </template>
  </DataTableWithSearch>
</template>
