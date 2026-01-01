<script setup lang="ts">
import { computed } from 'vue'
import DataViewer from './components/DataViewer.vue'

// 从 URL 中获取 queryId
const queryId = computed(() => {
  const path = window.location.pathname
  // 匹配 /data-viewer/view/{queryId} 格式
  const match = path.match(/\/data-viewer\/view\/([^/]+)/)
  return match ? match[1] : null
})
</script>

<template>
  <div id="app">
    <DataViewer v-if="queryId" :query-id="queryId" />
    <div v-else class="no-query">
      <h1>Foggy Data Viewer</h1>
      <p>请通过有效的查询链接访问数据浏览器</p>
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.no-query {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: #606266;
}

.no-query h1 {
  margin-bottom: 16px;
  color: #303133;
}
</style>
