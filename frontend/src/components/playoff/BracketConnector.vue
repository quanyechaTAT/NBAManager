<template>
  <div class="bracket-connector">
    <svg width="20" :height="height" class="connector-svg">
      <line
        v-for="i in count"
        :key="i"
        :x1="direction === 'right' ? 0 : 20"
        :y1="getLineY(i)"
        :x2="direction === 'right' ? 20 : 0"
        :y2="getLineY(i)"
        class="connector-line"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
interface Props {
  count: number
  direction?: 'left' | 'right'
  height?: number
}

const props = withDefaults(defineProps<Props>(), {
  direction: 'right',
  height: 240
})

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
  width: 20px;
}

.connector-svg {
  overflow: visible;
}

.connector-line {
  stroke: var(--border-light, #e5e7eb);
  stroke-width: 2;
  stroke-dasharray: 3,3;
}
</style>
