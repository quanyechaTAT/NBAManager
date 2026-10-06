<template>
  <div class="composer">
    <!-- 快捷助威话术栏 (Quick Cheer Presets) -->
    <div class="quick-chants-strip">
      <span class="chants-label">快捷喊话:</span>
      <div class="chants-scroll">
        <button
          v-for="chant in availableChants"
          :key="chant"
          type="button"
          class="chant-pill"
          :disabled="!connected || sendLoading || cooldownSeconds > 0"
          @click="insertChant(chant)"
        >
          {{ chant }}
        </button>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="composer-input-wrap">
      <textarea
        ref="textareaRef"
        v-model="text"
        class="composer-textarea"
        :placeholder="placeholderText"
        :disabled="!connected"
        maxlength="500"
        rows="2"
        @keydown.enter.exact.prevent="handleSend"
        @input="autoResize"
      ></textarea>
    </div>

    <!-- 底部工具栏 -->
    <div class="composer-toolbar">
      <div class="toolbar-left">
        <!-- 表情选择器按钮 -->
        <button
          ref="emojiBtnRef"
          type="button"
          class="tool-btn emoji-tool-btn"
          title="插入表情"
          :disabled="!connected"
          @click="openEmojiPicker"
        >
          <svg class="emoji-svg-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M8 14s1.5 2 4 2 4-2 4-2" />
            <line x1="9" y1="9" x2="9.01" y2="9" />
            <line x1="15" y1="9" x2="15.01" y2="9" />
          </svg>
          <span class="emoji-tool-text">表情</span>
        </button>

        <span v-if="cooldownSeconds > 0" class="cooldown-badge">
          <span class="cooldown-spinner"></span>
          发言冷却 {{ cooldownSeconds }}s
        </span>
      </div>

      <div class="toolbar-right">
        <span class="char-count" :class="{ 'is-limit': text.length > 450 }">
          {{ text.length }} / 500
        </span>
        <button
          type="button"
          class="send-btn"
          :class="{ 'is-cooling': cooldownSeconds > 0 }"
          :disabled="!canSend"
          @click="handleSend"
        >
          <template v-if="sendLoading">
            <span class="send-spinner"></span>
            <span>发送中</span>
          </template>
          <template v-else-if="cooldownSeconds > 0">
            <span class="cooldown-num">{{ cooldownSeconds }}s</span>
            <span>冷却中</span>
          </template>
          <template v-else>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
            <span>发送</span>
          </template>
        </button>
      </div>
    </div>

    <!-- 表情选择器弹出层 -->
    <EmojiPicker
      :visible="emojiPickerVisible"
      :anchor="emojiBtnRef"
      @select="onEmojiSelect"
      @close="emojiPickerVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted } from 'vue'
import EmojiPicker from '@/components/EmojiPicker.vue'

const props = withDefaults(
  defineProps<{
    connected: boolean
    sendLoading: boolean
    homeTeam?: string
    awayTeam?: string
  }>(),
  {
    homeTeam: '',
    awayTeam: '',
  }
)

const emit = defineEmits<{ send: [content: string] }>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const emojiBtnRef = ref<HTMLButtonElement | null>(null)
const emojiPickerVisible = ref(false)

// 发言冷却时间（秒）
const cooldownSeconds = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

const COOLDOWN_DURATION = 3 // 3秒冷却

function startCooldown() {
  cooldownSeconds.value = COOLDOWN_DURATION
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldownSeconds.value -= 1
    if (cooldownSeconds.value <= 0) {
      if (cooldownTimer) clearInterval(cooldownTimer)
      cooldownTimer = null
      cooldownSeconds.value = 0
    }
  }, 1000)
}

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

// 快捷助威话术列表
const availableChants = computed(() => {
  const list: string[] = []
  if (props.awayTeam) list.push(`${props.awayTeam}加油！`)
  if (props.homeTeam) list.push(`${props.homeTeam}必胜！`)
  list.push(
    '漂亮的三分！',
    '手感发烫！',
    '铁血防守！',
    '百步穿杨！',
    '防守反击！',
    '关键决胜时刻！'
  )
  return list
})

const canSend = computed(() => {
  return (
    props.connected &&
    text.value.trim().length > 0 &&
    !props.sendLoading &&
    cooldownSeconds.value === 0
  )
})

const placeholderText = computed(() => {
  if (!props.connected) return '正在连接观赛直播间...'
  if (cooldownSeconds.value > 0) return `发言冷却中 (${cooldownSeconds.value}s)...`
  return '输入观赛评论，Enter 发送，Shift+Enter 换行（最多500字）...'
})

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function handleSend() {
  if (!canSend.value) return
  const content = text.value.trim()
  if (!content) return

  emit('send', content)
  text.value = ''
  startCooldown()

  nextTick(() => {
    autoResize()
    textareaRef.value?.focus()
  })
}

function insertChant(chant: string) {
  if (!props.connected || props.sendLoading || cooldownSeconds.value > 0) return
  // 直接以该助威语发送，提供更爽快的即时互动体验
  emit('send', chant)
  startCooldown()
}

function openEmojiPicker() {
  emojiPickerVisible.value = !emojiPickerVisible.value
}

function onEmojiSelect(emoji: string) {
  const el = textareaRef.value
  if (el) {
    const start = el.selectionStart ?? text.value.length
    const end = el.selectionEnd ?? text.value.length
    text.value = text.value.slice(0, start) + emoji + text.value.slice(end)
    nextTick(() => {
      el.focus()
      const newPos = start + emoji.length
      el.setSelectionRange(newPos, newPos)
      autoResize()
    })
  } else {
    text.value += emoji
    nextTick(() => autoResize())
  }
}
</script>

<style scoped>
.composer {
  margin: 0 16px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-medium);
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: border-color var(--duration-fast) var(--ease-smooth);
}

.composer:focus-within {
  border-color: var(--accent, #0891B2);
  box-shadow: 0 0 0 2px var(--accent-lighter, rgba(8, 145, 178, 0.15)), 0 4px 16px rgba(0, 0, 0, 0.12);
}

/* 快捷喊话条 */
.quick-chants-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.chants-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-dim, #94a3b8);
  white-space: nowrap;
}

.chants-scroll {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
}

.chants-scroll::-webkit-scrollbar {
  display: none;
}

.chant-pill {
  padding: 3px 9px;
  border-radius: 9999px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.12s ease;
  flex-shrink: 0;
}

.chant-pill:hover:not(:disabled) {
  background: var(--accent-lighter, rgba(8, 145, 178, 0.15));
  border-color: var(--accent, #0891B2);
  color: var(--accent, #0891B2);
  transform: translateY(-1px);
}

.chant-pill:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 输入框包装 */
.composer-input-wrap {
  padding: 10px 14px 4px;
}

.composer-textarea {
  width: 100%;
  min-height: 44px;
  max-height: 120px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  resize: none;
  outline: none;
  box-sizing: border-box;
}

.composer-textarea::placeholder {
  color: var(--text-dim);
}

.composer-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 工具栏 */
.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 10px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.emoji-tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.12s ease;
}

.emoji-tool-btn:hover:not(:disabled) {
  background: var(--accent-lighter);
  border-color: var(--accent);
  color: var(--accent);
}

.emoji-tool-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.emoji-tool-icon {
  font-size: 14px;
}

.emoji-tool-text {
  font-size: 12px;
  font-weight: 600;
}

.cooldown-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 700;
  color: #F59E0B;
  background: rgba(245, 158, 11, 0.12);
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.cooldown-spinner {
  width: 9px;
  height: 9px;
  border: 1.5px solid #F59E0B;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.char-count {
  font-size: 11px;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
}

.char-count.is-limit {
  color: #EF4444;
  font-weight: 700;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: var(--accent, #0891B2);
  color: var(--accent-contrast, #FFFFFF);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.14s ease;
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.12);
  transform: translateY(-1px);
}

.send-btn:active:not(:disabled) {
  transform: translateY(0) scale(0.98);
}

.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.send-btn.is-cooling {
  background: #475569;
  color: #CBD5E1;
}

.cooldown-num {
  font-size: 11px;
  font-weight: 800;
  background: rgba(0, 0, 0, 0.25);
  padding: 1px 5px;
  border-radius: 4px;
}

.send-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
