<template>
  <div class="app-empty" :class="`app-empty--${size}`">
    <p v-if="title" class="app-empty__title">{{ title }}</p>
    <p v-if="description" class="app-empty__description">{{ description }}</p>
    <div v-if="$slots.actions" class="app-empty__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  size?: 'sm' | 'md' | 'lg'
}>(), {
  title: '',
  description: '',
  size: 'md'
})
</script>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.app-empty--sm {
  padding: 24px 16px;
}

.app-empty--md {
  padding: 40px 24px;
}

.app-empty--lg {
  padding: 60px 24px;
}

.app-empty__title {
  font-family: var(--font-heading);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px;
}

.app-empty--sm .app-empty__title {
  font-size: 14px;
}

.app-empty--md .app-empty__title {
  font-size: 16px;
}

.app-empty--lg .app-empty__title {
  font-size: 18px;
}

.app-empty__description {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
  max-width: 320px;
  line-height: 1.5;
}

.app-empty--sm .app-empty__description {
  font-size: 13px;
}

.app-empty__actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
