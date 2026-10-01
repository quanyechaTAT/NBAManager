<template>
  <div v-if="matches.length > 0" class="live-ticker">
    <!-- 左侧固定标签：按真实数据来源标注，避免把历史赛果称作「实时赛况」 -->
    <div class="live-ticker__label" :class="{ 'is-live': hasLive }">
      <span v-if="hasLive" class="live-ticker__label-dot" />
      <span v-else class="live-ticker__label-mark" aria-hidden="true" />
      <span>{{ label }}</span>
    </div>

    <div ref="trackRef" class="live-ticker__track">
      <div
        class="live-ticker__content"
        :class="{ 'is-scrolling': shouldScroll }"
        :style="{ '--ticker-duration': duration }"
      >
        <!-- 两组内容首尾相接：位移 50% 即一组宽度，实现无缝循环 -->
        <div
          v-for="groupIndex in groups"
          :key="groupIndex"
          class="live-ticker__group"
          :aria-hidden="groupIndex > 0 ? 'true' : undefined"
        >
          <div
            v-for="match in matches"
            :key="match.id"
            class="live-ticker__item"
            :class="[`is-${match.status.toLowerCase()}`]"
          >
            <span v-if="match.status === 'LIVE'" class="live-ticker__live-dot" aria-hidden="true" aria-label="直播中" />
            <span v-else-if="match.status === 'FINISHED'" class="live-ticker__final-mark" aria-hidden="true">FINAL</span>
            <span v-if="match.status !== 'LIVE' && match.matchDate" class="live-ticker__date">{{ dateLabel(match.matchDate) }}</span>

            <!-- 主队 -->
            <span class="live-ticker__team">
              <img v-if="getTeamLogo(match.homeTeam)" :src="getTeamLogo(match.homeTeam)" :alt="match.homeTeam" class="live-ticker__logo" />
              <span class="live-ticker__team-name">{{ match.homeTeam }}</span>
              <span
                v-if="match.status !== 'SCHEDULED'"
                class="live-ticker__score"
                :class="{ 'is-winner': match.homeScore > match.awayScore, 'is-loser': match.homeScore < match.awayScore }"
              >{{ match.homeScore }}</span>
            </span>

            <span class="live-ticker__vs">{{ getVsText(match) }}</span>

            <!-- 客队 -->
            <span class="live-ticker__team">
              <span
                v-if="match.status !== 'SCHEDULED'"
                class="live-ticker__score"
                :class="{ 'is-winner': match.awayScore > match.homeScore, 'is-loser': match.awayScore < match.homeScore }"
              >{{ match.awayScore }}</span>
              <span class="live-ticker__team-name">{{ match.awayTeam }}</span>
              <img v-if="getTeamLogo(match.awayTeam)" :src="getTeamLogo(match.awayTeam)" :alt="match.awayTeam" class="live-ticker__logo" />
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MatchRecord } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import { classifyMatches, liveMatches } from '@/utils/matchFeed'

interface Props {
  /** 比赛记录列表（已按展示优先级排序） */
  matches: MatchRecord[]
}

const props = defineProps<Props>()

/** 标签文案：直播中 / 今日赛程 / 最近赛果 */
const label = computed(() => {
  const groups = classifyMatches(props.matches)
  return groups[0]?.label ?? '赛事'
})

/** 只有真正存在直播时才启用橙色脉冲 */
const hasLive = computed(() => liveMatches(props.matches).length > 0)

const trackRef = ref<HTMLElement | null>(null)
/** 内容宽度超过可视区域时才需要滚动 */
const shouldScroll = ref(false)
/** 滚动一轮的时长，按内容宽度换算，保证匀速 */
const duration = ref('30s')

/** 需要渲染的分组数量：滚动时复制一份用于无缝衔接 */
const groups = computed(() => (shouldScroll.value ? [0, 1] : [0]))

let resizeObserver: ResizeObserver | null = null

/** 按 70px/s 匀速估算时长，限制在 16~60s，避免过快或过慢 */
function calcDuration(groupWidth: number): string {
  const seconds = Math.min(60, Math.max(16, groupWidth / 70))
  return `${seconds.toFixed(1)}s`
}

/**
 * 测量单组内容宽度并决定是否滚动。
 * 只测第一组，避免复制组带来的宽度自激。
 */
function measure() {
  const track = trackRef.value
  const group = track?.querySelector<HTMLElement>('.live-ticker__group')
  if (!track || !group) return

  const groupWidth = group.getBoundingClientRect().width
  const trackWidth = track.clientWidth
  if (trackWidth === 0) return

  // 留 1px 容差，避免因亚像素舍入导致轻微溢出就启动滚动
  const overflow = groupWidth > trackWidth + 1
  shouldScroll.value = overflow
  duration.value = overflow ? calcDuration(groupWidth) : '30s'
}

function observe() {
  resizeObserver?.disconnect()
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => measure())
  if (trackRef.value) resizeObserver.observe(trackRef.value)
  // 观察内容本身：队徽异步加载、队名变化都会改变宽度
  const content = trackRef.value?.querySelector('.live-ticker__content')
  if (content) resizeObserver.observe(content)
}

onMounted(async () => {
  await nextTick()
  observe()
  measure()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

// 数据刷新（每 2 分钟）后重新测量
watch(() => props.matches, async () => {
  await nextTick()
  measure()
})

/**
 * 获取对阵中间的文本。
 * 未开赛显示 'vs'；已结束显示 'FINAL'；直播显示 'LIVE'。
 * 不编造节次/剩余时间——接口未提供这些字段。
 */
function getVsText(match: MatchRecord): string {
  if (match.status === 'LIVE') return 'LIVE'
  if (match.status === 'FINISHED') return 'FINAL'
  return 'vs'
}

function dateLabel(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
/* ===== 通栏赛况条：胶囊卡片 + 无缝循环滚动 + 两端渐隐 ===== */
.live-ticker {
  display: flex;
  align-items: stretch;
  width: 100%;
  height: 40px;
  background: var(--bg-base);
  border-bottom: 1px solid var(--border-subtle);
}

/* 左侧固定标签 */
.live-ticker__label {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 16px;
  flex-shrink: 0;
  font-family: var(--font-heading);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.5px;
  color: var(--accent);
  background: var(--accent-soft);
  border-right: 1px solid var(--border-subtle);
  z-index: 3;
  white-space: nowrap;
}

.live-ticker__label-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--live, #F97316);
  animation: liveBlink 1.6s ease-in-out infinite;
}

/* 非直播状态：静态标记，不用脉冲，避免"假直播"暗示 */
.live-ticker__label-mark {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
}

.live-ticker__label.is-live {
  color: var(--live, #F97316);
  background: rgba(249, 115, 22, 0.12);
}

@keyframes liveBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

/* 终场标记：弱化，与直播的橙色脉冲区分 */
.live-ticker__final-mark {
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
  color: var(--text-tertiary);
  border: 1px solid var(--border-medium);
  border-radius: 3px;
  padding: 2px 3px;
  flex-shrink: 0;
}

/* 滚动区：横向裁剪 + 两端渐隐遮罩 */
.live-ticker__track {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 0 12px;
  overflow: hidden;
  scrollbar-width: none;
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 16px, #000 calc(100% - 16px), transparent 100%);
  mask-image: linear-gradient(90deg, transparent 0, #000 16px, #000 calc(100% - 16px), transparent 100%);
}
.live-ticker__track::-webkit-scrollbar { display: none; }

/* 内容轨道：宽度由内容决定，不参与收缩 */
.live-ticker__content {
  display: flex;
  align-items: center;
  width: max-content;
  flex-shrink: 0;
}

/* 无缝循环：从 0 平移到 -50%（恰好一组宽度） */
.live-ticker__content.is-scrolling {
  animation: liveTickerScroll var(--ticker-duration, 30s) linear infinite;
  will-change: transform;
}

/* 悬停 / 键盘聚焦时暂停，方便阅读与点击 */
.live-ticker__content.is-scrolling:hover,
.live-ticker__content.is-scrolling:focus-within {
  animation-play-state: paused;
}

@keyframes liveTickerScroll {
  from { transform: translate3d(0, 0, 0); }
  to { transform: translate3d(-50%, 0, 0); }
}

/* 单组内容：组内间距 8px，组间由左右各 4px 内边距拼成 8px，保证接缝处等距 */
.live-ticker__group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  padding: 0 4px;
}

/* 比赛胶囊卡片 */
.live-ticker__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
}

/* 已结束仍须可读，以状态和颜色区分直播 */
.live-ticker__item.is-finished { opacity: 0.88; }

.live-ticker__date {
  color: var(--text-muted);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* 进行中：保持醒目 */
/* 进行中：橙色描边 + 极淡底色，与已结束/未开赛明确区分 */
.live-ticker__item.is-live {
  border-color: rgba(249, 115, 22, 0.45);
  background: rgba(249, 115, 22, 0.06);
}

.live-ticker__team {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.live-ticker__logo {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.live-ticker__team-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.3px;
}

/* 比分：胜方加粗主色，负方弱化 */
.live-ticker__score {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 800;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.live-ticker__score.is-winner { color: var(--text-primary); }

.live-ticker__vs {
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  letter-spacing: 0.5px;
}
.live-ticker__vs--live { color: var(--live); }
.live-ticker__vs--scheduled { color: var(--text-tertiary); }

/* LIVE 橙色脉冲圆点：仅真实直播使用 */
.live-ticker__live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--live, #F97316);
  animation: livePulse 1.5s ease-out infinite;
}

@keyframes livePulse {
  0% { box-shadow: 0 0 0 0 rgba(var(--live-rgb), 0.5); }
  70% { box-shadow: 0 0 0 6px rgba(var(--live-rgb), 0); }
  100% { box-shadow: 0 0 0 0 rgba(var(--live-rgb), 0); }
}

/* 亮色主题 */
[data-theme="light"] .live-ticker { background: var(--bg-base); }

/* 移动端隐藏（底部导航已承载核心入口） */
@media (max-width: 768px) {
  .live-ticker { display: none; }
}

/* 减少动态效果：关闭自动滚动，恢复手动横向滚动 */
@media (prefers-reduced-motion: reduce) {
  .live-ticker__content.is-scrolling { animation: none; }
  .live-ticker__track { overflow-x: auto; }
  .live-ticker__group[aria-hidden="true"] { display: none; }
}
</style>
