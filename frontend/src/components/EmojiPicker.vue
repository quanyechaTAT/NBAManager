<template>
  <Teleport to="body">
    <div v-if="visible" class="emoji-backdrop" :style="{ zIndex: pickerZIndex - 1 }" @click="$emit('close')"></div>
    <div
      v-if="visible"
      class="emoji-picker"
      :class="anchor ? 'emoji-picker--anchored' : 'emoji-picker--centered'"
      :style="pickerStyle"
      @keydown="onKeydown"
    >
      <!-- 简约专业顶栏 -->
      <div class="emoji-header">
        <span class="emoji-header-title">常用表情</span>
        <button type="button" class="emoji-close" title="关闭 (Esc)" @click="$emit('close')">✕</button>
      </div>

      <!-- 快捷高频助威条 -->
      <div class="emoji-quick-strip">
        <span class="quick-strip-label">高频:</span>
        <div class="quick-strip-items">
          <button
            v-for="e in quickReactions"
            :key="e.char"
            type="button"
            class="quick-emoji-btn"
            :title="e.name"
            @click="select(e.char)"
          >
            {{ e.char }}
          </button>
        </div>
      </div>

      <!-- 搜索框 -->
      <div class="emoji-search">
        <div class="search-input-wrap">
          <svg class="search-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            ref="searchRef"
            v-model="search"
            placeholder="搜索表情、术语..."
            class="emoji-search-input"
          />
          <button v-if="search" type="button" class="clear-search-btn" @click="search = ''">✕</button>
        </div>
      </div>

      <!-- 表情网格内容区 -->
      <div ref="gridRef" class="emoji-grid">
        <!-- 搜索结果 -->
        <template v-if="search.trim()">
          <div v-if="searchResults.length > 0" class="emoji-items">
            <button
              v-for="(e, i) in searchResults"
              :key="e.char"
              type="button"
              class="emoji-item"
              :class="{ highlighted: highlightIndex === i }"
              :title="e.name"
              @click="select(e.char)"
              @mouseenter="highlightIndex = i"
            >
              <span class="emoji-char">{{ e.char }}</span>
            </button>
          </div>
          <div v-else class="emoji-empty">未匹配到表情</div>
        </template>

        <!-- 默认展示精选 16 个经典表情 -->
        <template v-else>
          <div class="emoji-items">
            <button
              v-for="(e, i) in curatedEmojis"
              :key="e.char"
              type="button"
              class="emoji-item"
              :class="{ highlighted: highlightIndex === i }"
              :title="e.name"
              @click="select(e.char)"
              @mouseenter="highlightIndex = i"
            >
              <span class="emoji-char">{{ e.char }}</span>
            </button>
          </div>
        </template>
      </div>

      <!-- 底部辅助提示 -->
      <div class="emoji-footer">
        <span class="footer-tip">Enter 选定 · Esc 关闭</span>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'

export interface EmojiItem {
  char: string
  name: string
  en: string
}

const props = defineProps<{ visible: boolean; anchor?: HTMLElement | null }>()
const emit = defineEmits<{ (e: 'select', emoji: string): void; (e: 'close'): void }>()

const search = ref('')
const highlightIndex = ref(-1)
const searchRef = ref<HTMLInputElement | null>(null)
const gridRef = ref<HTMLElement | null>(null)

// 精选 16 个最经典的篮球竞技与高频情绪符号
const curatedEmojis: EmojiItem[] = [
  { char: '🏀', name: '篮球', en: 'basketball ball hoop' },
  { char: '🔥', name: '火热手感', en: 'fire hot flame lit on fire' },
  { char: '👍', name: '点赞好球', en: 'thumbs up good shot like' },
  { char: '👏', name: '鼓掌致敬', en: 'clap applause bravo respect' },
  { char: '🎯', name: '三分穿心/百步穿杨', en: 'target clutch sharp three pointer 3pt splash 三分' },
  { char: '💯', name: '满分神球', en: 'hundred 100 points perfect' },
  { char: '🏆', name: '总冠军奖杯', en: 'trophy champion cup' },
  { char: '👑', name: '球王MVP', en: 'crown king mvp best' },
  { char: '🐐', name: '历史最佳(GOAT)', en: 'goat greatest of all time' },
  { char: '🚫', name: '大火锅/盖帽', en: 'block rejection deny defense no 盖帽' },
  { char: '🧱', name: '打铁/空心铁', en: 'brick iron missed shot clank 空心铁' },
  { char: '⚡', name: '闪电快攻', en: 'lightning fast break speed' },
  { char: '💪', name: '强硬对抗', en: 'strong muscle and one flex' },
  { char: '🧊', name: '冷血大心脏', en: 'ice in veins clutch cold' },
  { char: '🤝', name: '体育握手', en: 'handshake sportsmanship respect' },
  { char: '😤', name: '昂扬斗志', en: 'triumph grit tough proud' },
]

// 快捷条只取前 8 个
const quickReactions: EmojiItem[] = curatedEmojis.slice(0, 8)

const searchResults = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return []
  return curatedEmojis.filter(e =>
    e.name.toLowerCase().includes(q) ||
    e.en.toLowerCase().includes(q) ||
    e.char === q
  )
})

const currentItems = computed(() => {
  return search.value.trim() ? searchResults.value : curatedEmojis
})

function select(char: string) {
  emit('select', char)
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  const items = currentItems.value
  if (!items.length) return

  if (e.key === 'Escape') {
    e.preventDefault()
    emit('close')
    return
  }

  if (e.key === 'ArrowRight') {
    e.preventDefault()
    highlightIndex.value = Math.min(highlightIndex.value + 1, items.length - 1)
    return
  }

  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    highlightIndex.value = Math.max(highlightIndex.value - 1, 0)
    return
  }

  const cols = 4
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    highlightIndex.value = Math.min(highlightIndex.value + cols, items.length - 1)
    return
  }

  if (e.key === 'ArrowUp') {
    e.preventDefault()
    highlightIndex.value = Math.max(highlightIndex.value - cols, 0)
    return
  }

  if (e.key === 'Enter') {
    e.preventDefault()
    if (highlightIndex.value >= 0 && highlightIndex.value < items.length) {
      select(items[highlightIndex.value].char)
    }
  }
}

// 弹窗定位与 Z-Index
const pickerZIndex = ref(3000)
const pickerStyle = ref<Record<string, string>>({})

function computePosition() {
  if (!props.anchor) {
    pickerStyle.value = {}
    return
  }
  const rect = props.anchor.getBoundingClientRect()
  const pickerW = 320
  const pickerH = 340
  const margin = 8

  let top = rect.top - pickerH - margin
  if (top < margin) {
    top = rect.bottom + margin
  }

  let left = rect.left + rect.width / 2 - pickerW / 2
  if (left + pickerW > window.innerWidth - margin) {
    left = window.innerWidth - pickerW - margin
  }
  if (left < margin) {
    left = margin
  }

  pickerStyle.value = {
    position: 'fixed',
    top: `${Math.round(top)}px`,
    left: `${Math.round(left)}px`,
    zIndex: `${pickerZIndex.value}`,
  }
}

watch(() => props.visible, (v) => {
  if (v) {
    search.value = ''
    highlightIndex.value = -1
    computePosition()
    window.addEventListener('resize', computePosition, { passive: true })
    window.addEventListener('scroll', computePosition, { passive: true, capture: true })
    nextTick(() => {
      searchRef.value?.focus()
    })
  } else {
    window.removeEventListener('resize', computePosition)
    window.removeEventListener('scroll', computePosition, true)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', computePosition)
  window.removeEventListener('scroll', computePosition, true)
})
</script>

<style scoped>
.emoji-backdrop {
  position: fixed;
  inset: 0;
  background: transparent;
}

.emoji-picker {
  background: var(--bg-card);
  border: 1px solid var(--border-medium);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.2), 0 2px 6px rgba(0, 0, 0, 0.08);
  width: 320px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  user-select: none;
  font-family: inherit;
}

.emoji-picker--centered {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 3000;
}

.emoji-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-light);
}

.emoji-header-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.emoji-close {
  border: none;
  background: transparent;
  color: var(--text-dim);
  font-size: 14px;
  line-height: 1;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.emoji-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.emoji-quick-strip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.quick-strip-label {
  font-size: 11px;
  color: var(--text-dim);
  font-weight: 500;
  flex-shrink: 0;
}

.quick-strip-items {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}
.quick-strip-items::-webkit-scrollbar {
  display: none;
}

.quick-emoji-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.12s ease, transform 0.12s ease;
}

.quick-emoji-btn:hover {
  background: var(--bg-card);
  transform: scale(1.1);
}

.emoji-search {
  padding: 8px 12px;
}

.search-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 9px;
  color: var(--text-dim);
  pointer-events: none;
}

.emoji-search-input {
  width: 100%;
  height: 30px;
  padding: 0 26px 0 28px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-input, var(--bg-hover));
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  transition: border-color 0.15s ease;
}

.emoji-search-input:focus {
  border-color: var(--accent, #0891b2);
}

.clear-search-btn {
  position: absolute;
  right: 6px;
  border: none;
  background: transparent;
  color: var(--text-dim);
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
}

.emoji-grid {
  padding: 6px 12px 12px;
  min-height: 160px;
  max-height: 220px;
  overflow-y: auto;
}

.emoji-items {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.emoji-item {
  height: 44px;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  border-radius: 8px;
  font-size: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease, transform 0.12s ease;
}

.emoji-item:hover,
.emoji-item.highlighted {
  background: var(--accent-lighter);
  border-color: var(--accent, #0891b2);
  transform: translateY(-1px);
}

.emoji-empty {
  padding: 32px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-dim);
}

.emoji-footer {
  padding: 6px 12px;
  background: var(--bg-hover);
  border-top: 1px solid var(--border-light);
  display: flex;
  justify-content: flex-end;
}

.footer-tip {
  font-size: 11px;
  color: var(--text-dim);
}
</style>
