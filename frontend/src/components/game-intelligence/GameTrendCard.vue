<template>
  <section class="game-trend-card">
    <header class="game-trend-header">
      <div class="title-row">
        <strong>比赛走势</strong>
        <span>领先分差、攻击波与关键节点</span>
      </div>
      <div v-if="model.points.length" class="trend-pills">
        <span>最大领先 <b>{{ maxLead }}</b></span>
        <span>反超 <b>{{ comebackCount }}</b></span>
        <span>最后5分钟 <b :class="finalFiveClass">{{ finalFiveLabel }}</b></span>
      </div>
    </header>

    <div v-if="model.points.length" class="trend-score-board">
      <div class="score-team score-team-away">
        <img v-if="awayLogo" :src="awayLogo" class="team-logo" alt="" />
        <div v-else class="team-mark">{{ awayInitial }}</div>
        <div class="team-meta">
          <strong>{{ awayTeam || '客队' }}</strong>
          <span>{{ awayInitial }}</span>
        </div>
        <b class="team-score away-score">{{ model.latestScore.away }}</b>
      </div>

      <div class="game-state">
        <strong>{{ gameStatusText }}</strong>
        <span>{{ model.latestClockLabel }}</span>
        <small>{{ scoreLeader }}</small>
      </div>

      <div class="score-team score-team-home">
        <b class="team-score home-score">{{ model.latestScore.home }}</b>
        <div class="team-meta">
          <strong>{{ homeTeam || '主队' }}</strong>
          <span>{{ homeInitial }}</span>
        </div>
        <img v-if="homeLogo" :src="homeLogo" class="team-logo" alt="" />
        <div v-else class="team-mark home">{{ homeInitial }}</div>
      </div>
    </div>

    <div v-if="model.points.length" class="trend-layout">
      <div class="trend-main-card">
        <MomentumArea
          :model="model"
          :home-team="homeTeam"
          :away-team="awayTeam"
          :display-mode="displayMode"
          :width="chartSize.width"
          :height="chartSize.height"
          :padding="chartPadding"
          @select="selectByOrder"
        />
        <EventStrip
          :markers="model.markers"
          :runs="model.runs"
          :chart-left="chartPadding.left"
          :chart-width="chartSize.width - chartPadding.left - chartPadding.right"
          @select="selectByOrder"
          @run-click="(start, end) => emit('runClick', start, end)"
        />
      </div>

    </div>

    <div v-else class="empty-state">暂无走势数据</div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MomentumSnapshot, TimelineEvent } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import EventStrip from './game-trend/EventStrip.vue'
import MomentumArea from './game-trend/MomentumArea.vue'
import type { TrendDisplayMode } from './game-trend/types'
import { buildTrendModel, findNearestPoint } from './game-trend/trendUtils'

const props = defineProps<{
  data: MomentumSnapshot[]
  timelineEvents: TimelineEvent[]
  homeTeam: string
  awayTeam: string
  selectedEvent?: TimelineEvent | null
}>()

const emit = defineEmits<{
  pointClick: [snapshot: MomentumSnapshot]
  runClick: [startOrder: number, endOrder: number]
}>()

const displayMode = ref<TrendDisplayMode>('lead') // kept for API compatibility

const chartSize = {
  width: 1180,
  height: 380,
}

const chartPadding = {
  top: 36,
  right: 24,
  bottom: 36,
  left: 52,
}

const model = computed(() =>
  buildTrendModel({
    data: props.data,
    events: props.timelineEvents,
    homeTeam: props.homeTeam,
    awayTeam: props.awayTeam,
    selectedEvent: props.selectedEvent,
    width: chartSize.width,
    height: chartSize.height,
    padding: chartPadding,
  })
)

const homeLogo = computed(() => getTeamLogo(props.homeTeam) || '')
const awayLogo = computed(() => getTeamLogo(props.awayTeam) || '')
const homeInitial = computed(() => teamInitial(props.homeTeam, '主'))
const awayInitial = computed(() => teamInitial(props.awayTeam, '客'))

const gameStatusText = computed(() => {
  const latest = model.value.points.at(-1)
  if (!latest) return '暂无数据'
  return latest.period >= 4 && latest.gameClock === '0:00' ? '已结束' : '进行中'
})

const scoreLeader = computed(() => {
  const margin = model.value.latestScore.home - model.value.latestScore.away
  if (margin === 0) return '双方打平'
  const team = margin > 0 ? props.homeTeam || '主队' : props.awayTeam || '客队'
  return `${team}领先 ${Math.abs(margin)} 分`
})

const maxLead = computed(() => Math.max(model.value.metrics.maxHomeLead, model.value.metrics.maxAwayLead))
const comebackCount = computed(() => model.value.points.filter((point) => point.keyLabel === '反超').length)
const finalFiveClass = computed(() => (model.value.metrics.finalFiveTrend >= 0 ? 'home' : 'away'))
const finalFiveLabel = computed(() => {
  const trend = model.value.metrics.finalFiveTrend
  if (trend === 0) return '持平'
  const team = trend > 0 ? props.homeTeam || '主队' : props.awayTeam || '客队'
  return `${trend > 0 ? '+' : ''}${trend} ${team}`
})

function selectByOrder(eventOrder: number) {
  const exact = props.data.find((snapshot) => snapshot.eventOrder === eventOrder)
  if (exact) {
    emit('pointClick', exact)
    return
  }

  const nearest = findNearestPoint(model.value.points, eventOrder)
  if (nearest) emit('pointClick', nearest)
}

function teamInitial(name: string, fallback: string): string {
  const value = (name || '').trim()
  if (!value) return fallback
  if (/[\u4e00-\u9fa5]/.test(value)) return value.slice(0, 2)
  const parts = value.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return parts.map((part) => part[0]).join('').slice(0, 3).toUpperCase()
  return value.slice(0, 3).toUpperCase()
}
</script>

<style scoped>
.game-trend-card {
  --trend-home: var(--gi-home);
  --trend-away: var(--accent-light);
  --trend-home-soft: rgba(var(--gi-home-rgb), 0.18);
  --trend-away-soft: rgba(var(--gi-away-rgb), 0.16);
  --trend-surface: var(--bg-card);
  --trend-panel: var(--bg-panel);
  --trend-hover: var(--bg-hover);
  --trend-text: var(--text-primary);
  --trend-muted: var(--text-secondary);
  --trend-dim: var(--text-muted);
  --trend-border: var(--border-light);
  --trend-border-strong: var(--border-medium);
  --trend-grid: var(--border-subtle);
  --quarter-fill-a: rgba(var(--bg-panel-rgb), 0.32);
  --quarter-fill-b: rgba(var(--border-light-rgb), 0.28);

  position: relative;
  display: grid;
  gap: 10px;
  padding: 14px;
  overflow: hidden;
  color: var(--trend-text);
  background:
    radial-gradient(circle at 0% 0%, var(--trend-home-soft), transparent 28%),
    radial-gradient(circle at 100% 0%, var(--trend-away-soft), transparent 30%),
    var(--trend-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  box-shadow:
    var(--shadow-lg),
    inset 0 1px 0 var(--border-subtle);
  backdrop-filter: blur(4px);
}

.game-trend-card > * {
  position: relative;
  z-index: 1;
}

.game-trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.title-row {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.title-row strong {
  color: var(--trend-text);
  font-size: 15px;
  font-weight: 950;
  letter-spacing: -0.01em;
}

.title-row span {
  color: var(--trend-home);
  font-size: 11px;
  font-weight: 750;
}

.trend-pills {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.trend-pills span {
  display: inline-flex;
  align-items: center;
  height: 22px;
  gap: 4px;
  padding: 0 8px;
  color: var(--trend-muted);
  background: rgba(var(--bg-panel-rgb), 0.58);
  border: 1px solid var(--trend-border);
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 850;
  white-space: nowrap;
}

.trend-pills b {
  color: var(--trend-text);
  font-size: 11px;
}

.trend-pills b.home {
  color: var(--trend-home);
}

.trend-pills b.away {
  color: var(--trend-away);
}

.trend-score-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(200px, 0.45fr) minmax(0, 1fr);
  align-items: center;
  min-height: 60px;
  padding: 10px 14px;
  overflow: hidden;
  background:
    linear-gradient(90deg, rgba(var(--gi-home-rgb), 0.12), rgba(var(--bg-card-rgb), 0.85) 47%, rgba(var(--gi-away-rgb), 0.12)),
    var(--trend-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(4px);
}

.score-team {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.score-team-home {
  justify-content: flex-end;
  text-align: right;
}

.team-logo,
.team-mark {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  object-fit: contain;
  border-radius: var(--radius-full);
  filter: drop-shadow(0 10px 20px var(--bg-page));
}

.team-mark {
  color: var(--trend-muted);
  background: var(--bg-card);
  border: 1px solid var(--trend-border);
  font-size: 10px;
  font-weight: 950;
  box-shadow: inset 0 1px 0 var(--border-subtle);
}

.team-meta {
  min-width: 0;
}

.team-meta strong {
  display: block;
  overflow: hidden;
  color: var(--trend-text);
  font-size: 13px;
  font-weight: 900;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-meta span {
  display: block;
  margin-top: 5px;
  color: var(--trend-muted);
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.08em;
}

.team-score {
  flex: 0 0 auto;
  font-size: clamp(28px, 3.5vw, 42px);
  font-weight: 950;
  line-height: 1;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.away-score {
  color: var(--trend-away);
  text-shadow: 0 0 28px var(--trend-away-soft);
}

.home-score {
  color: var(--trend-home);
  text-shadow: 0 0 28px var(--trend-home-soft);
}

.game-state {
  display: grid;
  justify-items: center;
  gap: 2px;
  min-width: 100px;
  padding: 0 12px;
  color: var(--trend-muted);
  text-align: center;
  border-right: 1px solid var(--trend-border);
  border-left: 1px solid var(--trend-border);
}

.game-state strong {
  color: var(--trend-text);
  font-size: 12px;
  font-weight: 900;
}

.game-state span,
.game-state small {
  color: var(--trend-muted);
  font-size: 10px;
  font-weight: 800;
}

.trend-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  align-items: start;
}

.trend-main-card {
  align-self: start;
  min-width: 0;
  padding: 10px;
  background:
    radial-gradient(circle at 50% 0%, rgba(var(--gi-home-rgb), 0.06), transparent 38%),
    var(--trend-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(4px);
}

.empty-state {
  display: grid;
  min-height: 280px;
  place-items: center;
  color: var(--trend-muted);
  background: var(--bg-panel);
  border: 1px dashed var(--trend-border);
  border-radius: var(--radius-xl);
  font-weight: 800;
}

@media (max-width: 860px) {
  .trend-score-board {
    grid-template-columns: 1fr;
    justify-items: center;
    gap: 14px;
  }

  .score-team,
  .score-team-home {
    justify-content: center;
    text-align: center;
  }

  .game-state {
    order: 3;
    border: 0;
  }
}

@media (max-width: 720px) {
  .game-trend-card,
  .trend-main-card {
    padding: 14px;
  }
}
</style>
