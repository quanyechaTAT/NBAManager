<template>
  <aside class="trend-overview">
    <!-- 比赛概览：圆环 + 最大领先 -->
    <article class="overview-panel">
      <div class="panel-head">
        <div>
          <strong>比赛概览</strong>
          <span>Game Overview</span>
        </div>
        <i>PbP</i>
      </div>

      <div class="lead-compare">
        <div class="lead-side">
          <small>{{ homeTeam || '主队' }}</small>
          <b class="home">+{{ metrics.maxHomeLead }}</b>
          <span>最大领先</span>
        </div>
        <div class="lead-donut" :style="{ '--home-ratio': `${homeRatio * 360}deg` }">
          <em>{{ Math.max(metrics.maxHomeLead, metrics.maxAwayLead) }}</em>
        </div>
        <div class="lead-side right">
          <small>{{ awayTeam || '客队' }}</small>
          <b class="away">+{{ metrics.maxAwayLead }}</b>
          <span>最大领先</span>
        </div>
      </div>

      <!-- 2x2 指标卡片网格 -->
      <div class="metric-grid">
        <div>
          <b>{{ metrics.homeLeadChanges }}</b>
          <span>领先变化</span>
        </div>
        <div>
          <b>{{ metrics.tieCount }}</b>
          <span>平局次数</span>
        </div>
        <div>
          <b>{{ criticalShotCount }}</b>
          <span>关键球</span>
        </div>
        <div>
          <b>{{ comebackCount }}</b>
          <span>反超节点</span>
        </div>
      </div>

      <div class="run-card">
        <span>最长攻击波</span>
        <strong>{{ metrics.longestRunLabel }}</strong>
      </div>

      <WinProbabilityCard :home="metrics.homeWinSignal" :away="metrics.awayWinSignal" />

      <div class="mini-trend" aria-label="最后五分钟走势">
        <div>
          <span>最后5分钟走势</span>
          <strong :class="finalFiveClass">{{ finalFiveText }}</strong>
        </div>
        <div class="mini-bars" :class="finalFiveClass">
          <i v-for="bar in finalFiveBars" :key="bar.index" :style="{ height: `${bar.height}px` }"></i>
        </div>
      </div>
    </article>

    <!-- 关键时刻：药丸卡片样式 -->
    <article class="overview-panel clutch-panel">
      <div class="panel-head compact">
        <div>
          <strong>关键时刻</strong>
          <span>Clutch Moments</span>
        </div>
      </div>

      <ul class="moment-list">
        <li v-for="moment in keyMoments" :key="moment.id">
          <i :class="moment.kind">{{ moment.badge }}</i>
          <div>
            <strong>{{ moment.title }}</strong>
            <span>{{ moment.meta }}</span>
          </div>
        </li>
      </ul>
    </article>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WinProbabilityCard from './WinProbabilityCard.vue'
import type { TrendEventKind, TrendMetrics, TrendModel } from './types'
import { clockToSeconds, displayDescription, periodLabel } from './trendUtils'

const props = defineProps<{
  model: TrendModel
  metrics: TrendMetrics
  homeTeam: string
  awayTeam: string
}>()

const homeRatio = computed(() => {
  const total = Math.max(1, props.metrics.maxHomeLead + props.metrics.maxAwayLead)
  return props.metrics.maxHomeLead / total
})

const criticalShotCount = computed(() =>
  props.model.markers.filter((marker) => marker.isKeyMoment || ['three', 'dunk', 'block', 'steal'].includes(marker.kind)).length
)

const comebackCount = computed(() => props.model.points.filter((point) => point.keyLabel === '反超').length)
const finalFiveClass = computed(() => (props.metrics.finalFiveTrend >= 0 ? 'home' : 'away'))
const finalFiveText = computed(() => {
  const trend = props.metrics.finalFiveTrend
  if (trend === 0) return '持平'
  const team = trend > 0 ? props.homeTeam || '主队' : props.awayTeam || '客队'
  return `${trend > 0 ? '+' : ''}${trend} ${team}`
})

const finalFiveBars = computed(() => {
  const lastPeriod = Math.max(4, props.model.points.at(-1)?.period ?? 4)
  const source = props.model.points
    .filter((point) => point.period >= lastPeriod && clockToSeconds(point.gameClock) <= 300)
    .slice(-12)

  const points = source.length ? source : props.model.points.slice(-12)
  const max = Math.max(1, ...points.map((point) => Math.abs(point.signedMargin)))
  return points.map((point, index) => ({
    index,
    height: Math.max(7, Math.round((Math.abs(point.signedMargin) / max) * 32)),
  }))
})

const keyMoments = computed(() => {
  const fromMarkers = props.model.markers
    .filter((marker) => marker.isKeyMoment)
    .slice(0, 5)
    .map((marker) => ({
      id: marker.event.id,
      kind: marker.kind,
      badge: badgeForKind(marker.kind),
      title: displayDescription(marker.event),
      meta: `${periodLabel(marker.event.period)} ${marker.event.gameClock} · ${marker.event.homeScore}-${marker.event.awayScore}`,
    }))

  if (fromMarkers.length) return fromMarkers

  return props.model.points
    .filter((point) => point.keyLabel)
    .slice(0, 5)
    .map((point) => ({
      id: point.id,
      kind: 'run' as TrendEventKind,
      badge: 'R',
      title: point.keyLabel || '关键走势',
      meta: `${periodLabel(point.period)} ${point.gameClock} · ${point.homeScore}-${point.awayScore}`,
    }))
})

function badgeForKind(kind: TrendEventKind): string {
  if (kind === 'three') return '3'
  if (kind === 'turnover') return '!'
  if (kind === 'timeout') return 'T'
  if (kind === 'block') return 'B'
  if (kind === 'steal') return 'S'
  return '●'
}
</script>

<style scoped>
.trend-overview {
  display: grid;
  align-content: start;
  gap: 10px;
}

.overview-panel {
  padding: 14px;
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  background: rgba(var(--bg-card-rgb), 0.6);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(4px);
}

.overview-panel-main {
  display: grid;
  gap: 12px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head div {
  display: grid;
  gap: 3px;
}

.panel-head strong {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 950;
}

.panel-head span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.panel-head > i {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  color: var(--text-muted);
  background: rgba(var(--bg-card-rgb), 0.8);
  font-style: normal;
  font-size: 11px;
  font-weight: 950;
}

.compact {
  margin-bottom: 12px;
}

.lead-compare {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 66px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.lead-side {
  display: grid;
  gap: 4px;
}

.lead-side.right {
  text-align: right;
}

.lead-side small,
.lead-side span {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lead-side b {
  font-size: 24px;
  font-weight: 950;
  line-height: 1;
}

.home {
  color: var(--gi-home);
}

.away {
  color: var(--accent-light);
}

.lead-donut {
  position: relative;
  display: grid;
  width: 66px;
  height: 66px;
  place-items: center;
  border-radius: var(--radius-full);
  background: conic-gradient(var(--gi-home) 0 var(--home-ratio), var(--accent-light) var(--home-ratio) 360deg);
}

.lead-donut::after {
  position: absolute;
  inset: 12px;
  border-radius: inherit;
  background: var(--bg-panel);
  content: '';
}

.lead-donut em {
  position: relative;
  z-index: 1;
  color: var(--text-primary);
  font-style: normal;
  font-size: 17px;
  font-weight: 950;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.metric-grid div {
  display: grid;
  gap: 5px;
  min-height: 56px;
  padding: 9px 8px 9px 13px;
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  background: rgba(var(--bg-card-rgb), 0.7);
  backdrop-filter: blur(4px);
  position: relative;
  overflow: hidden;
}

.metric-grid div::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--gi-home);
  opacity: 0.5;
}

.metric-grid b {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 950;
}

.metric-grid span,
.run-card span,
.mini-trend span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 850;
}

.run-card {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  background: rgba(var(--bg-card-rgb), 0.7);
  backdrop-filter: blur(4px);
  margin-bottom: 12px;
}

.run-card strong {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 950;
}

.mini-trend {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: end;
  gap: 14px;
}

.mini-trend > div:first-child {
  display: grid;
  gap: 5px;
}

.mini-trend strong {
  font-size: 16px;
  font-weight: 950;
}

.mini-bars {
  display: flex;
  align-items: end;
  justify-content: flex-end;
  min-height: 34px;
  gap: 4px;
}

.mini-bars i {
  width: 8px;
  border-radius: var(--radius-full) var(--radius-full) var(--radius-sm) var(--radius-sm);
  background: var(--gi-home);
}

.mini-bars.away i {
  background: var(--accent-light);
}

.moment-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.moment-list li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  background: rgba(var(--bg-card-rgb), 0.7);
  backdrop-filter: blur(4px);
}

.moment-list i {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: var(--radius-full);
  color: var(--text-primary);
  background: rgba(16, 185, 129, 0.18);
  font-style: normal;
  font-size: 11px;
  font-weight: 950;
}

.moment-list i.turnover {
  background: rgba(239, 68, 68, 0.15);
}

.moment-list i.three,
.moment-list i.steal {
  background: rgba(16, 185, 129, 0.18);
}

.moment-list i.timeout,
.moment-list i.foul {
  background: rgba(245, 158, 11, 0.15);
}

.moment-list strong,
.moment-list span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.moment-list strong {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
}

.moment-list span {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 750;
}

@media (max-width: 1180px) {
  .trend-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .trend-overview {
    grid-template-columns: 1fr;
  }
}
</style>
