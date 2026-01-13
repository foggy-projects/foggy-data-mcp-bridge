<script setup>
import { withBase } from 'vitepress'

const props = defineProps({
  filename: {
    type: String,
    required: true
  },
  title: {
    type: String,
    default: '下载文档'
  }
})
</script>

<template>
  <a :href="withBase(`/downloads/${filename}`)" :download="filename" class="download-btn">
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
      <polyline points="7 10 12 15 17 10"></polyline>
      <line x1="12" y1="15" x2="12" y2="3"></line>
    </svg>
    {{ title }}
  </a>
</template>

<style scoped>
.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  color: var(--vp-c-brand);
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
  transition: color 0.2s;
}

.download-btn:hover {
  color: var(--vp-c-brand-dark);
  text-decoration: underline;
}

.download-btn svg {
  width: 16px;
  height: 16px;
}
</style>
