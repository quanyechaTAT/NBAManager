<template>
  <section class="broadcast" :class="{ 'is-empty': !headlineMatch }">
    <div class="broadcast__bg" aria-hidden="true">
      <span v-for="i in 3" :key="i" class="broadcast__ring" :style="{ '--ring-i': i }" />
    </div>

    <!-- 有比赛：以比分与状态为主角 -->
    <template v-if="headlineMatch">
      <header class="broadcast__head">
        <span class="broadcast__status" :class="`is-${headlineMatch.status.toLowerCase()}`">
          <span v-if="headlineMatch.status === 'LIVE'" class="broadcast__pulse" aria-hidden="true" />
          {{ statusText }}
        </span>
        <span class="broadcast__meta">{{ metaText }}</span>
      </header>

      <div class="broadcast__stage">
        <!-- 客队 -->
        <div class="broadcast__team">
          <div class="broadcast__logo">
            <img v-if="awayLogo" :src="awayLogo" :alt="headlineMatch.awayTeam" />
            <span v-else class="broadcast__logo-fb">{{ headlineMatch.awayTeam?.charAt(0) }}</span>
          </div>
          <span class="broadcast__name">{{ headlineMatch.awayTeam }}</span>
        </div>

        <!-- 比分 -->
        <div class="broadcast__score">
          <template v-if="showScore">
            <span class="broadcast__pts" :class="{ 'is-winner': awayWinning }">{{ headlineMatch.awayScore }}</span>
            <span class="broadcast__dash" aria-hidden="true">–</span>
            <span class="broadcast__pts" :class="{ 'is-winner': homeWinning }">{{ headlineMatch.homeScore }}</span>
          </template>
          <template v-else>
            <span class="broadcast__time">{{ kickoffText }}</span>
          </template>
        </div>

        <!-- 主队 -->
        <div class="broadcast__team">
          <div class="broadcast__logo">
            <img v-if="homeLogo" :src="homeLogo" :alt="headlineMatch.homeTeam" />
            <span v-else class="broadcast__logo-fb">{{ headlineMatch.homeTeam?.charAt(0) }}</span>
          </div>
          <span class="broadcast__name">{{ headlineMatch.homeTeam }}</span>
        </div>
      </div>

      <footer class="broadcast__foot">
        <button v-if="headlineMatch.nbaGameId" type="button" class="broadcast__btn" @click="goDetail">技术统计与战术复盘</button>
        <button type="button" class="broadcast__btn is-ghost" @click="$emit('open-feed')">完整赛程中心</button>
      </footer>
    </template>

    <!-- 无比赛：明确说明而不是留空 -->
    <template v-else>
      <p class="broadcast__eyebrow">COURTSIDE · ARENA CENTRAL</p>
      <h1 class="broadcast__empty-title">今日暂无进行中的赛事</h1>
      <p class="broadcast__empty-desc">实时比分、四节技术统计与战术复盘将在开赛后在此呈现。</p>
      <footer class="broadcast__foot">
        <button type="button" class="broadcast__btn" @click="$emit('open-feed')">浏览赛季赛程</button>
        <button type="button" class="broadcast__btn is-ghost" @click="$router.push('/teams')">球队战绩榜</button>
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MatchRecord } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import { hasScore } from '@/utils/matchFeed'

interface Props {
  /** 当前最该展示的比赛：优先直播，其次今日，再次最近赛果 */
  headlineMatch?: MatchRecord | null
  /** 数据源分类标签（直播中／今日赛程／最近赛果） */
  feedLabel?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{ 'open-feed': []; 'open-detail': [match: MatchRecord] }>()

const showScore = computed(() => (props.headlineMatch ? hasScore(props.headlineMatch) : false))
const awayLogo = computed(() => getTeamLogo(props.headlineMatch?.awayTeam ?? ''))
const homeLogo = computed(() => getTeamLogo(props.headlineMatch?.homeTeam ?? ''))

const homeWinning = computed(() =>
  showScore.value && (props.headlineMatch?.homeScore ?? 0) > (props.headlineMatch?.awayScore ?? 0)
)
const awayWinning = computed(() =>
  showScore.value && (props.headlineMatch?.awayScore ?? 0) > (props.headlineMatch?.homeScore ?? 0)
)

const statusText = computed(() => {
  const m = props.headlineMatch
  if (!m) return ''
  if (m.status === 'LIVE') return '直播中'
  if (m.status === 'SCHEDULED') return '未开赛'
  return '已结束'
})

const metaText = computed(() => {
  const m = props.headlineMatch
  if (!m) return ''
  const parts: string[] = []
  if (props.feedLabel) parts.push(props.feedLabel)
  if (m.season) parts.push(m.season)
  if (m.status === 'FINISHED' && m.matchDate) parts.push(formatDate(m.matchDate))
  return parts.join(' · ')
})

/** 未开赛时的开球时间；数据缺失就明确留空，不编造 */
const kickoffText = computed(() => {
  const m = props.headlineMatch
  if (!m) return ''
  return m.matchDate ? formatDate(m.matchDate) : '时间待定'
})

function formatDate(value: string) {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function goDetail() {
  const m = props.headlineMatch
  if (!m?.nbaGameId) return
  emit('open-detail', m)
}
</script>

<style scoped>
.broadcast {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid var(--border-medium);
  background:
    radial-gradient(85% 120% at 50% -20%, rgba(34, 211, 238, 0.14), transparent 60%),
    radial-gradient(70% 90% at 85% 100%, rgba(249, 115, 22, 0.07), transparent 60%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 45%),
    var(--bg-card);
  padding: 24px 28px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06), var(--shadow-sm);
  min-height: 220px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* 装饰性球场弧线 */
.broadcast__bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.broadcast__ring {
  position: absolute;
  right: -80px;
  top: 50%;
  width: calc(220px + var(--ring-i) * 130px);
  height: calc(220px + var(--ring-i) * 130px);
  transform: translateY(-50%);
  border: 1px solid var(--border-subtle);
  border-radius: 50%;
  opacity: 0.5;
}

.broadcast__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  position: relative;
  z-index: 1;
}

.broadcast__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.2px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
  text-transform: uppercase;
}
/* 直播：唯一使用橙色脉冲的状态 */
.broadcast__status.is-live {
  background: rgba(249, 115, 22, 0.14);
  color: var(--live, #F97316);
}
.broadcast__status.is-finished {
  background: var(--bg-elevated);
  color: var(--text-tertiary);
}
.broadcast__status.is-scheduled {
  background: var(--accent-soft);
  color: var(--accent);
}

.broadcast__pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--live, #F97316);
  animation: broadcastPulse 1.5s ease-out infinite;
}
@keyframes broadcastPulse {
  0% { box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.5); }
  70% { box-shadow: 0 0 0 7px rgba(249, 115, 22, 0); }
  100% { box-shadow: 0 0 0 0 rgba(249, 115, 22, 0); }
}

.broadcast__meta {
  font-size: 12px;
  color: var(--text-tertiary);
}

.broadcast__stage {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.broadcast__team {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.broadcast__team:last-of-type {
  flex-direction: row-reverse;
  text-align: right;
}

.broadcast__logo {
  width: 58px;
  height: 58px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-medium);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.08);
  overflow: hidden;
  transition: transform 0.2s ease, border-color 0.2s ease;
}
.broadcast__logo img {
  width: 42px;
  height: 42px;
  object-fit: contain;
}
.broadcast__logo-fb {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-secondary);
}

.broadcast__name {
  font-family: var(--font-heading);
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.broadcast__score {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 14px;
  background: rgba(0, 0, 0, 0.12);
  border-radius: 14px;
  border: 1px solid var(--border-subtle);
  font-variant-numeric: tabular-nums;
}
.broadcast__pts {
  font-family: var(--font-heading);
  font-size: 46px;
  font-weight: 800;
  line-height: 1;
  color: var(--text-secondary);
  transition: color 0.2s ease, text-shadow 0.2s ease;
}
.broadcast__pts.is-winner {
  color: var(--text-primary);
  text-shadow: 0 0 20px rgba(34, 211, 238, 0.35);
}
.broadcast__dash {
  font-size: 24px;
  color: var(--text-tertiary);
  font-weight: 300;
}
.broadcast__time {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 700;
  color: var(--accent);
}

.broadcast__foot {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  position: relative;
  z-index: 1;
}
.broadcast__btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 999px;
  border: none;
  background: var(--accent-gradient, var(--accent));
  color: var(--accent-contrast, #04202A);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.2px;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28), 0 4px 14px -3px rgba(34, 211, 238, 0.45);
  transition: transform 0.16s var(--ease-out-quart, ease), box-shadow 0.16s ease;
}
.broadcast__btn:hover {
  transform: translateY(-1px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.35), 0 8px 24px -5px rgba(34, 211, 238, 0.6);
}
.broadcast__btn.is-ghost {
  background: var(--bg-hover);
  border: 1px solid var(--border-medium);
  color: var(--text-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}
.broadcast__btn.is-ghost:hover {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: 0 4px 14px rgba(34, 211, 238, 0.2);
}

/* 空状态 */
.broadcast__eyebrow {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--accent);
  text-transform: uppercase;
  position: relative;
  z-index: 1;
}
.broadcast__empty-title {
  margin: 0 0 8px;
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: var(--text-primary);
  position: relative;
  z-index: 1;
}
.broadcast__empty-desc {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
  position: relative;
  z-index: 1;
}

@media (max-width: 768px) {
  .broadcast { padding: 18px 16px; border-radius: 14px; }
  .broadcast__stage { grid-template-columns: 1fr auto 1fr; gap: 8px; }
  .broadcast__logo { width: 44px; height: 44px; border-radius: 12px; }
  .broadcast__logo img { width: 30px; height: 30px; }
  .broadcast__name { font-size: 14px; }
  .broadcast__pts { font-size: 30px; }
  .broadcast__team { flex-direction: column; gap: 6px; text-align: center; }
  .broadcast__team:last-of-type { flex-direction: column; text-align: center; }
  .broadcast__empty-title { font-size: 22px; }
}
</style>
