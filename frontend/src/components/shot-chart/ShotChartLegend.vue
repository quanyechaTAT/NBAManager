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
    <div class="legend-total" v-if="total > 0">共 {{ total }} 次出手</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ total: number }>()
</script>

<style scoped>
.legend-panel {
  display: flex; flex-direction: column; gap: 10px; padding: 20px;
  background: rgba(17, 24, 39, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
}
.legend-title { font-size: 12px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 1.5px; }
.legend-items { display: flex; gap: 20px; }
.legend-item { display: flex; align-items: center; gap: 8px; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }
.dot-made { background: #00E676; box-shadow: 0 0 6px rgba(0,230,118,0.4); }
.dot-missed { background: #FF5252; box-shadow: 0 0 6px rgba(255,82,82,0.4); }
.legend-label { font-size: 12px; color: #94a3b8; }
.legend-total { font-size: 11px; color: #475569; }

/* ===== 亮色主题 ===== */
[data-theme="light"] .legend-panel {
  background: rgba(0, 0, 0, 0.02);
  border-color: rgba(0, 0, 0, 0.08);
}
[data-theme="light"] .legend-title { color: #475569; }
[data-theme="light"] .legend-label { color: #475569; }
[data-theme="light"] .legend-total { color: #64748b; }
</style>
