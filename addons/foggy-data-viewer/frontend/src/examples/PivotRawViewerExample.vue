<script setup lang="ts">
import PivotRawViewer from '@/components/PivotRawViewer.vue'
import { soaCandidatePivotRawFixture } from '@/__fixtures__/pivot/soaCandidatePivotRaw'
</script>

<template>
  <div class="pivot-raw-viewer-example">
    <header class="example-header">
      <div>
        <h2>TMS X6 SOA 应付核销候选透视表</h2>
        <p>ACCOUNT_RP_ITEM x subjectCode</p>
      </div>
    </header>

    <main class="example-main">
      <PivotRawViewer
        :raw-payload="soaCandidatePivotRawFixture"
        height="420"
        empty-cell-text="-"
      />
    </main>
  </div>
</template>

<style scoped>
.pivot-raw-viewer-example {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  min-height: 100vh;
  padding: 20px;
  color: #303133;
  background: #f5f7fa;
}

.example-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.example-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.example-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #606266;
}

.example-main {
  min-width: 0;
  min-height: 0;
  padding: 12px;
  overflow: auto;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

@media (max-width: 720px) {
  .pivot-raw-viewer-example {
    padding: 12px;
  }

  .example-header {
    align-items: flex-start;
  }
}
</style>
