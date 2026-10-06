<template>
  <header class="chat-header">
    <div class="chat-header-main">
      <div class="header-left">
        <div class="header-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </div>
        <div class="header-meta">
          <div class="title-row">
            <h2 class="header-title">观赛互动</h2>
            <span class="live-pill" title="现场连线实时同步">
              <span class="live-pulse"></span>
              LIVE 实况
            </span>
          </div>
          <div class="status-row">
            <span class="connection-badge" :class="{ 'is-connected': connected, 'is-reconnecting': reconnecting }">
              <span class="connection-dot"></span>
              {{ reconnecting ? '重连中' : connected ? '已连线' : '未连接' }}
            </span>
          </div>
        </div>
      </div>

      <div class="header-right">
        <div class="online-card" title="当前聊天室在线球迷数">
          <span class="online-dot"></span>
          <span class="online-count">{{ onlineCount }}</span>
          <span class="online-label">人在场</span>
        </div>
      </div>
    </div>

    <!-- 主客队阵营助威栏 -->
    <div v-if="hasTeams" class="camp-cheer-bar">
      <!-- 客队助威方 -->
      <div class="camp-side away" :class="{ active: selectedCamp === 'AWAY' }">
        <div class="team-info">
          <img v-if="awayLogo" :src="awayLogo" class="team-logo" :alt="awayTeam" />
          <div class="team-name-group">
            <span class="team-name">{{ awayTeam }}</span>
            <span class="cheer-role-tag away-tag">客队</span>
          </div>
        </div>
        <button
          type="button"
          class="cheer-btn away-btn"
          :class="{ 'is-selected': selectedCamp === 'AWAY' }"
          title="为客队助威"
          @click="$emit('cheer', 'AWAY')"
        >
          <span class="cheer-action">助威</span>
          <span class="cheer-val">{{ awayCheerCount }}</span>
        </button>
      </div>

      <!-- 中间阵营分布与切换 -->
      <div class="camp-center">
        <div class="pk-label">
          <span class="pk-pct away-pct">{{ awayPct }}%</span>
          <span class="pk-vs">VS</span>
          <span class="pk-pct home-pct">{{ homePct }}%</span>
        </div>
        <div class="pk-meter-track">
          <div class="pk-meter-away" :style="{ width: `${awayPct}%` }"></div>
          <div class="pk-meter-home" :style="{ width: `${homePct}%` }"></div>
        </div>
        <div class="camp-picker-chips">
          <button
            type="button"
            class="chip-btn away-chip"
            :class="{ active: selectedCamp === 'AWAY' }"
            @click="$emit('selectCamp', 'AWAY')"
          >
            客队
          </button>
          <button
            type="button"
            class="chip-btn neutral-chip"
            :class="{ active: selectedCamp === 'ALL' }"
            @click="$emit('selectCamp', 'ALL')"
          >
            全员
          </button>
          <button
            type="button"
            class="chip-btn home-chip"
            :class="{ active: selectedCamp === 'HOME' }"
            @click="$emit('selectCamp', 'HOME')"
          >
            主队
          </button>
        </div>
      </div>

      <!-- 主队助威方 -->
      <div class="camp-side home" :class="{ active: selectedCamp === 'HOME' }">
        <div class="team-info">
          <img v-if="homeLogo" :src="homeLogo" class="team-logo" :alt="homeTeam" />
          <div class="team-name-group">
            <span class="team-name">{{ homeTeam }}</span>
            <span class="cheer-role-tag home-tag">主队</span>
          </div>
        </div>
        <button
          type="button"
          class="cheer-btn home-btn"
          :class="{ 'is-selected': selectedCamp === 'HOME' }"
          title="为主队助威"
          @click="$emit('cheer', 'HOME')"
        >
          <span class="cheer-action">助威</span>
          <span class="cheer-val">{{ homeCheerCount }}</span>
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getTeamLogo } from '@/utils/teamLogos'

const props = withDefaults(
  defineProps<{
    connected: boolean
    reconnecting: boolean
    onlineCount: number
    homeTeam?: string
    awayTeam?: string
    selectedCamp?: 'HOME' | 'AWAY' | 'ALL'
    homeCheerCount?: number
    awayCheerCount?: number
  }>(),
  {
    homeTeam: '',
    awayTeam: '',
    selectedCamp: 'ALL',
    homeCheerCount: 42,
    awayCheerCount: 38,
  }
)

defineEmits<{
  selectCamp: [camp: 'HOME' | 'AWAY' | 'ALL']
  cheer: [team: 'HOME' | 'AWAY']
}>()

const hasTeams = computed(() => Boolean(props.homeTeam || props.awayTeam))

const homeLogo = computed(() => (props.homeTeam ? getTeamLogo(props.homeTeam) : ''))
const awayLogo = computed(() => (props.awayTeam ? getTeamLogo(props.awayTeam) : ''))

const awayPct = computed(() => {
  const sum = props.homeCheerCount + props.awayCheerCount
  if (sum <= 0) return 50
  return Math.round((props.awayCheerCount / sum) * 100)
})
const homePct = computed(() => 100 - awayPct.value)
</script>

<style scoped>
.chat-header {
  display: flex;
  flex-direction: column;
  background: var(--bg-card, #1A1D24);
  border-bottom: 1px solid var(--border-light, rgba(255, 255, 255, 0.08));
  flex-shrink: 0;
}

.chat-header-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--bg-hover, rgba(255, 255, 255, 0.05));
  border: 1px solid var(--border-light, rgba(255, 255, 255, 0.08));
  color: var(--accent, #0891B2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 7px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 999px;
  color: #EF4444;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.live-pulse {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #EF4444;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.connection-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-dim, #94a3b8);
}

.connection-badge.is-connected {
  color: var(--success, #22C55E);
}

.connection-badge.is-reconnecting {
  color: #EAB308;
}

.connection-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.header-right {
  display: flex;
  align-items: center;
}

.online-card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--bg-panel, rgba(255, 255, 255, 0.03));
  border: 1px solid var(--border-light, rgba(255, 255, 255, 0.08));
  border-radius: 6px;
}

.online-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success, #22C55E);
}

.online-count {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.online-label {
  font-size: 11px;
  color: var(--text-dim, #94a3b8);
}

/* 阵营助威栏 */
.camp-cheer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 18px 10px;
  gap: 14px;
  background: var(--bg-hover, rgba(255, 255, 255, 0.02));
  border-top: 1px solid var(--border-light, rgba(255, 255, 255, 0.05));
}

.camp-side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.camp-side.home {
  flex-direction: row-reverse;
  text-align: right;
}

.team-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.camp-side.home .team-info {
  flex-direction: row-reverse;
}

.team-logo {
  width: 24px;
  height: 24px;
  object-fit: contain;
  flex-shrink: 0;
}

.team-name-group {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.team-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cheer-role-tag {
  font-size: 10px;
  color: var(--text-dim);
}

.cheer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border-radius: 6px;
  border: 1px solid var(--border-medium);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  background: var(--bg-card);
  color: var(--text-secondary);
  transition: all 0.12s ease;
  flex-shrink: 0;
}

.away-btn:hover,
.away-btn.is-selected {
  background: rgba(239, 68, 68, 0.14);
  border-color: #EF4444;
  color: #EF4444;
}

.home-btn:hover,
.home-btn.is-selected {
  background: rgba(8, 145, 178, 0.14);
  border-color: var(--accent, #0891B2);
  color: var(--accent, #0891B2);
}

.cheer-val {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

/* PK 进度与中立切换 */
.camp-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 200px;
  flex-shrink: 0;
}

.pk-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.away-pct {
  color: #EF4444;
}

.home-pct {
  color: var(--accent, #0891B2);
}

.pk-vs {
  font-size: 10px;
  color: var(--text-dim);
  font-weight: 700;
}

.pk-meter-track {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: var(--bg-panel, rgba(255, 255, 255, 0.08));
  overflow: hidden;
  display: flex;
}

.pk-meter-away {
  height: 100%;
  background: #EF4444;
  transition: width 0.3s ease;
}

.pk-meter-home {
  height: 100%;
  background: var(--accent, #0891B2);
  transition: width 0.3s ease;
}

.camp-picker-chips {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chip-btn {
  background: transparent;
  border: 1px solid var(--border-light, rgba(255, 255, 255, 0.08));
  color: var(--text-dim);
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  padding: 1px 6px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.chip-btn:hover {
  color: var(--text-primary);
  border-color: var(--border-medium);
}

.chip-btn.active.away-chip {
  background: rgba(239, 68, 68, 0.12);
  border-color: #EF4444;
  color: #EF4444;
}

.chip-btn.active.neutral-chip {
  background: var(--bg-hover);
  border-color: var(--border-medium);
  color: var(--text-primary);
}

.chip-btn.active.home-chip {
  background: rgba(8, 145, 178, 0.12);
  border-color: var(--accent, #0891B2);
  color: var(--accent, #0891B2);
}

@media (max-width: 768px) {
  .camp-cheer-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .camp-center {
    order: -1;
    width: 100%;
  }
}
</style>
