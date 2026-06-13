<template>
  <div class="bracket-connector" :style="connectorStyle">
    <svg :width="width" :height="height" class="connector-svg">
      <line
        v-for="i in count"
        :key="i"
        :x1="direction === 'right' ? 0 : width"
        :y1="getLineY(i)"
        :x2="direction === 'right' ? width : 0"
        :y2="getLineY(i)"
        class="connector-line"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  count: number
  direction?: 'left' | 'right'
  width?: number
  height?: number
}

const props = withDefaults(defineProps<Props>(), {
  direction: 'right',
  width: 45,
  height: 300
})

const connectorStyle = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height}px`
}))

const getLineY = (index: number) => {
  const spacing = props.height / (props.count + 1)
  return spacing * index
}
</script>

<style scoped>
.bracket-connector {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.connector-svg {
  overflow: visible;
}

.connector-line {
  stroke: var(--border-light, #e5e7eb);
  stroke-width: 2;
  stroke-dasharray: 4,4;
}
</style>
