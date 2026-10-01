import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const THEME_KEY = 'nba_theme'

export const useThemeStore = defineStore('theme', () => {
  const prefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches
  const stored = localStorage.getItem(THEME_KEY) as 'dark' | 'light' | null
  const theme = ref<'dark' | 'light'>(stored ?? (prefersLight ? 'light' : 'dark'))

  function applyTheme(t: 'dark' | 'light') {
    document.documentElement.setAttribute('data-theme', t)
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  watch(theme, (t) => {
    localStorage.setItem(THEME_KEY, t)
    applyTheme(t)
  }, { immediate: true })

  return { theme, toggleTheme }
})
