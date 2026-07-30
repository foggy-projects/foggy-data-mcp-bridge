<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const tools = [
  {
    route: 'compose',
    label: 'Compose',
    code: 'A',
    description: '受限 Compose / CTE 的校验、预览与执行'
  },
  {
    route: 'fsscript',
    label: 'FSScript',
    code: 'B',
    description: '需要显式确认的高级脚本执行'
  }
] as const

const activeRoute = computed(() => String(route.name))

function openTool(name: 'compose' | 'fsscript'): void {
  void router.push({ name })
}
</script>

<template>
  <nav class="execution-tool-tabs" aria-label="执行工具类型">
    <button
      v-for="tool in tools"
      :key="tool.route"
      type="button"
      :class="{ active: activeRoute === tool.route }"
      :aria-current="activeRoute === tool.route ? 'page' : undefined"
      @click="openTool(tool.route)"
    >
      <span>{{ tool.code }}</span>
      <strong>{{ tool.label }}</strong>
      <small>{{ tool.description }}</small>
    </button>
  </nav>
</template>

<style scoped>
.execution-tool-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: var(--console-space-4);
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.execution-tool-tabs button {
  min-width: 0;
  min-height: 62px;
  display: grid;
  grid-template-columns: 22px auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 0;
  border-right: 1px solid var(--console-line);
  border-radius: 0;
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
  text-align: left;
}

.execution-tool-tabs button:last-child {
  border-right: 0;
}

.execution-tool-tabs button:hover {
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 7px,
      var(--console-hatch-line) 7px 8px
    );
  color: var(--console-text);
}

.execution-tool-tabs button.active {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.execution-tool-tabs span {
  font: 650 10px/1 var(--console-mono);
  opacity: 0.7;
}

.execution-tool-tabs strong {
  font-size: 14px;
}

.execution-tool-tabs small {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  font-weight: 450;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 620px) {
  .execution-tool-tabs {
    grid-template-columns: 1fr;
  }

  .execution-tool-tabs button {
    border-right: 0;
    border-bottom: 1px solid var(--console-line);
  }

  .execution-tool-tabs button:last-child {
    border-bottom: 0;
  }
}
</style>
