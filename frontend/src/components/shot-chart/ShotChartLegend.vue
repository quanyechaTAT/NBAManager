<template>
  <div class="legend-panel">
    <div class="legend-title">图例</div>
    <div class="legend-items">
      <div class="legend-item">
        <span class="legend-dot dot-made"></span>
        <span class="legend-label">命中</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot dot-missed"></span>
        <span class="legend-label">未中</span>
      </div>
    </div>
    <div class="legend-total" v-if="total > 0">
      共 {{ total }} 次出手
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ total: number }>()
</script>

<style scoped>
.legend-panel { display: flex; flex-direction: column; gap: 10px; padding: 16px; background: rgba(255,255,255,0.02); border: 1px solid rgba(139,92,246,0.12); border-radius: 12px; }
.legend-title { font-size: 12px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
.legend-items { display: flex; gap: 16px; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }
.dot-made { background: #00e676; box-shadow: 0 0 6px rgba(0,230,118,0.5); }
.dot-missed { background: #ff5252; box-shadow: 0 0 6px rgba(255,82,82,0.5); }
.legend-label { font-size: 12px; color: var(--text-secondary); }
.legend-total { font-size: 11px; color: var(--text-dim); }
</style>
