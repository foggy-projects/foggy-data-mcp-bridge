<script setup lang="ts">
defineProps<{
  code: string
  title: string
  caption?: string
  description?: string
  selected?: boolean
}>()
</script>

<template>
  <article class="resource-card" :class="{ selected }">
    <header class="resource-card-header">
      <span class="resource-card-code">{{ code }}</span>
      <slot name="status" />
    </header>
    <div class="resource-card-identity">
      <div v-if="caption" class="resource-card-caption">{{ caption }}</div>
      <h2>{{ title }}</h2>
      <p>{{ description || '当前资源未提供说明。' }}</p>
    </div>
    <div class="resource-card-meta"><slot name="meta" /></div>
    <footer class="resource-card-actions"><slot name="actions" /></footer>
  </article>
</template>

<style scoped>
.resource-card {
  position: relative;
  min-height: 286px;
  display: flex;
  flex-direction: column;
  padding: 19px;
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
  transition: transform var(--console-transition), box-shadow var(--console-transition);
}

.resource-card::after {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 18px;
  height: 18px;
  border-right: 3px solid var(--console-paper);
  border-bottom: 3px solid var(--console-paper);
  content: "";
  opacity: 0;
}

.resource-card:hover,
.resource-card:focus-within,
.resource-card.selected {
  z-index: 1;
  box-shadow: 5px 5px 0 var(--console-line);
  transform: translate(-2px, -2px);
}

.resource-card.selected::after {
  opacity: 1;
}

.resource-card-header {
  min-height: 28px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.resource-card-code,
.resource-card-caption {
  color: var(--console-dim);
  font: 650 10px/1.3 var(--console-mono);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.resource-card-identity {
  flex: 1;
}

.resource-card-caption {
  margin-top: 13px;
}

.resource-card h2 {
  margin: 7px 0 9px;
  overflow-wrap: anywhere;
  font-size: 21px;
}

.resource-card p {
  margin: 0 0 18px;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.65;
}

.resource-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 12px;
  margin-bottom: 16px;
  color: var(--console-dim);
  font: 11px/1.45 var(--console-mono);
}

.resource-card-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 13px;
  border-top: 1px solid var(--console-line);
}

@media (max-width: 560px) {
  .resource-card {
    min-height: 0;
  }
}
</style>
