<template>
  <div class="empty-state">
    <div class="empty-state__icon">
      <span v-if="icon">{{ icon }}</span>
      <svg v-else xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z" />
        <path d="M16 7V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v3" />
      </svg>
    </div>
    <h3 class="empty-state__title">{{ title }}</h3>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <div v-if="$slots.actions" class="empty-state__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 图标（emoji 或文本） */
  icon?: string
  /** 标题 */
  title: string
  /** 描述文本 */
  description?: string
}

withDefaults(defineProps<Props>(), {
  icon: '',
  description: ''
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
}

.empty-state__icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  color: var(--text-muted);
}

.empty-state__icon span {
  font-size: 32px;
}

.empty-state__icon svg {
  width: 32px;
  height: 32px;
}

.empty-state__title {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.empty-state__description {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
  max-width: 320px;
  line-height: 1.5;
}

.empty-state__actions {
  margin-top: 20px;
  display: flex;
  gap: 8px;
}
</style>
