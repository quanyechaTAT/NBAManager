<template>
  <div class="emoji-picker" v-if="visible">
    <div class="emoji-picker-header">
      <span class="emoji-picker-title">表情</span>
      <el-button link size="small" @click="$emit('close')">✕</el-button>
    </div>
    <div class="emoji-grid">
      <div v-for="emoji in emojis" :key="emoji" class="emoji-item" @click="selectEmoji(emoji)">
        {{ emoji }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'select', emoji: string): void
  (e: 'close'): void
}>()

// 抖音风格表情列表
const emojis = [
  // 基础表情
  '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '🙃',
  '😉', '😊', '😇', '🥰', '😍', '🤩', '😘', '😗', '😚', '😙',
  '🥲', '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🫢',
  '🤫', '🤔', '🫡', '🤐', '🤨', '😐', '😑', '😶', '🫥', '😏',
  '😒', '🙄', '😬', '🤥', '😌', '😔', '😪', '🤤', '😴', '😷',
  // 手势
  '👍', '👎', '👊', '✊', '🤛', '🤜', '👏', '🙌', '🫶', '👐',
  '🤲', '🤝', '🙏', '✌️', '🤞', '🫰', '🤟', '🤘', '👌', '🤌',
  '🤏', '👈', '👉', '👆', '🖕', '👇', '☝️', '🫵', '💪', '🦾',
  // 爱心
  '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔',
  '❤️‍🔥', '❤️‍🩹', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '💟',
  // 运动
  '🏀', '⚽', '🏈', '⚾', '🎾', '🏐', '🏉', '🎱', '🏓', '🏸',
  '🥊', '🥋', '🎯', '🏆', '🥇', '🥈', '🥉', '🏅', '🎖️', '🏵️',
  // 动物
  '🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼', '🐻‍❄️', '🐨',
  '🐯', '🦁', '🐮', '🐷', '🐸', '🐵', '🐔', '🐧', '🐦', '🦅',
  // 食物
  '🍕', '🍔', '🍟', '🌭', '🍿', '🧂', '🥓', '🥚', '🍳', '🧇',
  '🥞', '🧈', '🍞', '🥐', '🥨', '🧀', '🥗', '🥘', '🍲', '🍜',
  // 其他
  '🔥', '💯', '✨', '🌟', '💫', '⚡', '💥', '💨', '🕳️', '💣',
  '🎉', '🎊', '🎈', '🎁', '🎀', '🎂', '🍰', '🧁', '🍕', '🏀',
  '👑', '💎', '🔔', '📌', '🎵', '🎶', '🎤', '🎧', '🎸', '🎹',
]

function selectEmoji(emoji: string) {
  emit('select', emoji)
}
</script>

<style scoped>
.emoji-picker {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  width: 320px;
  max-height: 280px;
  overflow: hidden;
  z-index: 100;
}
.emoji-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-light);
}
.emoji-picker-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 2px;
  padding: 8px;
  max-height: 220px;
  overflow-y: auto;
}
.emoji-item {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-smooth);
}
.emoji-item:hover {
  background: var(--bg-hover);
  transform: scale(1.2);
}
</style>
