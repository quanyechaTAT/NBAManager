<template>
  <section class="pp-rail" v-if="topPlayers.length">
    <div class="rail-header">
      <div>
        <h4 class="rail-title">球员表现</h4>
        <p class="rail-subtitle">综合评分 Top 10 球员</p>
      </div>
      <span class="rail-count">{{ topPlayers.length }} 人</span>
    </div>

    <div class="rail-scroll" ref="scrollContainer">
      <article
        v-for="(player, idx) in topPlayers"
        :key="player.playerId"
        class="pp-card"
        :class="{
          'is-starter': player.starter,
          'hovered': hoveredId === player.playerId
        }"
        @mouseenter="hoveredId = player.playerId"
        @mouseleave="hoveredId = null"
        @click="goToPlayer(player)"
      >
        <!-- rank ribbon -->
        <div class="rank-ribbon" :class="rankTier(idx)">
          {{ idx + 1 }}
        </div>

        <!-- avatar -->
        <div class="avatar" :class="teamSide(player.teamName)">
          <template v-if="playerAvatarUrl(player)">
            <img :src="playerAvatarUrl(player)" :alt="player.playerName" />
          </template>
          <template v-else>
            <span class="avatar-initials">{{ initials(player.playerName) }}</span>
          </template>
        </div>

        <!-- name + team -->
        <div class="player-identity">
          <span class="player-name">{{ player.playerName }}</span>
          <div class="player-meta">
            <span class="team-label">{{ player.teamName }}</span>
            <span v-if="player.starter" class="starter-badge">首发</span>
          </div>
        </div>

        <!-- primary stats -->
        <div class="primary-stats">
          <div class="stat-block">
            <span class="stat-value">{{ player.points }}</span>
            <span class="stat-label">PTS</span>
          </div>
          <div class="stat-block">
            <span class="stat-value">{{ player.rebounds }}</span>
            <span class="stat-label">REB</span>
          </div>
          <div class="stat-block">
            <span class="stat-value">{{ player.assists }}</span>
            <span class="stat-label">AST</span>
          </div>
        </div>

        <!-- secondary stats (always visible) -->
        <div class="secondary-stats">
          <span class="chip chip-stl">STL {{ player.steals }}</span>
          <span class="chip chip-blk">BLK {{ player.blocks }}</span>
          <span class="chip chip-to">TO {{ player.turnovers }}</span>
        </div>

        <!-- expanded detail on hover -->
        <div v-if="hoveredId === player.playerId" class="expanded-detail">
          <div class="detail-row">
            <span class="detail-label">正负值</span>
            <span class="detail-value" :class="player.plusMinus >= 0 ? 'pm-pos' : 'pm-neg'">
              {{ player.plusMinus > 0 ? '+' : '' }}{{ player.plusMinus }}
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">命中率</span>
            <span class="detail-value fg-pct">
              {{ player.fgMade }}/{{ player.fgAttempted }}
              <span class="pct-hint">({{ player.fgPct }}%)</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">上场</span>
            <span class="detail-value">{{ player.minutes }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">三分</span>
            <span class="detail-value">
              {{ player.threeMade }}/{{ player.threeAttempted }}
              <span class="pct-hint">({{ player.threePct }}%)</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">罚球</span>
            <span class="detail-value">
              {{ player.ftMade }}/{{ player.ftAttempted }}
              <span class="pct-hint">({{ player.ftPct }}%)</span>
            </span>
          </div>
        </div>

        <!-- footer: plusMinus + FG% always -->
        <div class="card-footer">
          <span class="pm-badge" :class="player.plusMinus >= 0 ? 'pm-pos' : 'pm-neg'">
            {{ player.plusMinus > 0 ? '+' : '' }}{{ player.plusMinus }}
          </span>
          <span class="fg-badge">
            {{ player.fgMade }}/{{ player.fgAttempted }} ({{ player.fgPct }}%)
          </span>
        </div>
      </article>
    </div>
  </section>

  <section v-else class="empty-state">
    <div class="empty-icon">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="7" r="4" />
        <path d="M5.5 21a6.5 6.5 0 0 1 13 0" />
      </svg>
    </div>
    <div class="empty-title">暂无球员数据</div>
    <div class="empty-desc">Box Score 数据加载后，球员表现将在此处展示。</div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { BoxScorePlayer } from '@/api/types'

const props = defineProps<{
  homePlayers: BoxScorePlayer[]
  awayPlayers: BoxScorePlayer[]
  homeTeam: string
  awayTeam: string
}>()

const router = useRouter()
const hoveredId = ref<number | null>(null)

/** Combined player list with performance score, sorted descending, top 10 */
const topPlayers = computed(() => {
  const combined = [...props.homePlayers, ...props.awayPlayers]
  const scored = combined.map((p) => ({
    ...p,
    performanceScore:
      p.points +
      p.rebounds * 1.2 +
      p.assists * 1.5 +
      p.steals * 3 +
      p.blocks * 3 -
      p.turnovers * 2,
  }))
  scored.sort((a, b) => b.performanceScore - a.performanceScore)
  return scored.slice(0, 10)
})

function initials(name: string): string {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return name.slice(0, 2).toUpperCase()
}

function teamSide(teamName: string): string {
  if (teamName === props.homeTeam) return 'side-home'
  if (teamName === props.awayTeam) return 'side-away'
  return ''
}

function rankTier(idx: number): string {
  if (idx === 0) return 'rank-gold'
  if (idx === 1) return 'rank-silver'
  if (idx === 2) return 'rank-bronze'
  return ''
}

function playerAvatarUrl(player: BoxScorePlayer): string | null {
  // Placeholder: return null for now (no avatar URL in BoxScorePlayer)
  return null
}

function goToPlayer(player: BoxScorePlayer) {
  router.push({ name: 'player', params: { id: player.playerId } })
}
</script>

<style scoped>
.pp-rail {
  min-width: 0;
}

.rail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.rail-title {
  margin: 0;
  color: var(--gi-text, #f8fafc);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
}

.rail-subtitle {
  margin: 4px 0 0;
  color: var(--gi-muted, #94a3b8);
  font-size: 12px;
}

.rail-count {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  color: #93c5fd;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.rail-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-behavior: smooth;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.2) transparent;
}

.rail-scroll::-webkit-scrollbar {
  height: 5px;
}

.rail-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.rail-scroll::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.2);
  border-radius: 10px;
}

/* ---------- card ---------- */
.pp-card {
  position: relative;
  min-width: 200px;
  max-width: 240px;
  flex-shrink: 0;
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.pp-card.is-starter {
  border-color: rgba(59, 130, 246, 0.2);
}

.pp-card:hover {
  transform: translateY(-3px);
  border-color: rgba(255, 255, 255, 0.14);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}

/* ---------- rank ribbon ---------- */
.rank-ribbon {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(148, 163, 184, 0.1);
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rank-ribbon.rank-gold {
  background: rgba(245, 158, 11, 0.18);
  color: #fbbf24;
}

.rank-ribbon.rank-silver {
  background: rgba(148, 163, 184, 0.16);
  color: #cbd5e1;
}

.rank-ribbon.rank-bronze {
  background: rgba(180, 83, 9, 0.16);
  color: #fbbf24;
}

/* ---------- avatar ---------- */
.avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.1);
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-initials {
  color: #e2e8f0;
  font-size: 14px;
  font-weight: 800;
}

.avatar.side-home {
  background: rgba(59, 130, 246, 0.15);
  color: #93c5fd;
}

.avatar.side-away {
  background: rgba(249, 115, 22, 0.15);
  color: #fdba74;
}

/* ---------- identity ---------- */
.player-identity {
  margin-bottom: 12px;
}

.player-name {
  display: block;
  color: var(--gi-text, #f1f5f9);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}

.team-label {
  color: var(--gi-muted, #94a3b8);
  font-size: 11px;
}

.starter-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(59, 130, 246, 0.12);
  color: #93c5fd;
  font-size: 10px;
  font-weight: 700;
}

/* ---------- primary stats ---------- */
.primary-stats {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
}

.stat-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
}

.stat-value {
  color: var(--gi-text, #f8fafc);
  font-size: 18px;
  font-weight: 800;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  line-height: 1.1;
}

.stat-label {
  color: var(--gi-muted, #94a3b8);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.06em;
  margin-top: 2px;
}

/* ---------- secondary chips ---------- */
.secondary-stats {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.chip {
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 700;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.chip-stl {
  background: rgba(16, 185, 129, 0.1);
  color: #6ee7b7;
}

.chip-blk {
  background: rgba(139, 92, 246, 0.1);
  color: #c4b5fd;
}

.chip-to {
  background: rgba(239, 68, 68, 0.1);
  color: #fca5a5;
}

/* ---------- expanded detail ---------- */
.expanded-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  animation: fadeSlideIn 0.15s ease;
}

@keyframes fadeSlideIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-label {
  color: var(--gi-muted, #94a3b8);
  font-size: 11px;
}

.detail-value {
  color: var(--gi-text, #f1f5f9);
  font-size: 12px;
  font-weight: 600;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.pct-hint {
  color: var(--gi-muted, #94a3b8);
  font-size: 10px;
}

/* ---------- card footer ---------- */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--gi-border, rgba(255, 255, 255, 0.06));
}

.pm-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 800;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.pm-pos {
  background: rgba(16, 185, 129, 0.12);
  color: #6ee7b7;
}

.pm-neg {
  background: rgba(239, 68, 68, 0.12);
  color: #fca5a5;
}

.fg-badge {
  color: var(--gi-muted, #94a3b8);
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

/* ---------- empty state ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  border: 1px dashed var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.34);
  text-align: center;
}

.empty-icon {
  color: #475569;
  margin-bottom: 14px;
}

.empty-title {
  color: #e2e8f0;
  font-size: 15px;
  font-weight: 700;
}

.empty-desc {
  max-width: 320px;
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}
</style>
