<template>
  <div
    class="metric-card"
    :class="[`metric-card--${color}`, { 'metric-card--clickable': clickable }]"
    @click="handleClick"
  >
    <div class="metric-card__header">
      <div class="metric-card__icon" :style="iconStyle">
        <span v-if="icon" class="metric-card__icon-text">{{ icon }}</span>
      </div>
      <div
        v-if="trend && trendValue"
        class="metric-card__trend"
        :class="`metric-card__trend--${trend}`"
      >
        <span class="metric-card__trend-arrow">{{ trend === 'up' ? '↑' : '↓' }}</span>
        <span>{{ trendValue }}</span>
      </div>
    </div>
    <div class="metric-card__value">{{ displayValue }}</div>
    <div class="metric-card__label">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  /** 指标标签 */
  label: string
  /** 指标数值 */
  value: string | number
  /** 图标（emoji 或文本） */
  icon?: string
  /** 趋势方向：up 或 down */
  trend?: 'up' | 'down' | null
  /** 趋势数值（如 +12.5%） */
  trendValue?: string
  /** 颜色主题：accent / purple / cyan / gold / green / danger */
  color?: 'accent' | 'purple' | 'cyan' | 'gold' | 'green' | 'danger'
  /** 是否可点击 */
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  icon: '',
  trend: null,
  trendValue: '',
  color: 'accent',
  clickable: false
})

const emit = defineEmits<{
  (e: 'click'): void
}>()

const displayValue = computed(() => {
  if (typeof props.value === 'number') {
    return props.value.toLocaleString()
  }
  return props.value
})

const iconStyle = computed(() => {
  const colorMap: Record<string, string> = {
    accent: 'var(--accent)',
    purple: 'var(--purple)',
    cyan: 'var(--cyan)',
    gold: 'var(--gold)',
    green: 'var(--green)',
    danger: 'var(--danger)'
  }
  return {
    background: `${colorMap[props.color] || colorMap.accent}20`,
    color: colorMap[props.color] || colorMap.accent
  }
})

function handleClick() {
  if (props.clickable) {
    emit('click')
  }
}
</script>

<style scoped>
.metric-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 20px;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  overflow: hidden;
}

.metric-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}

.metric-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--border-medium);
  transform: translateY(-2px);
}

.metric-card:hover::before {
  opacity: 1;
}

.metric-card--clickable {
  cursor: pointer;
}

/* 颜色变体 */
.metric-card--accent::before { background: linear-gradient(90deg, transparent, var(--accent), transparent); }
.metric-card--purple::before { background: linear-gradient(90deg, transparent, var(--purple), transparent); }
.metric-card--cyan::before { background: linear-gradient(90deg, transparent, var(--cyan), transparent); }
.metric-card--gold::before { background: linear-gradient(90deg, transparent, var(--gold), transparent); }
.metric-card--green::before { background: linear-gradient(90deg, transparent, var(--green), transparent); }
.metric-card--danger::before { background: linear-gradient(90deg, transparent, var(--danger), transparent); }

.metric-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.metric-card__icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}

.metric-card__icon-text {
  font-size: 20px;
}

.metric-card__trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.metric-card__trend--up {
  color: var(--success);
  background: rgba(34, 197, 94, 0.1);
}

.metric-card__trend--down {
  color: var(--danger);
  background: rgba(239, 68, 68, 0.1);
}

.metric-card__trend-arrow {
  font-size: 10px;
}

.metric-card__value {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
  margin-bottom: 4px;
}

.metric-card__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>
