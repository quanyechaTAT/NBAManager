<template>
  <div class="sports-header">
    <div class="sports-header__content">
      <div class="sports-header__text">
        <p v-if="eyebrow" class="sports-header__eyebrow">{{ eyebrow }}</p>
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
  /** 眉标 */
  eyebrow?: string
}

withDefaults(defineProps<Props>(), {
  subtitle: '',
  eyebrow: ''
})
</script>

<style scoped>
.sports-header {
  margin-bottom: 28px;
  position: relative;
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

.sports-header__eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--accent);
  opacity: 0.9;
}

.sports-header__title {
  font-family: var(--font-heading);
  font-size: clamp(24px, 2.4vw, 32px);
  font-weight: 800;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.6px;
  line-height: 1.15;
  background: linear-gradient(120deg, var(--text-primary) 0%, var(--text-primary) 62%, rgba(34, 211, 238, 0.92) 160%);
  -webkit-background-clip: text;
  background-clip: text;
}

@supports not ((-webkit-background-clip: text) or (background-clip: text)) {
  .sports-header__title {
    color: var(--text-primary);
    background: none;
  }
}

.sports-header__subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 8px 0 0;
  line-height: 1.55;
  max-width: 56ch;
}

.sports-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 4px;
}

@media (max-width: 640px) {
  .sports-header__content {
    flex-direction: column;
    align-items: stretch;
  }

  .sports-header__actions {
    justify-content: flex-start;
  }
}
</style>
