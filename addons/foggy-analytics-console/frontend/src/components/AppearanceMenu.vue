<script setup lang="ts">
import { ref } from 'vue'
import { useAnalyticsTheme, type AnalyticsTheme } from '../theme'

const menu = ref<HTMLDetailsElement | null>(null)
const { theme, selectTheme } = useAnalyticsTheme()

const choose = (value: AnalyticsTheme) => {
  selectTheme(value)
  menu.value?.removeAttribute('open')
}
</script>

<template>
  <details ref="menu" class="appearance-menu">
    <summary aria-label="选择外观主题">
      <span class="appearance-icon" aria-hidden="true">◐</span>
      <span class="appearance-copy"><small>外观</small><strong>{{ theme === 'simple' ? '简洁' : '专业' }}</strong></span>
      <span class="appearance-chevron" aria-hidden="true">⌃</span>
    </summary>
    <div class="appearance-options" role="radiogroup" aria-label="外观主题">
      <div class="appearance-heading">选择主题</div>
      <button type="button" role="radio" :aria-checked="theme === 'simple'" @click="choose('simple')">
        <span class="theme-preview simple-preview" aria-hidden="true"><i></i><b></b></span>
        <span><strong>简洁</strong><small>明亮、专注于对话</small></span>
        <em aria-hidden="true">{{ theme === 'simple' ? '✓' : '' }}</em>
      </button>
      <button type="button" role="radio" :aria-checked="theme === 'professional'" @click="choose('professional')">
        <span class="theme-preview professional-preview" aria-hidden="true"><i></i><b></b></span>
        <span><strong>专业</strong><small>分析工作台风格</small></span>
        <em aria-hidden="true">{{ theme === 'professional' ? '✓' : '' }}</em>
      </button>
    </div>
  </details>
</template>

<style scoped>
.appearance-menu { position: relative; min-width: 0; }
.appearance-menu summary { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; align-items: center; gap: 9px; min-height: 48px; padding: 7px 10px; border: 1px solid var(--appearance-border); border-radius: var(--radius-sm); background: var(--appearance-trigger); color: var(--appearance-text); cursor: pointer; list-style: none; }
.appearance-menu summary::-webkit-details-marker { display: none; }
.appearance-menu summary:hover { background: var(--appearance-hover); }
.appearance-icon { display: grid; width: 29px; height: 29px; place-items: center; border: 1px solid var(--appearance-border); border-radius: var(--radius-sm); font-size: 15px; }
.appearance-copy { min-width: 0; }
.appearance-copy small, .appearance-copy strong { display: block; }
.appearance-copy small { color: var(--appearance-muted); font-size: 9px; letter-spacing: .04em; }
.appearance-copy strong { margin-top: 3px; font-size: 11px; }
.appearance-chevron { color: var(--appearance-muted); font-size: 11px; transition: transform .16s ease; }
.appearance-menu[open] .appearance-chevron { transform: rotate(180deg); }
.appearance-options { position: absolute; z-index: 60; bottom: calc(100% + 8px); left: 0; width: 236px; overflow: hidden; padding: 6px; border: 1px solid var(--popover-border); border-radius: var(--radius-md); background: var(--popover-bg); color: var(--ink); box-shadow: var(--popover-shadow); }
.appearance-heading { padding: 7px 8px 8px; color: var(--ink-soft); font-size: 10px; font-weight: 650; }
.appearance-options button { display: grid; grid-template-columns: 36px minmax(0, 1fr) 16px; align-items: center; gap: 10px; width: 100%; padding: 9px 8px; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--ink); cursor: pointer; text-align: left; }
.appearance-options button:hover, .appearance-options button[aria-checked="true"] { background: var(--control-hover); }
.appearance-options button > span:nth-child(2) { min-width: 0; }
.appearance-options button strong, .appearance-options button small { display: block; }
.appearance-options button strong { font-size: 12px; }
.appearance-options button small { margin-top: 3px; color: var(--ink-soft); font-size: 9px; }
.appearance-options button em { color: var(--cobalt); font: normal 700 13px var(--sans); }
.theme-preview { position: relative; display: block; width: 35px; height: 27px; overflow: hidden; border: 1px solid #c6c6c8; border-radius: 4px; background: #fff; }
.theme-preview i { position: absolute; inset: 0 auto 0 0; width: 9px; background: #f0f0f2; }
.theme-preview b { position: absolute; left: 13px; right: 4px; top: 7px; height: 4px; border-radius: 2px; background: #1672d4; box-shadow: 0 7px 0 #e8e8ea; }
.professional-preview { border-color: #17211f; background: #f8f4ea; }
.professional-preview i { background: #17211f; }
.professional-preview b { border-radius: 0; background: #1748b5; box-shadow: 0 7px 0 #dfd5c1; }

.masthead-controls .appearance-options { right: 0; bottom: auto; left: auto; top: calc(100% + 8px); }

@media (max-width: 760px) {
  .appearance-options { width: 226px; }
}
</style>
