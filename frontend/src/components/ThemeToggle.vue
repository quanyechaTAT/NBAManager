<template>
  <button
    type="button"
    class="theme-toggle"
    @click="toggleTheme"
    :title="theme === 'dark' ? '切换到亮色主题' : '切换到暗色主题'"
    :aria-label="theme === 'dark' ? '切换到亮色主题' : '切换到暗色主题'"
  >
    <span class="theme-toggle__orb" aria-hidden="true">
      <!-- 暗色显示月亮，亮色显示太阳 -->
      <svg
        class="theme-toggle__icon theme-toggle__icon--moon"
        :class="{ 'is-active': theme === 'dark' }"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        width="16"
        height="16"
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
      </svg>
      <svg
        class="theme-toggle__icon"
        :class="{ 'is-active': theme === 'light' }"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        width="16"
        height="16"
      >
        <circle cx="12" cy="12" r="5"/>
        <line x1="12" y1="1" x2="12" y2="3"/>
        <line x1="12" y1="21" x2="12" y2="23"/>
        <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
        <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
        <line x1="1" y1="12" x2="3" y2="12"/>
        <line x1="21" y1="12" x2="23" y2="12"/>
        <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
        <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
      </svg>
    </span>
  </button>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import { storeToRefs } from 'pinia'

const store = useThemeStore()
const { theme } = storeToRefs(store)
const { toggleTheme } = store
</script>

<style scoped>
.theme-toggle {
  appearance: none;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  color: var(--text-muted);
  width: 36px;
  height: 36px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition:
    color var(--duration-nav, 0.18s) var(--ease-smooth),
    border-color var(--duration-nav, 0.18s) var(--ease-smooth),
    background var(--duration-nav, 0.18s) var(--ease-smooth),
    box-shadow var(--duration-nav, 0.18s) var(--ease-smooth),
    transform var(--duration-nav, 0.18s) var(--ease-out-quart, ease);
}

.theme-toggle:hover {
  color: var(--accent);
  border-color: rgba(34, 211, 238, 0.28);
  background: var(--accent-lighter);
  box-shadow: 0 6px 18px rgba(34, 211, 238, 0.12);
  transform: translateY(-1px);
}

.theme-toggle:active {
  transform: scale(0.96);
}

.theme-toggle__orb {
  position: relative;
  width: 16px;
  height: 16px;
  display: block;
}

.theme-toggle__icon {
  position: absolute;
  inset: 0;
  opacity: 0;
  transform: rotate(-40deg) scale(0.7);
  transition:
    opacity 0.22s var(--ease-out-quart, ease),
    transform 0.28s var(--ease-out-expo, ease);
}

.theme-toggle__icon.is-active {
  opacity: 1;
  transform: rotate(0deg) scale(1);
}
</style>
