import { ref } from 'vue'

/**
 * 表格行选择逻辑
 */
export function useTableSelection<T = Record<string, unknown>>() {
  const selectedRows = ref<T[]>([])

  /**
   * checkbox 变化处理
   */
  function onCheckboxChange({ records }: { records: T[] }) {
    selectedRows.value = records
  }

  /**
   * 全选变化处理
   */
  function onCheckboxAll({ records }: { records: T[] }) {
    selectedRows.value = records
  }

  /**
   * 清空选择
   */
  function clearSelection() {
    selectedRows.value = []
  }

  /**
   * 获取选中行数
   */
  function getSelectedCount() {
    return selectedRows.value.length
  }

  return {
    selectedRows,
    onCheckboxChange,
    onCheckboxAll,
    clearSelection,
    getSelectedCount
  }
}
