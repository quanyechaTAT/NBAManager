<template>
  <Teleport to="body">
    <div v-if="visible" class="emoji-backdrop" @click="$emit('close')"></div>
    <div v-if="visible" class="emoji-picker" :style="pos">
      <div class="emoji-picker-header">
        <span class="emoji-picker-title">选择表情</span>
        <button class="emoji-close" @click="$emit('close')">✕</button>
      </div>
      <div class="emoji-search">
        <input v-model="search" placeholder="搜索表情..." class="emoji-search-input" />
      </div>
      <div class="emoji-grid">
        <div v-for="group in filtered" :key="group.name" class="emoji-group">
          <div class="emoji-group-title">{{ group.name }}</div>
          <div class="emoji-items">
            <button v-for="e in group.items" :key="e.char" class="emoji-item" :title="e.name" @click="select(e.char)">{{ e.char }}</button>
          </div>
        </div>
        <div v-if="filtered.length === 0" class="emoji-empty">未找到匹配的表情</div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
const props = defineProps<{ visible: boolean; anchor?: HTMLElement | null }>()
const emit = defineEmits<{ (e: 'select', emoji: string): void; (e: 'close'): void }>()
const search = ref('')

const pos = computed(() => {
  if (props.anchor) {
    const r = props.anchor.getBoundingClientRect()
    return { top: `${r.bottom + 4}px`, left: `${r.left}px` }
  }
  return { bottom: '50px', left: '0' }
})

const groups = [
  { name: '🏀 篮球', items: [
    { char: '🏀', name: '篮球' }, { char: '⛹️', name: '打球' }, { char: '🏆', name: '奖杯' },
    { char: '🥇', name: '金牌' }, { char: '🥈', name: '银牌' }, { char: '🥉', name: '铜牌' },
    { char: '🎯', name: '命中' }, { char: '🔥', name: '火热' }, { char: '⚡', name: '闪电' },
    { char: '💥', name: '爆发' }, { char: '💪', name: '力量' }, { char: '🙌', name: '庆祝' },
  ]},
  { name: '😀 表情', items: [
    { char: '😀', name: '开心' }, { char: '😂', name: '笑哭' }, { char: '🤣', name: '笑翻' },
    { char: '😊', name: '微笑' }, { char: '😍', name: '喜爱' }, { char: '🤔', name: '思考' },
    { char: '😤', name: '生气' }, { char: '😢', name: '难过' }, { char: '😱', name: '震惊' },
    { char: '🥳', name: '庆祝' }, { char: '🤯', name: '爆炸头' }, { char: '🤡', name: '小丑' },
  ]},
  { name: '👍 手势', items: [
    { char: '👍', name: '点赞' }, { char: '👎', name: '踩' }, { char: '👏', name: '鼓掌' },
    { char: '🙌', name: '举手' }, { char: '🤝', name: '握手' }, { char: '✌️', name: '胜利' },
    { char: '🤞', name: '祈祷' }, { char: '💪', name: '加油' }, { char: '👀', name: '看' },
    { char: '🫡', name: '敬礼' }, { char: '🫠', name: '融化' }, { char: '🫣', name: '偷看' },
  ]},
  { name: '❤️ 爱心', items: [
    { char: '❤️', name: '红心' }, { char: '🧡', name: '橙心' }, { char: '💛', name: '黄心' },
    { char: '💚', name: '绿心' }, { char: '💙', name: '蓝心' }, { char: '💜', name: '紫心' },
    { char: '💯', name: '满分' }, { char: '✨', name: '闪亮' }, { char: '🌟', name: '星星' },
  ]},
  { name: '🎉 派对', items: [
    { char: '🎉', name: '派对' }, { char: '🎊', name: '彩球' }, { char: '🎈', name: '气球' },
    { char: '🎁', name: '礼物' }, { char: '🎂', name: '蛋糕' }, { char: '🎆', name: '烟花' },
    { char: '⭐', name: '星' }, { char: '💡', name: '灯泡' }, { char: '📰', name: '新闻' },
  ]},
]
const filtered = computed(() => {
  if (!search.value.trim()) return groups
  const q = search.value.toLowerCase()
  return groups.map(g => ({ ...g, items: g.items.filter(e => e.name.includes(q)) })).filter(g => g.items.length > 0)
})
function select(c: string) { emit('select', c) }
</script>

<style scoped>
.emoji-backdrop { position: fixed; inset: 0; z-index: 999; }
.emoji-picker { position: fixed; width: 300px; max-height: 340px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: 10px; box-shadow: 0 6px 24px rgba(0,0,0,0.18); z-index: 1000; overflow: hidden; display: flex; flex-direction: column; }
.emoji-picker-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-bottom: 1px solid var(--border-light); }
.emoji-picker-title { font-size: 12px; font-weight: 600; color: var(--text-primary); }
.emoji-close { background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 13px; padding: 2px 5px; border-radius: 4px; }
.emoji-close:hover { background: var(--bg-hover); }
.emoji-search { padding: 6px 12px; border-bottom: 1px solid var(--border-light); }
.emoji-search-input { width: 100%; padding: 5px 8px; border: 1px solid var(--border-light); border-radius: 5px; background: var(--bg-input); color: var(--text-primary); font-size: 12px; outline: none; }
.emoji-search-input:focus { border-color: var(--accent); }
.emoji-grid { flex: 1; overflow-y: auto; padding: 6px 8px; }
.emoji-group { margin-bottom: 6px; }
.emoji-group-title { font-size: 10px; color: var(--text-dim); font-weight: 600; margin-bottom: 3px; }
.emoji-items { display: flex; flex-wrap: wrap; gap: 1px; }
.emoji-item { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-size: 18px; border: none; background: none; border-radius: 5px; cursor: pointer; transition: background 0.12s, transform 0.12s; }
.emoji-item:hover { background: var(--bg-hover); transform: scale(1.15); }
.emoji-empty { text-align: center; padding: 16px; color: var(--text-dim); font-size: 12px; }
[data-theme="light"] .emoji-picker { background: #fff; border-color: #E8E5E0; box-shadow: 0 6px 24px rgba(0,0,0,0.08); }
[data-theme="light"] .emoji-search-input { background: #F5F3F0; border-color: #E8E5E0; }
[data-theme="light"] .emoji-item:hover { background: #FAF9F7; }
</style>
