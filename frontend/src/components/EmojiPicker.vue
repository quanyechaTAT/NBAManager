<template>
  <Teleport to="body">
    <div v-if="visible" class="emoji-backdrop" :style="{ zIndex: pickerZIndex - 1 }" @click="$emit('close')"></div>
    <div
      v-if="visible"
      class="emoji-picker"
      :style="pickerStyle"
      @keydown="onKeydown"
    >
      <!-- Tab 栏 -->
      <div class="emoji-tabs">
        <button
          v-for="group in allGroups"
          :key="group.id"
          class="emoji-tab"
          :class="{ active: activeTab === group.id }"
          :title="group.name"
          @click="activeTab = group.id"
        >{{ group.icon }}</button>
        <span class="emoji-tabs-spacer"></span>
        <button class="emoji-close" @click="$emit('close')">✕</button>
      </div>

      <!-- 搜索框 -->
      <div class="emoji-search">
        <input
          ref="searchRef"
          v-model="search"
          placeholder="搜索（中文/英文/表情）..."
          class="emoji-search-input"
        />
      </div>

      <!-- 内容区 -->
      <div class="emoji-grid" ref="gridRef">
        <!-- 搜索模式 -->
        <template v-if="search.trim()">
          <div class="emoji-items">
            <button
              v-for="(e, i) in searchResults"
              :key="e.char"
              class="emoji-item"
              :class="{ highlighted: highlightIndex === i }"
              :title="`${e.name} ${e.en}`"
              @click="select(e.char)"
              @mouseenter="highlightIndex = i"
            >{{ e.char }}</button>
          </div>
          <div v-if="searchResults.length === 0" class="emoji-empty">未找到匹配的表情</div>
        </template>

        <!-- 分组模式 -->
        <template v-else>
          <!-- 最近使用 -->
          <div v-if="activeTab === 'recent' && recentEmojis.length === 0" class="emoji-empty">
            暂无使用记录
          </div>
          <div v-if="activeTab === 'recent' && recentEmojis.length > 0" class="emoji-group">
            <div class="emoji-group-title">最近使用</div>
            <div class="emoji-items">
              <button
                v-for="(e, i) in recentEmojis"
                :key="e.char"
                class="emoji-item"
                :class="{ highlighted: highlightIndex === i }"
                :title="`${e.name} ${e.en}`"
                @click="select(e.char)"
                @mouseenter="highlightIndex = i"
              >{{ e.char }}</button>
            </div>
          </div>

          <!-- 当前 Tab 分组 -->
          <div v-if="currentGroup" class="emoji-group">
            <div class="emoji-group-title">{{ currentGroup.name }}</div>
            <div class="emoji-items">
              <button
                v-for="(e, i) in currentGroup.items"
                :key="e.char"
                class="emoji-item"
                :class="{ highlighted: highlightIndex === i }"
                :title="`${e.name} ${e.en}`"
                @click="select(e.char)"
                @mouseenter="highlightIndex = i"
              >{{ e.char }}</button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'

interface EmojiItem {
  char: string
  name: string
  en: string
}

interface EmojiGroup {
  id: string
  name: string
  icon: string
  items: EmojiItem[]
}

const props = defineProps<{ visible: boolean; anchor?: HTMLElement | null }>()
const emit = defineEmits<{ (e: 'select', emoji: string): void; (e: 'close'): void }>()

const RECENT_KEY = 'nbamanager_emoji_recent'
const MAX_RECENT = 16

const search = ref('')
const activeTab = ref('recent')
const highlightIndex = ref(-1)
const searchRef = ref<HTMLInputElement | null>(null)
const gridRef = ref<HTMLElement | null>(null)

// ── 表情数据 ──
const groups: EmojiGroup[] = [
  {
    id: 'sports', name: '篮球', icon: '🏀',
    items: [
      { char: '🏀', name: '篮球', en: 'basketball ball sport' },
      { char: '⛹️', name: '打球', en: 'basketball player bouncing' },
      { char: '🏆', name: '奖杯', en: 'trophy champion award' },
      { char: '🥇', name: '金牌', en: 'gold medal first place' },
      { char: '🥈', name: '银牌', en: 'silver medal second place' },
      { char: '🥉', name: '铜牌', en: 'bronze medal third place' },
      { char: '🏅', name: '奖牌', en: 'medal sports' },
      { char: '🎯', name: '命中', en: 'target bullseye direct hit' },
      { char: '🔥', name: '火热', en: 'fire hot flame lit' },
      { char: '⚡', name: '闪电', en: 'lightning bolt zap thunder' },
      { char: '💥', name: '爆发', en: 'boom explosion collision' },
      { char: '💪', name: '力量', en: 'strong muscle bicep flex' },
      { char: '🙌', name: '庆祝', en: 'celebration hooray raising hands' },
      { char: '📣', name: '喇叭', en: 'megaphone cheer loudspeaker' },
      { char: '⏱️', name: '计时', en: 'stopwatch timer clock' },
      { char: '📊', name: '数据', en: 'stats chart bar graph data' },
      { char: '🏟️', name: '体育场', en: 'stadium arena' },
      { char: '🎖️', name: '勋章', en: 'medal decoration' },
      { char: '🥇', name: '冠军', en: 'champion winner first' },
      { char: '⛹️‍♀️', name: '女球员', en: 'woman basketball player' },
    ]
  },
  {
    id: 'smileys', name: '表情', icon: '😀',
    items: [
      { char: '😀', name: '开心', en: 'happy grin smile' },
      { char: '😃', name: '哈哈', en: 'happy open mouth smile' },
      { char: '😄', name: '大笑', en: 'smile laugh happy eyes' },
      { char: '😁', name: '咧嘴', en: 'grin beam' },
      { char: '😆', name: '眯眼笑', en: 'laughing squinting' },
      { char: '😅', name: '苦笑', en: 'sweat smile nervous' },
      { char: '🤣', name: '笑翻', en: 'rofl rolling floor laughing' },
      { char: '😂', name: '笑哭', en: 'tears joy laugh cry' },
      { char: '🙂', name: '微笑', en: 'slight smile' },
      { char: '😊', name: '害羞笑', en: 'blush shy smile' },
      { char: '😇', name: '天使', en: 'angel halo innocent' },
      { char: '🥰', name: '喜爱', en: 'love hearts smiling' },
      { char: '😍', name: '心动', en: 'heart eyes love crush' },
      { char: '🤩', name: '星星眼', en: 'star eyes wow amazed' },
      { char: '😘', name: '飞吻', en: 'kiss wink love' },
      { char: '😜', name: '吐舌', en: 'tongue wink playful' },
      { char: '🤔', name: '思考', en: 'thinking hmm consider' },
      { char: '🤨', name: '怀疑', en: 'skeptical raised eyebrow' },
      { char: '😐', name: '无语', en: 'neutral expressionless' },
      { char: '😑', name: '面无表情', en: 'expressionless blank' },
      { char: '😶', name: '沉默', en: 'silent no mouth' },
      { char: '😏', name: '得意', en: 'smirk sly' },
      { char: '😒', name: '不爽', en: 'unamused annoyed' },
      { char: '🙄', name: '翻白眼', en: 'eye roll whatever' },
      { char: '😬', name: '尴尬', en: 'grimace awkward' },
      { char: '😮‍💨', name: '叹气', en: 'exhale sigh relief' },
      { char: '😤', name: '生气', en: 'angry triumph mad' },
      { char: '😠', name: '愤怒', en: 'angry mad furious' },
      { char: '😡', name: '暴怒', en: 'rage red angry mad' },
      { char: '🤬', name: '骂人', en: 'swearing cursing angry' },
      { char: '😈', name: '恶魔', en: 'devil evil horns' },
      { char: '👿', name: '坏蛋', en: 'imp evil angry devil' },
      { char: '💀', name: '骷髅', en: 'skull dead death' },
      { char: '💩', name: '便便', en: 'poop poo crap' },
      { char: '🤡', name: '小丑', en: 'clown fool' },
      { char: '👻', name: '幽灵', en: 'ghost spooky boo' },
      { char: '👽', name: '外星人', en: 'alien ufo' },
      { char: '🤖', name: '机器人', en: 'robot bot' },
      { char: '😺', name: '猫咪笑', en: 'cat smile happy' },
      { char: '😸', name: '猫咪开心', en: 'cat grin happy' },
      { char: '😹', name: '猫咪笑哭', en: 'cat tears joy' },
      { char: '😻', name: '猫咪爱心', en: 'cat heart eyes love' },
      { char: '🙀', name: '猫咪震惊', en: 'cat shocked wow' },
      { char: '😿', name: '猫咪哭', en: 'cat crying sad' },
      { char: '😾', name: '猫咪生气', en: 'cat angry mad' },
    ]
  },
  {
    id: 'gestures', name: '手势', icon: '👍',
    items: [
      { char: '👍', name: '点赞', en: 'thumbs up like approve' },
      { char: '👎', name: '踩', en: 'thumbs down dislike' },
      { char: '👏', name: '鼓掌', en: 'clap applause bravo' },
      { char: '🙌', name: '举手', en: 'raising hands hooray' },
      { char: '🤝', name: '握手', en: 'handshake deal agree' },
      { char: '✌️', name: '胜利', en: 'peace victory' },
      { char: '🤞', name: '祈祷', en: 'fingers crossed hope luck' },
      { char: '👐', name: '张开手', en: 'open hands jazz' },
      { char: '🤲', name: '捧手', en: 'palms up together' },
      { char: '👋', name: '挥手', en: 'wave hello hi bye' },
      { char: '🤚', name: '手背', en: 'raised back of hand' },
      { char: '🖐️', name: '张开手掌', en: 'hand fingers splayed' },
      { char: '✋', name: '停', en: 'stop hand high five' },
      { char: '🖖', name: '瓦肯', en: 'vulcan salute spock' },
      { char: '👌', name: '好的', en: 'ok okay perfect' },
      { char: '🤌', name: '捏手', en: 'pinched fingers italian' },
      { char: '🤏', name: '一点点', en: 'pinching small' },
      { char: '👈', name: '左指', en: 'point left' },
      { char: '👉', name: '右指', en: 'point right' },
      { char: '👆', name: '上指', en: 'point up' },
      { char: '👇', name: '下指', en: 'point down' },
      { char: '☝️', name: '食指上', en: 'index point up' },
      { char: '🫵', name: '指你', en: 'pointing at you' },
      { char: '🫡', name: '敬礼', en: 'salute respect' },
      { char: '🫠', name: '融化', en: 'melting face' },
      { char: '🫣', name: '偷看', en: 'peeking hiding' },
      { char: '👀', name: '看', en: 'eyes look see watch' },
      { char: '👁️', name: '眼睛', en: 'eye see look' },
      { char: '👅', name: '舌头', en: 'tongue lick taste' },
      { char: '👄', name: '嘴巴', en: 'mouth lips' },
      { char: '🫶', name: '爱心手', en: 'heart hands love' },
      { char: '✍️', name: '写字', en: 'writing pen' },
      { char: '🙏', name: '祈祷手', en: 'pray please thanks' },
    ]
  },
  {
    id: 'hearts', name: '爱心', icon: '❤️',
    items: [
      { char: '❤️', name: '红心', en: 'red heart love' },
      { char: '🧡', name: '橙心', en: 'orange heart' },
      { char: '💛', name: '黄心', en: 'yellow heart' },
      { char: '💚', name: '绿心', en: 'green heart' },
      { char: '💙', name: '蓝心', en: 'blue heart' },
      { char: '💜', name: '紫心', en: 'purple heart' },
      { char: '🖤', name: '黑心', en: 'black heart dark' },
      { char: '🤍', name: '白心', en: 'white heart pure' },
      { char: '🤎', name: '棕心', en: 'brown heart' },
      { char: '💔', name: '心碎', en: 'broken heart sad' },
      { char: '❤️‍🔥', name: '燃烧的心', en: 'heart on fire passion' },
      { char: '💕', name: '两颗心', en: 'two hearts love' },
      { char: '💞', name: '旋转的心', en: 'revolving hearts love' },
      { char: '💓', name: '心跳', en: 'heartbeat heart pulse' },
      { char: '💗', name: '成长的心', en: 'growing heart love' },
      { char: '💖', name: '闪亮的心', en: 'sparkling heart' },
      { char: '💘', name: '箭穿心', en: 'heart arrow cupid' },
      { char: '💝', name: '蝴蝶结心', en: 'heart ribbon gift' },
      { char: '💯', name: '满分', en: 'hundred perfect score' },
      { char: '✨', name: '闪亮', en: 'sparkles stars shine' },
      { char: '🌟', name: '星星', en: 'star glowing bright' },
      { char: '⭐', name: '星', en: 'star' },
      { char: '💫', name: '眩晕', en: 'dizzy star shooting' },
    ]
  },
  {
    id: 'party', name: '派对', icon: '🎉',
    items: [
      { char: '🎉', name: '派对', en: 'party popper celebration' },
      { char: '🎊', name: '彩球', en: 'confetti ball' },
      { char: '🎈', name: '气球', en: 'balloon party' },
      { char: '🎁', name: '礼物', en: 'gift present box' },
      { char: '🎂', name: '蛋糕', en: 'birthday cake' },
      { char: '🎆', name: '烟花', en: 'fireworks celebration' },
      { char: '🎇', name: '火花', en: 'sparkler firework' },
      { char: '🧨', name: '鞭炮', en: 'firecracker dynamite' },
      { char: '🎑', name: '赏月', en: 'moon viewing ceremony' },
      { char: '🎀', name: '蝴蝶结', en: 'ribbon bow' },
      { char: '🎗️', name: '丝带', en: 'reminder ribbon' },
      { char: '🎪', name: '马戏团', en: 'circus tent' },
      { char: '🎫', name: '门票', en: 'ticket admission' },
      { char: '🎭', name: '面具', en: 'masks performing arts' },
      { char: '🎨', name: '调色板', en: 'art palette painting' },
      { char: '🎬', name: '电影', en: 'movie clapper film' },
      { char: '🎤', name: '麦克风', en: 'microphone karaoke' },
      { char: '🎧', name: '耳机', en: 'headphones music' },
      { char: '🎵', name: '音符', en: 'music note' },
      { char: '🎶', name: '音符组', en: 'notes music' },
      { char: '🎹', name: '钢琴', en: 'piano keyboard' },
      { char: '🎺', name: '小号', en: 'trumpet horn' },
      { char: '🎸', name: '吉他', en: 'guitar rock' },
      { char: '🥁', name: '鼓', en: 'drum drumsticks' },
    ]
  },
  {
    id: 'food', name: '食物', icon: '🍕',
    items: [
      { char: '🍕', name: '披萨', en: 'pizza slice' },
      { char: '🍔', name: '汉堡', en: 'hamburger burger' },
      { char: '🍟', name: '薯条', en: 'french fries' },
      { char: '🌭', name: '热狗', en: 'hotdog sausage' },
      { char: '🍿', name: '爆米花', en: 'popcorn movie' },
      { char: '🧂', name: '盐', en: 'salt salty' },
      { char: '🥓', name: '培根', en: 'bacon meat' },
      { char: '🥚', name: '鸡蛋', en: 'egg' },
      { char: '🍳', name: '煎蛋', en: 'cooking fried egg' },
      { char: '🧇', name: '华夫饼', en: 'waffle' },
      { char: '🥞', name: '煎饼', en: 'pancake' },
      { char: '🧀', name: '奶酪', en: 'cheese' },
      { char: '🥩', name: '牛排', en: 'steak meat cut' },
      { char: '🍗', name: '鸡腿', en: 'chicken leg drumstick' },
      { char: '🍖', name: '肉', en: 'meat bone' },
      { char: '🌮', name: '墨西哥卷', en: 'taco mexican' },
      { char: '🌯', name: '卷饼', en: 'burrito wrap' },
      { char: '🍺', name: '啤酒', en: 'beer mug drink' },
      { char: '🍻', name: '干杯', en: 'beers cheers toast' },
      { char: '🥂', name: '香槟', en: 'champagne glasses toast' },
      { char: '🍷', name: '红酒', en: 'wine red glass' },
      { char: '🍸', name: '鸡尾酒', en: 'cocktail martini' },
      { char: '🍹', name: '热带饮品', en: 'tropical drink cocktail' },
      { char: '☕', name: '咖啡', en: 'coffee cup hot' },
      { char: '🧃', name: '果汁', en: 'juice box beverage' },
      { char: '🥤', name: '奶茶', en: 'bubble tea cup straw' },
      { char: '🍦', name: '冰淇淋', en: 'ice cream soft serve' },
      { char: '🍧', name: '刨冰', en: 'shaved ice dessert' },
      { char: '🍰', name: '蛋糕', en: 'cake slice dessert' },
      { char: '🧁', name: '杯子蛋糕', en: 'cupcake muffin' },
    ]
  },
  {
    id: 'animals', name: '动物', icon: '🐾',
    items: [
      { char: '🐶', name: '狗', en: 'dog puppy pet' },
      { char: '🐱', name: '猫', en: 'cat kitten pet' },
      { char: '🐭', name: '老鼠', en: 'mouse rat' },
      { char: '🐹', name: '仓鼠', en: 'hamster pet' },
      { char: '🐰', name: '兔子', en: 'rabbit bunny' },
      { char: '🦊', name: '狐狸', en: 'fox clever' },
      { char: '🐻', name: '熊', en: 'bear teddy' },
      { char: '🐼', name: '熊猫', en: 'panda bear' },
      { char: '🐨', name: '考拉', en: 'koala australia' },
      { char: '🐯', name: '老虎', en: 'tiger cat' },
      { char: '🦁', name: '狮子', en: 'lion king' },
      { char: '🐮', name: '牛', en: 'cow moo' },
      { char: '🐷', name: '猪', en: 'pig oink' },
      { char: '🐸', name: '青蛙', en: 'frog toad' },
      { char: '🐵', name: '猴子', en: 'monkey ape' },
      { char: '🐔', name: '鸡', en: 'chicken hen' },
      { char: '🐧', name: '企鹅', en: 'penguin bird' },
      { char: '🐦', name: '鸟', en: 'bird tweet' },
      { char: '🦅', name: '鹰', en: 'eagle hawk bird' },
      { char: '🦆', name: '鸭子', en: 'duck bird' },
      { char: '🦉', name: '猫头鹰', en: 'owl wise bird' },
      { char: '🐴', name: '马', en: 'horse' },
      { char: '🦄', name: '独角兽', en: 'unicorn magic' },
      { char: '🐝', name: '蜜蜂', en: 'bee honey buzz' },
      { char: '🦋', name: '蝴蝶', en: 'butterfly beautiful' },
      { char: '🐌', name: '蜗牛', en: 'snail slow' },
      { char: '🐞', name: '瓢虫', en: 'ladybug bug' },
      { char: '🐢', name: '乌龟', en: 'turtle slow' },
      { char: '🐍', name: '蛇', en: 'snake' },
      { char: '🦈', name: '鲨鱼', en: 'shark' },
    ]
  },
]

// ── 最近使用 ──
const recentChars = ref<string[]>(loadRecent())

function loadRecent(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}

function saveRecent(chars: string[]) {
  localStorage.setItem(RECENT_KEY, JSON.stringify(chars))
}

function addToRecent(char: string) {
  const list = recentChars.value.filter(c => c !== char)
  list.unshift(char)
  if (list.length > MAX_RECENT) list.length = MAX_RECENT
  recentChars.value = list
  saveRecent(list)
}

function findEmoji(char: string): EmojiItem | undefined {
  for (const g of groups) {
    const found = g.items.find(e => e.char === char)
    if (found) return found
  }
  return undefined
}

const recentEmojis = computed<EmojiItem[]>(() => {
  return recentChars.value.map(c => findEmoji(c)).filter(Boolean) as EmojiItem[]
})

const allGroups = computed(() => [
  { id: 'recent', name: '最近使用', icon: '🕐', items: [] },
  ...groups,
])

const currentGroup = computed(() => {
  if (activeTab.value === 'recent') return null
  return groups.find(g => g.id === activeTab.value) || null
})

// ── 搜索 ──
const allFlat = computed(() => groups.flatMap(g => g.items))

const searchResults = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return []
  return allFlat.value.filter(e =>
    e.name.toLowerCase().includes(q) ||
    e.en.toLowerCase().includes(q) ||
    e.char === q
  )
})

// ── 键盘导航 ──
const currentItems = computed(() => {
  if (search.value.trim()) return searchResults.value
  if (activeTab.value === 'recent') return recentEmojis.value
  return currentGroup.value?.items || []
})

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
    scrollToHighlighted()
    return
  }

  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    highlightIndex.value = Math.max(highlightIndex.value - 1, 0)
    scrollToHighlighted()
    return
  }

  const cols = Math.floor(296 / 36)

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    highlightIndex.value = Math.min(highlightIndex.value + cols, items.length - 1)
    scrollToHighlighted()
    return
  }

  if (e.key === 'ArrowUp') {
    e.preventDefault()
    highlightIndex.value = Math.max(highlightIndex.value - cols, 0)
    scrollToHighlighted()
    return
  }

  if (e.key === 'Enter') {
    e.preventDefault()
    if (highlightIndex.value >= 0 && highlightIndex.value < items.length) {
      select(items[highlightIndex.value].char)
    }
    return
  }
}

function scrollToHighlighted() {
  nextTick(() => {
    const grid = gridRef.value
    if (!grid) return
    const highlighted = grid.querySelector('.emoji-item.highlighted') as HTMLElement
    if (highlighted) {
      highlighted.scrollIntoView({ block: 'nearest' })
    }
  })
}

// ── 选择 ──
function select(char: string) {
  addToRecent(char)
  emit('select', char)
}

// ── 智能定位 ──
const PICKER_W = 340
const PICKER_H = 380

/** 获取 anchor 所在 el-dialog 的 z-index，如果没有则返回 0 */
function getParentDialogZIndex(el: HTMLElement | null): number {
  let node: HTMLElement | null = el
  while (node) {
    if (node.classList?.contains('el-overlay') || node.classList?.contains('el-dialog__wrapper')) {
      const z = parseInt(getComputedStyle(node).zIndex, 10)
      if (!isNaN(z) && z > 0) return z
    }
    node = node.parentElement
  }
  return 0
}

const pickerZIndex = computed(() => {
  if (!props.anchor) return 1000
  const dialogZ = getParentDialogZIndex(props.anchor)
  return dialogZ > 0 ? dialogZ + 10 : 1000
})

const pickerStyle = computed(() => {
  const z = pickerZIndex.value
  if (!props.anchor) {
    return {
      top: '50%',
      left: '50%',
      transform: 'translate(-50%, -50%)',
      zIndex: z,
    }
  }
  const r = props.anchor.getBoundingClientRect()
  let top = r.bottom + 4
  let left = r.left

  if (top + PICKER_H > window.innerHeight) {
    top = r.top - PICKER_H - 4
  }
  if (left + PICKER_W > window.innerWidth) {
    left = r.right - PICKER_W
  }
  if (left < 8) {
    left = 8
  }
  if (top < 8) {
    top = 8
  }

  return {
    top: `${top}px`,
    left: `${left}px`,
    zIndex: z,
  }
})

// ── 打开时重置状态 ──
watch(() => props.visible, (v) => {
  if (v) {
    search.value = ''
    activeTab.value = 'recent'
    highlightIndex.value = -1
    nextTick(() => {
      searchRef.value?.focus()
    })
  }
})

// ── 搜索时重置高亮 ──
watch(search, () => {
  highlightIndex.value = -1
})
</script>

<style scoped>
.emoji-backdrop { position: fixed; inset: 0; z-index: 999; }
.emoji-picker {
  position: fixed;
  width: 340px;
  max-height: 400px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.24);
  z-index: 1000;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  animation: emojiIn 0.15s cubic-bezier(0.22, 1, 0.36, 1);
}
@keyframes emojiIn {
  from { opacity: 0; transform: scale(0.95) translateY(-4px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

/* Tab 栏 */
.emoji-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border-light);
  overflow-x: auto;
  scrollbar-width: none;
}
.emoji-tabs::-webkit-scrollbar { display: none; }
.emoji-tab {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  border: none;
  background: none;
  border-radius: 6px;
  cursor: pointer;
  opacity: 0.5;
  transition: all 0.12s;
  flex-shrink: 0;
}
.emoji-tab:hover { opacity: 0.8; background: var(--bg-hover); }
.emoji-tab.active { opacity: 1; background: var(--accent-lighter); }
.emoji-tabs-spacer { flex: 1; }
.emoji-close {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  background: none; border: none;
  color: var(--text-muted); cursor: pointer;
  font-size: 13px; border-radius: 6px;
  flex-shrink: 0;
}
.emoji-close:hover { background: var(--bg-hover); color: var(--text-primary); }

/* 搜索 */
.emoji-search {
  padding: 6px 10px;
  border-bottom: 1px solid var(--border-light);
}
.emoji-search-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.12s;
}
.emoji-search-input:focus { border-color: var(--accent); }
.emoji-search-input::placeholder { color: var(--text-dim); }

/* 内容区 */
.emoji-grid {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
  min-height: 200px;
  max-height: 300px;
}
.emoji-group { margin-bottom: 8px; }
.emoji-group-title {
  font-size: 10px;
  color: var(--text-dim);
  font-weight: 600;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.emoji-items { display: flex; flex-wrap: wrap; gap: 2px; }
.emoji-item {
  width: 34px; height: 34px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
  border: none; background: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s, transform 0.1s;
}
.emoji-item:hover { background: var(--bg-hover); transform: scale(1.15); }
.emoji-item.highlighted {
  background: var(--accent-lighter);
  outline: 2px solid var(--accent);
  outline-offset: -2px;
  transform: scale(1.1);
}
.emoji-empty {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-dim);
  font-size: 13px;
}

/* 移动端 */
@media (max-width: 768px) {
  .emoji-picker {
    width: calc(100vw - 32px);
    max-width: 340px;
    left: 50% !important;
    transform: translateX(-50%) !important;
  }
}

/* 浅色主题 */
[data-theme="light"] .emoji-picker {
  background: #fff;
  border-color: #E8E5E0;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}
[data-theme="light"] .emoji-search-input {
  background: #F5F3F0;
  border-color: #E8E5E0;
}
[data-theme="light"] .emoji-item:hover { background: #FAF9F7; }
[data-theme="light"] .emoji-tab:hover { background: #F5F3F0; }
[data-theme="light"] .emoji-tab.active { background: rgba(232, 93, 38, 0.1); }
</style>
