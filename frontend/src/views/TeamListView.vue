<template>
  <div class="page">
    <div class="page-inner">
      <!-- 顶部 Tab + 赛季选择 -->
      <div class="rank-toolbar">
        <div class="rank-tabs">
          <button class="rank-tab" :class="{ active: conferenceView === 'full' }" @click="switchView('full')">东西部排行榜</button>
          <button class="rank-tab" :class="{ active: conferenceView === 'division' }" @click="switchView('division')">分区排行榜</button>
        </div>
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
            <h2>东部联盟</h2>
            <span class="conf-count">{{ eastStandings.length }} 支球队</span>
          </div>
          <div class="table-wrap">
            <table class="rank-table" :class="{ 'has-extra': isCurrentSeason }">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th class="col-team">球队</th>
                  <th class="col-w">胜</th>
                  <th class="col-l">负</th>
                  <th class="col-gb">胜场差</th>
                  <th class="col-pct">胜率</th>
                  <template v-if="isCurrentSeason">
                    <th class="col-home">主场</th>
                    <th class="col-away">客场</th>
                    <th class="col-ppg">得分</th>
                    <th class="col-oppg">失分</th>
                    <th class="col-net">净胜</th>
                    <th class="col-streak">连胜/负</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(t, i) in eastStandings" :key="t.teamName" @click="goTeamDetail(t.teamName)">
                  <td class="col-rank"><span class="rank-num" :class="{ 'top3': i < 3 }">{{ i + 1 }}</span></td>
                  <td class="col-team">
                    <img v-if="getTeamLogo(t.teamName)" :src="getTeamLogo(t.teamName)" class="team-logo" alt="" />
                    <span class="team-name">{{ t.teamName }}</span>
                  </td>
                  <td class="col-w">{{ t.wins }}</td>
                  <td class="col-l">{{ t.losses }}</td>
                  <td class="col-gb">{{ t.gamesBehind > 0 ? t.gamesBehind.toFixed(1) : '—' }}</td>
                  <td class="col-pct">{{ winPct(t.wins, t.losses) }}</td>
                  <template v-if="isCurrentSeason">
                    <td class="col-home">{{ t.homeRecord || '—' }}</td>
                    <td class="col-away">{{ t.awayRecord || '—' }}</td>
                    <td class="col-ppg">{{ t.pointsPerGame != null ? t.pointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-oppg">{{ t.oppPointsPerGame != null ? t.oppPointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-net" :class="{ 'net-pos': (t.netRating ?? 0) > 0, 'net-neg': (t.netRating ?? 0) < 0 }">{{ t.netRating != null ? (t.netRating > 0 ? '+' : '') + t.netRating.toFixed(1) : '—' }}</td>
                    <td class="col-streak" :class="{ 'streak-w': t.streak?.includes('连胜'), 'streak-l': t.streak?.includes('连败') }">{{ t.streak || '—' }}</td>
                  </template>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="conf-section">
          <div class="conf-header">
            <h2>西部联盟</h2>
            <span class="conf-count">{{ westStandings.length }} 支球队</span>
          </div>
          <div class="table-wrap">
            <table class="rank-table" :class="{ 'has-extra': isCurrentSeason }">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th class="col-team">球队</th>
                  <th class="col-w">胜</th>
                  <th class="col-l">负</th>
                  <th class="col-gb">胜场差</th>
                  <th class="col-pct">胜率</th>
                  <template v-if="isCurrentSeason">
                    <th class="col-home">主场</th>
                    <th class="col-away">客场</th>
                    <th class="col-ppg">得分</th>
                    <th class="col-oppg">失分</th>
                    <th class="col-net">净胜</th>
                    <th class="col-streak">连胜/负</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(t, i) in westStandings" :key="t.teamName" @click="goTeamDetail(t.teamName)">
                  <td class="col-rank"><span class="rank-num" :class="{ 'top3': i < 3 }">{{ i + 1 }}</span></td>
                  <td class="col-team">
                    <img v-if="getTeamLogo(t.teamName)" :src="getTeamLogo(t.teamName)" class="team-logo" alt="" />
                    <span class="team-name">{{ t.teamName }}</span>
                  </td>
                  <td class="col-w">{{ t.wins }}</td>
                  <td class="col-l">{{ t.losses }}</td>
                  <td class="col-gb">{{ t.gamesBehind > 0 ? t.gamesBehind.toFixed(1) : '—' }}</td>
                  <td class="col-pct">{{ winPct(t.wins, t.losses) }}</td>
                  <template v-if="isCurrentSeason">
                    <td class="col-home">{{ t.homeRecord || '—' }}</td>
                    <td class="col-away">{{ t.awayRecord || '—' }}</td>
                    <td class="col-ppg">{{ t.pointsPerGame != null ? t.pointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-oppg">{{ t.oppPointsPerGame != null ? t.oppPointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-net" :class="{ 'net-pos': (t.netRating ?? 0) > 0, 'net-neg': (t.netRating ?? 0) < 0 }">{{ t.netRating != null ? (t.netRating > 0 ? '+' : '') + t.netRating.toFixed(1) : '—' }}</td>
                    <td class="col-streak" :class="{ 'streak-w': t.streak?.includes('连胜'), 'streak-l': t.streak?.includes('连败') }">{{ t.streak || '—' }}</td>
                  </template>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <!-- 分区排行榜视图 -->
      <template v-if="conferenceView === 'division'">
        <section class="conf-section" v-loading="divLoading" v-for="(teams, divName) in divisionStandings" :key="divName">
          <div class="conf-header">
            <h2>{{ divName }}赛区</h2>
            <span class="conf-count">{{ teams.length }} 支球队</span>
          </div>
          <div class="table-wrap">
            <table class="rank-table" :class="{ 'has-extra': isCurrentSeason }">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th class="col-team">球队</th>
                  <th class="col-w">胜</th>
                  <th class="col-l">负</th>
                  <th class="col-gb">胜场差</th>
                  <th class="col-pct">胜率</th>
                  <template v-if="isCurrentSeason">
                    <th class="col-home">主场</th>
                    <th class="col-away">客场</th>
                    <th class="col-ppg">得分</th>
                    <th class="col-oppg">失分</th>
                    <th class="col-net">净胜</th>
                    <th class="col-streak">连胜/负</th>
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
                  <td class="col-gb">{{ t.gamesBehind > 0 ? t.gamesBehind.toFixed(1) : '—' }}</td>
                  <td class="col-pct">{{ winPct(t.wins, t.losses) }}</td>
                  <template v-if="isCurrentSeason">
                    <td class="col-home">{{ t.homeRecord || '—' }}</td>
                    <td class="col-away">{{ t.awayRecord || '—' }}</td>
                    <td class="col-ppg">{{ t.pointsPerGame != null ? t.pointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-oppg">{{ t.oppPointsPerGame != null ? t.oppPointsPerGame.toFixed(1) : '—' }}</td>
                    <td class="col-net" :class="{ 'net-pos': (t.netRating ?? 0) > 0, 'net-neg': (t.netRating ?? 0) < 0 }">{{ t.netRating != null ? (t.netRating > 0 ? '+' : '') + t.netRating.toFixed(1) : '—' }}</td>
                    <td class="col-streak" :class="{ 'streak-w': t.streak?.includes('连胜'), 'streak-l': t.streak?.includes('连败') }">{{ t.streak || '—' }}</td>
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
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRankings, fetchDivisionRankings } from '@/api/team'
import { getAvailableSeasons, getHistoricalRankings, getHistoricalDivisionRankings } from '@/api/historical'
import type { TeamRank } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'

const router = useRouter()
const conferenceView = ref<'full' | 'division'>('full')
const eastStandings = ref<TeamRank[]>([])
const westStandings = ref<TeamRank[]>([])
const divisionStandings = ref<Record<string, TeamRank[]>>({})
const rankLoading = ref(false)
const divLoading = ref(false)

// 赛季选择
const currentSeason = ref('')
const availableSeasons = ref<string[]>([])
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  min-height: calc(100vh - 108px);
  animation: pageFadeIn 0.3s ease forwards;
  opacity: 0;
  transform: translateY(8px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

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
  border-radius: 8px;
  padding: 3px;
}
.rank-tab {
  padding: 8px 20px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.rank-tab.active {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}
.rank-tab:hover:not(.active) {
  color: var(--text-primary);
  background: var(--bg-card);
}

/* ===== 联盟/赛区区块 ===== */
.conf-section { margin-bottom: 24px; }
.conf-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--accent);
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
  border-radius: var(--radius-lg);
  background: var(--bg-card);
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
  transition: background 0.15s ease;
}
.rank-table tbody tr:last-child { border-bottom: none; }
.rank-table tbody tr:hover { background: var(--bg-hover); }
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

/* ===== 净胜分/连胜颜色 ===== */
.net-pos { color: var(--success); }
.net-neg { color: var(--danger); }
.streak-w { color: var(--success); font-weight: 600; }
.streak-l { color: var(--danger); font-weight: 600; }

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  .rank-toolbar { flex-direction: column; gap: 12px; align-items: flex-start; }
}
@media (max-width: 768px) {
  .rank-table { font-size: 12px; }
  .rank-table td, .rank-table th { padding: 6px 4px; }
}
</style>
