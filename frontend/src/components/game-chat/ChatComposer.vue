<template>
  <div class="composer">
    <div class="composer-input-wrap">
      <textarea
        ref="textareaRef"
        v-model="text"
        class="composer-textarea"
        :placeholder="connected ? '输入消息……（最多500字）' : '连接中...'"
        :disabled="!connected"
        maxlength="500"
        rows="2"
        @keydown.enter.exact.prevent="handleSend"
        @input="autoResize"
      ></textarea>
    </div>

    <div class="composer-toolbar">
      <div class="toolbar-left"></div>
      <div class="toolbar-right">
        <span class="char-count" :class="{ 'is-limit': text.length > 450 }">
          {{ text.length }} / 500
        </span>
        <button
          class="send-btn"
          :disabled="!canSend"
          @click="handleSend"
        >
          <svg v-if="!sendLoading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
          <span v-if="sendLoading" class="send-spinner"></span>
          <span>发送</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'

const props = defineProps<{
  connected: boolean
  sendLoading: boolean
}>()

const emit = defineEmits<{ send: [content: string] }>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const canSend = computed(() => props.connected && text.value.trim().length > 0 && !props.sendLoading)

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function handleSend() {
  if (!canSend.value) return
  emit('send', text.value)
  text.value = ''
  nextTick(() => autoResize())
}
</script>

<style scoped>
.composer {
  margin: 0 18px 18px;
  background: var(--chat-input-bg, var(--bg-input));
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
}

.composer-input-wrap {
  padding: 14px 16px 4px;
}

.composer-textarea {
  width: 100%;
  min-height: 44px;
  max-height: 120px;
  border: none;
  background: transparent;
  color: var(--chat-text, var(--text-primary));
  font-size: 14px;
  font-family: var(--font-body);
  line-height: 1.6;
  resize: none;
  outline: none;
}

.composer-textarea::placeholder {
  color: var(--chat-dim, var(--text-muted));
}

.composer-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 10px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tool-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  color: var(--chat-dim, var(--text-muted));
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.tool-btn:hover:not(:disabled) {
  background: var(--chat-hover, var(--bg-hover));
  color: var(--chat-text, var(--text-primary));
}

.tool-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.char-count {
  font-size: 12px;
  color: var(--chat-dim, var(--text-muted));
  font-variant-numeric: tabular-nums;
}

.char-count.is-limit {
  color: var(--danger);
}

.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 18px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--chat-accent, var(--accent));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.1);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
