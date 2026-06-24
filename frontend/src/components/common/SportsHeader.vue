<template>
  <div class="sports-header">
    <div class="sports-header__content">
      <div class="sports-header__text">
        <h1 class="sports-header__title">{{ title }}</h1>
        <p v-if="subtitle" class="sports-header__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="sports-header__actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 主标题 */
  title: string
  /** 副标题 */
  subtitle?: string
}

withDefaults(defineProps<Props>(), {
  subtitle: ''
})
</script>

<style scoped>
.sports-header {
  margin-bottom: 24px;
}

.sports-header__content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.sports-header__text {
  flex: 1;
  min-width: 0;
}

.sports-header__title {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.5px;
  line-height: 1.2;
}

.sports-header__subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 6px 0 0;
  line-height: 1.5;
}

.sports-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
