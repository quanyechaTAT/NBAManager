# EmojiPicker 全面优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 EmojiPicker.vue 从 57 个表情的基础选择器升级为 ~200 个表情、Tab 分组、最近使用、中英文搜索、智能定位、键盘支持的专业表情面板，并通过 useEmojiPicker composable 统一 3 个页面的状态管理。

**架构：** 新增 `useEmojiPicker` composable 管理 open/close/callback 状态；重写 `EmojiPicker.vue` 组件，内部包含 Tab 导航、搜索、表情网格、智能定位和键盘事件处理；3 个消费页面（PostDetailView、MatchDetailView、CommunityView）从多个独立 ref 迁移到单一 composable 调用。

**技术栈：** Vue 3 Composition API、TypeScript、localStorage

---

## 文件结构

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| 新增 | `frontend/src/composables/useEmojiPicker.ts` | composable：管理 visible、anchor、open/onSelect/close |
| 重写 | `frontend/src/components/EmojiPicker.vue` | 组件：Tab 导航、搜索、表情数据、智能定位、键盘支持 |
| 修改 | `frontend/src/views/PostDetailView.vue` | 消费端：3 个 ref + 3 个函数 → 1 个 composable |
| 修改 | `frontend/src/views/MatchDetailView.vue` | 消费端：2 个 ref + 2 个函数 → 1 个 composable |
| 修改 | `frontend/src/views/CommunityView.vue` | 消费端：1 个 ref + emojiTarget → 1 个 composable |

---

### 任务 1：创建 useEmojiPicker composable

**文件：**
- 创建：`frontend/src/composables/useEmojiPicker.ts`

- [ ] **步骤 1：创建 composable 文件**

```typescript
// frontend/src/composables/useEmojiPicker.ts
import { ref } from 'vue'

export function useEmojiPicker() {
  const visible = ref(false)
  const anchor = ref<HTMLElement | null>(null)
  let callback: ((emoji: string) => void) | null = null

  function open(anchorEl: HTMLElement, onInsert: (emoji: string) => void) {
    anchor.value = anchorEl
    callback = onInsert
    visible.value = true
  }

  function onSelect(emoji: string) {
    callback?.(emoji)
    visible.value = false
  }

  function close() {
    visible.value = false
  }

  return { visible, anchor, open, onSelect, close }
}
```

- [ ] **步骤 2：验证文件语法**

运行：`cd frontend && npx vue-tsc --noEmit --pretty 2>&1 | head -20`
预期：无 composable 相关错误（其他已有错误可忽略）

- [ ] **步骤 3：Commit**

```bash
git add frontend/src/composables/useEmojiPicker.ts
git commit -m "feat: 添加 useEmojiPicker composable

管理 visible/anchor/open/onSelect/close 状态，
替代各页面中多个独立的 emoji 状态 ref。"
```

---

### 任务 2：重写 EmojiPicker.vue — 表情数据与 Tab 导航

**文件：**
- 重写：`frontend/src/components/EmojiPicker.vue`

- [ ] **步骤 1：重写 EmojiPicker.vue — 数据层 + Tab 导航 + 搜索**

将整个文件替换为以下内容（包含 ~200 个表情数据、8 个 Tab、中英文搜索）：

```vue
<template>
  <Teleport to="body">
    <div v-if="visible" class="emoji-backdrop" @click="$emit('close')"></div>
    <div
      v-if="visible"
      ref="pickerRef"
      class="emoji-picker"
      :style="pickerStyle"
      @keydown="onKeydown"
    >
      <!-- Tab 栏 -->
      <div class="emoji-tabs">
        <button
          v-for="(group, i) in allGroups"
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
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'

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
const pickerRef = ref<HTMLElement | null>(null)
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
      { char: '⛹️‍♀️', name: '女球员', en: 'woman basketball player' },
      { char: '🏟️', name: '体育场', en: 'stadium arena' },
      { char: '🥇', name: '冠军', en: 'champion winner first' },
      { char: '📣', name: '喇叭', en: 'megaphone cheer loudspeaker' },
      { char: '🏟️', name: '球场', en: 'court field stadium' },
      { char: '⏱️', name: '计时', en: 'stopwatch timer clock' },
      { char: '📊', name: '数据', en: 'stats chart bar graph data' },
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
      { char: '💪', name: '肌肉', en: 'muscle strong bicep' },
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
      { char: '🔥', name: '火', en: 'fire hot flame' },
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
      { char: '✨', name: '星光', en: 'sparkles stars' },
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

  // 估算每行个数（基于容器宽度 ~300px，每个 item ~34px + gap 2px）
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

const pickerStyle = computed(() => {
  if (!props.anchor) {
    // fallback: 居中
    return {
      top: '50%',
      left: '50%',
      transform: 'translate(-50%, -50%)',
    }
  }
  const r = props.anchor.getBoundingClientRect()
  let top = r.bottom + 4
  let left = r.left

  // 下边界翻转
  if (top + PICKER_H > window.innerHeight) {
    top = r.top - PICKER_H - 4
  }
  // 右边界翻转
  if (left + PICKER_W > window.innerWidth) {
    left = r.right - PICKER_W
  }
  // 左边界保护
  if (left < 8) {
    left = 8
  }
  // 上边界保护
  if (top < 8) {
    top = 8
  }

  return {
    top: `${top}px`,
    left: `${left}px`,
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
```

- [ ] **步骤 2：验证组件无语法错误**

运行：`cd frontend && npx vue-tsc --noEmit --pretty 2>&1 | grep -i "EmojiPicker" | head -10`
预期：无 EmojiPicker 相关错误

- [ ] **步骤 3：Commit**

```bash
git add frontend/src/components/EmojiPicker.vue
git commit -m "feat: 重写 EmojiPicker — Tab导航/200表情/搜索/智能定位/键盘

- 8个Tab分组：最近使用、篮球、表情、手势、爱心、派对、食物、动物
- ~200个表情，每个包含中英文搜索字段
- localStorage 持久化最近使用（MRU，最多16个）
- 智能定位：视口边界检测、自动翻转
- 键盘支持：Esc关闭、方向键导航、Enter选择
- 移动端适配：居中显示、max-width限制"
```

---

### 任务 3：适配 PostDetailView — 3 个状态 → 1 个 composable

**文件：**
- 修改：`frontend/src/views/PostDetailView.vue`

- [ ] **步骤 1：替换 import 和状态声明**

在 `import` 区域，将：
```typescript
import EmojiPicker from '@/components/EmojiPicker.vue'
```
替换为：
```typescript
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'
```

在 `const` 声明区域，删除以下 3 行：
```typescript
const showEmojiPicker = ref(false)
const showReplyEmojiPicker = ref(false)
const showEditEmojiPicker = ref(false)
```

替换为：
```typescript
const emoji = useEmojiPicker()
```

- [ ] **步骤 2：添加 template ref 声明**

在 composable 声明之后添加：
```typescript
const commentEmojiBtn = ref<HTMLElement | null>(null)
const replyEmojiBtn = ref<HTMLElement | null>(null)
const editEmojiBtn = ref<HTMLElement | null>(null)
```

- [ ] **步骤 3：删除 3 个 insert 函数**

删除：
```typescript
function insertEmoji(emoji: string) { newComment.value += emoji; showEmojiPicker.value = false }
function insertReplyEmoji(emoji: string) { replyContent.value += emoji; showReplyEmojiPicker.value = false }
function insertEditEmoji(emoji: string) { editForm.value.content += emoji; showEditEmojiPicker.value = false }
```

- [ ] **步骤 4：更新 template — 评论区 emoji 触发器**

将评论区的：
```html
<div class="emoji-trigger" @click="showEmojiPicker = !showEmojiPicker">
  <svg .../>
</div>
<EmojiPicker :visible="showEmojiPicker" @select="insertEmoji" @close="showEmojiPicker = false" />
```
替换为：
```html
<div ref="commentEmojiBtn" class="emoji-trigger" @click="emoji.open(commentEmojiBtn!, e => newComment += e)">
  <svg .../>
</div>
<EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
```

- [ ] **步骤 5：更新 template — 回复区 emoji 触发器**

将回复区的：
```html
<div class="emoji-trigger" @click="showReplyEmojiPicker = !showReplyEmojiPicker">
  <svg .../>
</div>
<EmojiPicker :visible="showReplyEmojiPicker" @select="insertReplyEmoji" @close="showReplyEmojiPicker = false" />
```
替换为：
```html
<div ref="replyEmojiBtn" class="emoji-trigger" @click="emoji.open(replyEmojiBtn!, e => replyContent += e)">
  <svg .../>
</div>
<EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
```

注意：回复区的 EmojiPicker 组件实例可以删除，复用评论区的那个（因为同一时间只有一个可见）。但为简化改动，保留两个实例也可以——它们共享同一个 composable 状态。

实际上更简洁的做法是：删除回复区的 EmojiPicker 组件，因为 composable 管理的是同一个 visible/anchor。回复区只需触发 `emoji.open()`，弹窗会自动显示在正确的 anchor 位置。

将回复区的：
```html
<div ref="replyEmojiBtn" class="emoji-trigger" @click="emoji.open(replyEmojiBtn!, e => replyContent += e)">
  <svg .../>
</div>
<EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
```
简化为（删除 EmojiPicker 实例）：
```html
<div ref="replyEmojiBtn" class="emoji-trigger" @click="emoji.open(replyEmojiBtn!, e => replyContent += e)">
  <svg .../>
</div>
```

- [ ] **步骤 6：更新 template — 编辑弹窗 emoji 触发器**

将编辑弹窗中的：
```html
<button class="emoji-trigger" @click.prevent="showEditEmojiPicker = !showEditEmojiPicker" title="插入表情">
  <svg .../>
</button>
...
<EmojiPicker :visible="showEditEmojiPicker" @select="insertEditEmoji" @close="showEditEmojiPicker = false" />
```
替换为：
```html
<button ref="editEmojiBtn" class="emoji-trigger" @click.prevent="emoji.open(editEmojiBtn!, e => editForm.content += e)" title="插入表情">
  <svg .../>
</button>
```
（删除编辑弹窗中的 EmojiPicker 实例，复用主 EmojiPicker）

- [ ] **步骤 7：确保主 EmojiPicker 放在 template 顶层**

在 `</div>` 结束标签之前（`</template>` 之前），确保有一个 EmojiPicker 实例：
```html
<EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
```

- [ ] **步骤 8：验证编译**

运行：`cd frontend && npx vue-tsc --noEmit --pretty 2>&1 | grep -i "PostDetail" | head -10`
预期：无 PostDetailView 相关错误

- [ ] **步骤 9：Commit**

```bash
git add frontend/src/views/PostDetailView.vue
git commit -m "refactor: PostDetailView 使用 useEmojiPicker composable

3个独立emoji状态 → 1个composable调用
3个insert函数 → 内联箭头函数
3个EmojiPicker实例 → 1个共享实例"
```

---

### 任务 4：适配 MatchDetailView — 2 个状态 → 1 个 composable

**文件：**
- 修改：`frontend/src/views/MatchDetailView.vue`

- [ ] **步骤 1：替换 import 和状态声明**

在 `import` 区域，将：
```typescript
import EmojiPicker from '@/components/EmojiPicker.vue'
```
替换为：
```typescript
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'
```

删除以下 4 行：
```typescript
const showEmojiPicker = ref(false)
const showReplyEmojiPicker = ref(false)
const emojiBtnRef = ref<HTMLElement | null>(null)
const replyEmojiBtnRef = ref<HTMLElement | null>(null)
```

替换为：
```typescript
const emoji = useEmojiPicker()
const commentEmojiBtn = ref<HTMLElement | null>(null)
const replyEmojiBtn = ref<HTMLElement | null>(null)
```

- [ ] **步骤 2：删除 2 个 insert 函数**

删除：
```typescript
function insertEmoji(emoji: string) {
  newComment.value += emoji
  showEmojiPicker.value = false
}

function insertReplyEmoji(emoji: string) {
  replyContent.value += emoji
  showReplyEmojiPicker.value = false
}
```

- [ ] **步骤 3：更新 template — 评论区 emoji 触发器**

将：
```html
<div ref="emojiBtnRef" class="emoji-trigger" @click="showEmojiPicker = !showEmojiPicker">
  <span class="emoji-icon">😀</span>
</div>
<EmojiPicker :visible="showEmojiPicker" :anchor="emojiBtnRef" @select="insertEmoji" @close="showEmojiPicker = false" />
```
替换为：
```html
<div ref="commentEmojiBtn" class="emoji-trigger" @click="emoji.open(commentEmojiBtn!, e => newComment += e)">
  <span class="emoji-icon">😀</span>
</div>
```

在 template 末尾（`</template>` 之前）添加共享的 EmojiPicker：
```html
<EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
```

- [ ] **步骤 4：更新 template — 回复区 emoji 触发器**

将：
```html
<div ref="replyEmojiBtnRef" class="emoji-trigger" @click="showReplyEmojiPicker = !showReplyEmojiPicker">
  <span class="emoji-icon">😀</span>
</div>
<EmojiPicker :visible="showReplyEmojiPicker" :anchor="replyEmojiBtnRef" @select="insertReplyEmoji" @close="showReplyEmojiPicker = false" />
```
替换为：
```html
<div ref="replyEmojiBtn" class="emoji-trigger" @click="emoji.open(replyEmojiBtn!, e => replyContent += e)">
  <span class="emoji-icon">😀</span>
</div>
```

- [ ] **步骤 5：验证编译**

运行：`cd frontend && npx vue-tsc --noEmit --pretty 2>&1 | grep -i "MatchDetail" | head -10`
预期：无 MatchDetailView 相关错误

- [ ] **步骤 6：Commit**

```bash
git add frontend/src/views/MatchDetailView.vue
git commit -m "refactor: MatchDetailView 使用 useEmojiPicker composable

2个独立emoji状态 → 1个composable调用
2个insert函数 → 内联箭头函数
2个EmojiPicker实例 → 1个共享实例"
```

---

### 任务 5：适配 CommunityView — 1 个状态 + emojiTarget → 1 个 composable

**文件：**
- 修改：`frontend/src/views/CommunityView.vue`

- [ ] **步骤 1：替换 import 和状态声明**

在 `import` 区域，将：
```typescript
import EmojiPicker from '@/components/EmojiPicker.vue'
```
替换为：
```typescript
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'
```

删除以下 2 行：
```typescript
const showEmojiPicker = ref(false)
const emojiTarget = ref<'title' | 'content'>('content')
```

替换为：
```typescript
const emoji = useEmojiPicker()
const emojiTarget = ref<'title' | 'content'>('content')
```

（保留 `emojiTarget` 因为 CommunityView 的发帖弹窗有标题和内容两个目标）

- [ ] **步骤 2：添加 template ref 声明**

```typescript
const contentEmojiBtn = ref<HTMLElement | null>(null)
```

- [ ] **步骤 3：删除 insertEmoji 函数**

删除：
```typescript
function insertEmoji(emoji: string) {
  if (emojiTarget.value === 'title') {
    form.value.title += emoji
  } else {
    form.value.content += emoji
  }
  showEmojiPicker.value = false
}
```

- [ ] **步骤 4：更新 openCreate 函数**

将：
```typescript
function openCreate() { createVisible.value = true; showEmojiPicker.value = false }
```
替换为：
```typescript
function openCreate() { createVisible.value = true; emoji.close() }
```

- [ ] **步骤 5：更新 template — emoji 触发器**

将：
```html
<button class="emoji-trigger" @click.prevent="showEmojiPicker = !showEmojiPicker; emojiTarget = 'content'" title="插入表情">
  <svg .../>
</button>
...
<EmojiPicker :visible="showEmojiPicker && emojiTarget === 'content'" @select="insertEmoji" @close="showEmojiPicker = false" />
```
替换为：
```html
<button ref="contentEmojiBtn" class="emoji-trigger" @click.prevent="emojiTarget = 'content'; emoji.open(contentEmojiBtn!, e => form.content += e)" title="插入表情">
  <svg .../>
</button>
```

在 template 末尾（`</template>` 之前）添加：
```html
<EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
```

- [ ] **步骤 6：验证编译**

运行：`cd frontend && npx vue-tsc --noEmit --pretty 2>&1 | grep -i "Community" | head -10`
预期：无 CommunityView 相关错误

- [ ] **步骤 7：Commit**

```bash
git add frontend/src/views/CommunityView.vue
git commit -m "refactor: CommunityView 使用 useEmojiPicker composable

1个emoji状态 + emojiTarget → 1个composable调用
insertEmoji函数 → 内联箭头函数"
```

---

### 任务 6：端到端验证

- [ ] **步骤 1：TypeScript 全量检查**

运行：`cd frontend && npx vue-tsc --noEmit --pretty 2>&1 | tail -20`
预期：无新增错误（已有错误可忽略）

- [ ] **步骤 2：启动开发服务器验证**

运行：`cd frontend && npm run dev`
预期：编译成功，无报错

- [ ] **步骤 3：功能验证清单**

手动验证以下场景（在浏览器中）：

1. **PostDetailView**：
   - 评论区点击😀 → 弹窗显示在按钮下方 → 选择表情 → 插入到评论框
   - 回复区点击😀 → 弹窗显示在回复按钮下方 → 选择表情 → 插入到回复框
   - 编辑弹窗点击😀 → 弹窗显示 → 选择表情 → 插入到编辑内容框
   - 三个场景不会互相干扰（同一时间只有一个弹窗）

2. **MatchDetailView**：
   - 评论区点击😀 → 弹窗显示 → 选择表情 → 插入到评论框
   - 回复区点击😀 → 弹窗显示 → 选择表情 → 插入到回复框

3. **CommunityView**：
   - 发帖弹窗点击😀 → 弹窗显示 → 选择表情 → 插入到内容框

4. **通用功能**：
   - Tab 切换正常（最近使用、篮球、表情等 8 个分组）
   - 搜索中文（"开心"）→ 找到😀
   - 搜索英文（"smile"）→ 找到😊
   - 搜索表情字符（"😂"）→ 找到😂
   - 最近使用记录持久化（刷新页面后仍在）
   - Esc 关闭弹窗
   - 方向键导航高亮
   - Enter 选择高亮表情
   - 弹窗靠近底部时自动翻转到上方
   - 移动端视口下弹窗不溢出
   - 深色/浅色主题均正常

- [ ] **步骤 4：最终 Commit（如有修复）**

如果验证中发现问题并修复：
```bash
git add -A
git commit -m "fix: EmojiPicker 优化的验证修复"
```

- [ ] **步骤 5：汇总 Commit**

```bash
git log --oneline -10
```

确认所有 commit 按顺序排列，功能完整。
