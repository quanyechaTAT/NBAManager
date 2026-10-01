<template>
  <div class="page">
    <div class="page-inner">
      <!-- 页面头部 -->
      <SportsHeader
        eyebrow="NBA STANDINGS & RANKINGS"
        title="球队战绩与分区排位"
        subtitle="东西部 30 支球队战绩胜率、主客场分差与季后赛资格争夺线实时透视"
      />
      <!-- 顶部 Tab + 赛季选择 -->
      <div class="rank-toolbar">
        <el-segmented
          v-model="conferenceView"
          :options="[{ label: '东西部排行榜', value: 'full' }, { label: '分区排行榜', value: 'division' }]"
          @change="(v: any) => switchView(v)"
        />
        <el-segmented
          v-if="isMobile && isCurrentSeason"
          v-model="mobileStatsView"
          :options="[{ label: '基础战绩', value: 'basic' }, { label: '进阶数据', value: 'advanced' }]"
        />
        <el-select v-model="selectedSeason" @change="onSeasonChange" style="width: 180px" size="default">
          <el-option v-for="s in availableSeasons" :key="s" :value="s">
            {{ formatSeasonLabel(s) }}
            <el-tag v-if="s === currentSeason" size="small" type="success" style="margin-left: 6px">当前</el-tag>
          </el-option>
        </el-select>
      </div>

      <!-- 东西部排行榜视图 -->
      <template v-if="conferenceView === 'full'">
        <section class="conf-section" v-loading="rankLoading">
          <div class="conf-header">
            <h2>东部联盟 (Eastern Conference)</h2>
            <span class="conf-count">{{ eastStandings.length }} 支球队</span>
          </div>
          <div class="table-wrap">
            <table class="rank-table" :class="{ 'has-extra': isCurrentSeason, 'is-advanced': showMobileAdvanced }">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th class="col-team">球队</th>
                  <th class="col-w">胜 W</th>
                  <th class="col-l">负 L</th>
                  <th class="col-gb">胜场差 GB</th>
                  <th class="col-pct">胜率 PCT</th>
                  <template v-if="isCurrentSeason && showAdvancedCols">
                    <th class="col-home">主场 HOME</th>
                    <th class="col-away">客场 AWAY</th>
                    <th class="col-ppg">得分 PPG</th>
                    <th class="col-oppg">失分 OPPG</th>
                    <th class="col-net">净胜 DIFF</th>
                    <th class="col-streak">近况 STRK</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(t, i) in eastStandings" :key="t.teamName" :class="standingsRowTier(i)" @click="goTeamDetail(t.teamName)">
                  <td class="col-rank"><span class="rank-num" :class="{ 'top3': i < 3 }">{{ i + 1 }}</span></td>
                  <td class="col-team">
                    <img v-if="getTeamLogo(t.teamName)" :src="getTeamLogo(t.teamName)" class="team-logo" alt="" />
                    <span class="team-name">{{ t.teamName }}</span>
                  </td>
                  <td class="col-w">{{ t.wins }}</td>
                  <td class="col-l">{{ t.losses }}</td>
                  <td class="col-gb">{{ formatGb(t.gamesBehind) }}</td>
                  <td class="col-pct">{{ winPct(t.wins, t.losses) }}</td>
                  <template v-if="isCurrentSeason && showAdvancedCols">
                    <td class="col-home">{{ t.homeRecord || '—' }}</td>
                    <td class="col-away">{{ t.awayRecord || '—' }}</td>
                    <td class="col-ppg">{{ t.pointsPerGame != null ? t.pointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-oppg">{{ t.oppPointsPerGame != null ? t.oppPointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-net" :class="{ 'net-pos': (t.netRating ?? 0) > 0, 'net-neg': (t.netRating ?? 0) < 0 }">{{ t.netRating != null ? (t.netRating > 0 ? '+' : '') + t.netRating.toFixed(1) : '—' }}</td>
                    <td class="col-streak"><span class="streak-pill" :class="{ 'streak-w': t.streak?.includes('连胜'), 'streak-l': t.streak?.includes('连败') }">{{ t.streak || '—' }}</span></td>
                  </template>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="conf-section">
          <div class="conf-header">
            <h2>西部联盟 (Western Conference)</h2>
            <span class="conf-count">{{ westStandings.length }} 支球队</span>
          </div>
          <div class="table-wrap">
            <table class="rank-table" :class="{ 'has-extra': isCurrentSeason, 'is-advanced': showMobileAdvanced }">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th class="col-team">球队</th>
                  <th class="col-w">胜 W</th>
                  <th class="col-l">负 L</th>
                  <th class="col-gb">胜场差 GB</th>
                  <th class="col-pct">胜率 PCT</th>
                  <template v-if="isCurrentSeason && showAdvancedCols">
                    <th class="col-home">主场 HOME</th>
                    <th class="col-away">客场 AWAY</th>
                    <th class="col-ppg">得分 PPG</th>
                    <th class="col-oppg">失分 OPPG</th>
                    <th class="col-net">净胜 DIFF</th>
                    <th class="col-streak">近况 STRK</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(t, i) in westStandings" :key="t.teamName" :class="standingsRowTier(i)" @click="goTeamDetail(t.teamName)">
                  <td class="col-rank"><span class="rank-num" :class="{ 'top3': i < 3 }">{{ i + 1 }}</span></td>
                  <td class="col-team">
                    <img v-if="getTeamLogo(t.teamName)" :src="getTeamLogo(t.teamName)" class="team-logo" alt="" />
                    <span class="team-name">{{ t.teamName }}</span>
                  </td>
                  <td class="col-w">{{ t.wins }}</td>
                  <td class="col-l">{{ t.losses }}</td>
                  <td class="col-gb">{{ formatGb(t.gamesBehind) }}</td>
                  <td class="col-pct">{{ winPct(t.wins, t.losses) }}</td>
                  <template v-if="isCurrentSeason && showAdvancedCols">
                    <td class="col-home">{{ t.homeRecord || '—' }}</td>
                    <td class="col-away">{{ t.awayRecord || '—' }}</td>
                    <td class="col-ppg">{{ t.pointsPerGame != null ? t.pointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-oppg">{{ t.oppPointsPerGame != null ? t.oppPointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-net" :class="{ 'net-pos': (t.netRating ?? 0) > 0, 'net-neg': (t.netRating ?? 0) < 0 }">{{ t.netRating != null ? (t.netRating > 0 ? '+' : '') + t.netRating.toFixed(1) : '—' }}</td>
                    <td class="col-streak"><span class="streak-pill" :class="{ 'streak-w': t.streak?.includes('连胜'), 'streak-l': t.streak?.includes('连败') }">{{ t.streak || '—' }}</span></td>
                  </template>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <div class="standings-legend">
          <span class="legend-item"><span class="legend-dot legend-dot--playoff"></span>季后赛直通区 (前 6 名)</span>
          <span class="legend-item"><span class="legend-dot legend-dot--playin"></span>附加赛资格区 (第 7–10 名)</span>
          <span class="legend-item"><span class="legend-dot legend-dot--lottery"></span>乐透抽签区 (第 11–15 名)</span>
        </div>
      </template>

      <!-- 分区排行榜视图 -->
      <template v-if="conferenceView === 'division'">
        <section class="conf-section" v-loading="divLoading" v-for="(teams, divName) in divisionStandings" :key="divName">
          <div class="conf-header">
            <h2>{{ divName }}赛区</h2>
            <span class="conf-count">{{ teams.length }} 支球队</span>
          </div>
          <div class="table-wrap">
            <table class="rank-table" :class="{ 'has-extra': isCurrentSeason, 'is-advanced': showMobileAdvanced }">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th class="col-team">球队</th>
                  <th class="col-w">胜 W</th>
                  <th class="col-l">负 L</th>
                  <th class="col-gb">胜场差 GB</th>
                  <th class="col-pct">胜率 PCT</th>
                  <template v-if="isCurrentSeason && showAdvancedCols">
                    <th class="col-home">主场 HOME</th>
                    <th class="col-away">客场 AWAY</th>
                    <th class="col-ppg">得分 PPG</th>
                    <th class="col-oppg">失分 OPPG</th>
                    <th class="col-net">净胜 DIFF</th>
                    <th class="col-streak">近况 STRK</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(t, i) in teams" :key="t.teamName" @click="goTeamDetail(t.teamName)">
                  <td class="col-rank"><span class="rank-num" :class="{ 'top3': i < 3 }">{{ i + 1 }}</span></td>
                  <td class="col-team">
                    <img v-if="getTeamLogo(t.teamName)" :src="getTeamLogo(t.teamName)" class="team-logo" alt="" />
                    <span class="team-name">{{ t.teamName }}</span>
                  </td>
                  <td class="col-w">{{ t.wins }}</td>
                  <td class="col-l">{{ t.losses }}</td>
                  <td class="col-gb">{{ formatGb(t.gamesBehind) }}</td>
                  <td class="col-pct">{{ winPct(t.wins, t.losses) }}</td>
                  <template v-if="isCurrentSeason && showAdvancedCols">
                    <td class="col-home">{{ t.homeRecord || '—' }}</td>
                    <td class="col-away">{{ t.awayRecord || '—' }}</td>
                    <td class="col-ppg">{{ t.pointsPerGame != null ? t.pointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-oppg">{{ t.oppPointsPerGame != null ? t.oppPointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-net" :class="{ 'net-pos': (t.netRating ?? 0) > 0, 'net-neg': (t.netRating ?? 0) < 0 }">{{ t.netRating != null ? (t.netRating > 0 ? '+' : '') + t.netRating.toFixed(1) : '—' }}</td>
                    <td class="col-streak"><span class="streak-pill" :class="{ 'streak-w': t.streak?.includes('连胜'), 'streak-l': t.streak?.includes('连败') }">{{ t.streak || '—' }}</span></td>
                  </template>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRankings, fetchDivisionRankings } from '@/api/team'
import { getAvailableSeasons, getHistoricalRankings, getHistoricalDivisionRankings } from '@/api/historical'
import type { TeamRank } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import SportsHeader from '@/components/common/SportsHeader.vue'

const router = useRouter()
function standingsRowTier(i: number) {
  if (i < 6) return 'standings-tier--playoff'
  if (i < 10) return 'standings-tier--playin'
  return 'standings-tier--lottery'
}
const conferenceView = ref<'full' | 'division'>('full')
const eastStandings = ref<TeamRank[]>([])
const westStandings = ref<TeamRank[]>([])
const divisionStandings = ref<Record<string, TeamRank[]>>({})
const rankLoading = ref(false)
const divLoading = ref(false)

// 赛季选择
const currentSeason = ref('')
const availableSeasons = ref<string[]>([])
const isMobile = ref(window.innerWidth <= 768)
const mobileStatsView = ref<'basic' | 'advanced'>('basic')
const showAdvancedCols = computed(() => isCurrentSeason.value && (!isMobile.value || mobileStatsView.value === 'advanced'))
const showMobileAdvanced = computed(() => isCurrentSeason.value && isMobile.value && mobileStatsView.value === 'advanced')
function updateMobileWidth() { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', updateMobileWidth))
onUnmounted(() => window.removeEventListener('resize', updateMobileWidth))
const selectedSeason = ref('')

const isCurrentSeason = computed(() => selectedSeason.value === currentSeason.value)

function formatSeasonLabel(season: string): string {
  if (!season) return ''
  const parts = season.split('-')
  if (parts.length === 2) {
    return `${parts[0]}-${parts[1]} 常规赛`
  }
  return season
}

function goTeamDetail(teamName: string) {
  router.push({ path: '/teams/detail', query: { name: teamName } })
}

function winPct(w: number, l: number) {
  const t = w + l
  return t === 0 ? '0.0%' : ((w / t) * 100).toFixed(1) + '%'
}

async function loadRankings() {
  rankLoading.value = true
  try {
    if (isCurrentSeason.value) {
      const { data } = await fetchRankings()
      eastStandings.value = data?.东部 ?? data?.eastern ?? []
      westStandings.value = data?.西部 ?? data?.western ?? []
    } else {
      const data = await getHistoricalRankings(selectedSeason.value)
      eastStandings.value = data?.东部 ?? data?.eastern ?? []
      westStandings.value = data?.西部 ?? data?.western ?? []
    }
  } catch {
    ElMessage.error('加载排名失败')
  } finally {
    rankLoading.value = false
  }
}

async function loadDivisionRankings() {
  divLoading.value = true
  try {
    if (isCurrentSeason.value) {
      const { data } = await fetchDivisionRankings()
      divisionStandings.value = data || {}
    } else {
      const data = await getHistoricalDivisionRankings(selectedSeason.value)
      divisionStandings.value = data || {}
    }
  } catch {
    ElMessage.error('加载分区排名失败')
  } finally {
    divLoading.value = false
  }
}

/** 胜场差统一显示一位小数，缺失为 — */
function formatGb(v: number | null | undefined): string {
  if (v == null) return '—'
  return v.toFixed(1)
}

function switchView(view: 'full' | 'division') {
  conferenceView.value = view
  if (view === 'division' && Object.keys(divisionStandings.value).length === 0) {
    loadDivisionRankings()
  }
}

async function onSeasonChange() {
  divisionStandings.value = {}
  await loadRankings()
  if (conferenceView.value === 'division') {
    await loadDivisionRankings()
  }
}

onMounted(async () => {
  try {
    const seasonsData = await getAvailableSeasons()
    currentSeason.value = seasonsData.current
    availableSeasons.value = seasonsData.available || []
    selectedSeason.value = currentSeason.value
  } catch {
    currentSeason.value = '2025-26'
    selectedSeason.value = '2025-26'
    availableSeasons.value = ['2025-26']
  }
  await loadRankings()
})
</script>

<style scoped>
/* ===== 页面基础 ===== */
.page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 24px;
  min-height: calc(100vh - 108px);
  animation: pageFadeIn 0.3s ease forwards;
  opacity: 0;
  transform: translateY(8px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

/* ===== 页面头部 ===== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-header h1 {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.4px;
}
.page-header p { margin: 0; font-size: 14px; color: var(--text-muted); }

/* ===== 顶部工具栏 ===== */
.rank-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.rank-tabs {
  display: flex;
  gap: 0;
  background: var(--bg-hover);
  border-radius: 10px;
  padding: 3px;
  border: 1px solid var(--border-light);
}
.rank-tab {
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s var(--ease-out-quart, ease);
  white-space: nowrap;
}
.rank-tab.active {
  background: var(--accent);
  color: #04202A;
  font-weight: 700;
}
.rank-tab:hover:not(.active) {
  color: var(--text-primary);
  background: var(--bg-card);
  transform: translateY(-1px);
}

/* ===== 联盟/赛区区块 ===== */
.conf-section { margin-bottom: 24px; }
.conf-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-light);
}
.conf-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.conf-count { font-size: 12px; color: var(--text-muted); }

/* ===== 表格容器 ===== */
.table-wrap {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border: 1px solid var(--border-light);
  border-radius: 14px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
  position: relative;
}
.table-wrap::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.55), transparent);
  opacity: 0.55;
  pointer-events: none;
  z-index: 3;
}

/* ===== 表格基础 ===== */
.rank-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  table-layout: fixed;
}

/* ===== 基础列宽（6列） ===== */
.rank-table .col-rank { width: 7%; }
.rank-table .col-team { width: 28%; }
.rank-table .col-w { width: 10%; }
.rank-table .col-l { width: 10%; }
.rank-table .col-gb { width: 15%; }
.rank-table .col-pct { width: 15%; }

/* ===== 扩展列宽（12列，当前赛季） ===== */
.rank-table.has-extra .col-rank { width: 5%; }
.rank-table.has-extra .col-team { width: 18%; }
.rank-table.has-extra .col-w { width: 6%; }
.rank-table.has-extra .col-l { width: 6%; }
.rank-table.has-extra .col-gb { width: 8%; }
.rank-table.has-extra .col-pct { width: 8%; }
.rank-table.has-extra .col-home { width: 8%; }
.rank-table.has-extra .col-away { width: 8%; }
.rank-table.has-extra .col-ppg { width: 8%; }
.rank-table.has-extra .col-oppg { width: 8%; }
.rank-table.has-extra .col-net { width: 8%; }
.rank-table.has-extra .col-streak { width: 10%; }

/* ===== 表头 ===== */
.rank-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
}
.rank-table thead tr {
  background: var(--bg-hover);
}
.rank-table th {
  padding: 8px 6px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  white-space: nowrap;
  border-bottom: 1px solid var(--border-light);
  letter-spacing: 0.3px;
  vertical-align: middle;
}

/* ===== 数据行 ===== */
.rank-table tbody tr {
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease;
}
.rank-table tbody tr:hover {
  background: var(--bg-hover);
}
.rank-table tbody tr:last-child { border-bottom: none; }

/* 战绩分档指示 (Standings Tier Dividers) */
.standings-tier--playoff {
  border-left: 3px solid var(--zone-playoff, #10B981);
}
.standings-tier--playin {
  border-left: 3px solid var(--zone-playin, #F59E0B);
}
.standings-tier--lottery {
  border-left: 3px solid var(--zone-lottery, #64748B);
}
.conf-section tbody tr:nth-child(6) {
  border-bottom: 2px solid rgba(16, 185, 129, 0.45);
}
.conf-section tbody tr:nth-child(10) {
  border-bottom: 2px solid rgba(245, 158, 11, 0.45);
}
.rank-table td {
  padding: 8px 6px;
  color: var(--text-primary);
  white-space: nowrap;
  vertical-align: middle;
}

/* ===== 列对齐 ===== */
.col-rank { text-align: center; }
.col-team { text-align: left; }
.col-w, .col-l, .col-gb, .col-home, .col-away, .col-ppg, .col-oppg, .col-net, .col-streak {
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
}
.col-pct {
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
}

/* ===== 排名数字 ===== */
.rank-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
}
.rank-num.top3 {
  color: var(--danger);
  font-weight: 700;
  font-size: 14px;
}

/* ===== 球队列 ===== */
.col-team {
  display: flex;
  align-items: center;
  gap: 8px;
}
.team-logo {
  width: 22px;
  height: 22px;
  object-fit: contain;
  flex-shrink: 0;
}
.team-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 13px;
}

/* ===== 净胜分/连胜颜色与竞技徽章 ===== */
.net-pos { color: var(--success); font-weight: 600; }
.net-neg { color: var(--danger); font-weight: 600; }

.streak-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 7px;
  border-radius: var(--radius-sm, 6px);
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}
.streak-pill.streak-w {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.32);
  color: #10B981;
}
.streak-pill.streak-l {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.32);
  color: #EF4444;
}

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  .rank-toolbar { flex-direction: column; gap: 12px; align-items: flex-start; }
}

/* 手机端：不再压缩 12 列，改为「身份 + 关键战绩」的紧凑行 */
@media (max-width: 768px) {
  .rank-toolbar { width: 100%; }
  .rank-toolbar :deep(.el-segmented),
  .rank-toolbar :deep(.el-select) { width: 100% !important; }

  .table-wrap { overflow-x: visible; }

  .rank-table { font-size: 13px; }
  .rank-table td, .rank-table th { padding: 9px 4px; }

  /* 隐藏进阶列：手机端一次只看关键数据，避免逐列挤压 */
  .rank-table .col-gb,
  .rank-table .col-home,
  .rank-table .col-away,
  .rank-table .col-ppg,
  .rank-table .col-oppg,
  .rank-table .col-net,
  .rank-table .col-streak { display: none; }

  /* 手机端的列宽：排名 + 球队 + 胜负 + 胜率 */
  .rank-table .col-rank,
  .rank-table.has-extra .col-rank { width: 34px; }
  .rank-table .col-team,
  .rank-table.has-extra .col-team { width: auto; }
  .rank-table .col-w,
  .rank-table.has-extra .col-w { width: 40px; }
  .rank-table .col-l,
  .rank-table.has-extra .col-l { width: 40px; }
  .rank-table .col-pct,
  .rank-table.has-extra .col-pct { width: 58px; }

  .rank-table th { font-size: 11px; }
  .rank-table td { white-space: nowrap; }
  /* 球队名允许收缩，长队名以省略号结束而不是撑破表格 */
  .rank-table .col-team .team-name { overflow: hidden; text-overflow: ellipsis; }

  /* 进阶视图展示得分、净胜分与近期走势，基础胜负列让出空间。 */
  .rank-table.is-advanced .col-w,
  .rank-table.is-advanced .col-l,
  .rank-table.is-advanced .col-pct { display: none; }
  .rank-table.is-advanced .col-ppg,
  .rank-table.is-advanced .col-net,
  .rank-table.is-advanced .col-streak { display: table-cell; }
  .rank-table.is-advanced .col-ppg,
  .rank-table.is-advanced .col-net { width: 48px; }
  .rank-table.is-advanced .col-streak { width: 66px; }
}

.standings-legend {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 12px 18px;
  margin-top: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  font-size: 12px;
  color: var(--text-secondary);
  flex-wrap: wrap;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}
.legend-dot--playoff { background: var(--zone-playoff); }
.legend-dot--playin { background: var(--zone-playin); }
.legend-dot--lottery { background: var(--zone-lottery); }
</style>
