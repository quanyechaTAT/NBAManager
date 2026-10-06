<template>
  <div class="page animated-bg">
    <div class="bg-particles"><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div></div>
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <!-- 返回 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回
        </el-button>
      </div>

      <SportsHeader
        eyebrow="HEAD-TO-HEAD SHOWDOWN"
        title="球员数据对比"
        subtitle="并排对比两名现役球员的场均技术统计、效率表现与多维能力画像"
      />

      <!-- 球员选择 -->
      <el-row :gutter="20" class="compare-selectors">
        <el-col :xs="24" :sm="10">
          <el-select
            v-model="player1Id"
            filterable
            remote
            reserve-keyword
            placeholder="输入球员姓名搜索..."
            style="width:100%"
            @change="loadCompare"
            :remote-method="(q: string) => searchPlayers(q, 1)"
            :loading="searching1"
          >
            <el-option
              v-for="p in playerOptions1"
              :key="p.id"
              :label="`${p.name} (${p.teamName})`"
              :value="p.id"
            />
          </el-select>
        </el-col>
        <el-col :xs="24" :sm="4" class="vs-col">
          <span class="vs-badge">VS</span>
        </el-col>
        <el-col :xs="24" :sm="10">
          <el-select
            v-model="player2Id"
            filterable
            remote
            reserve-keyword
            placeholder="输入球员姓名搜索..."
            style="width:100%"
            @change="loadCompare"
            :remote-method="(q: string) => searchPlayers(q, 2)"
            :loading="searching2"
          >
            <el-option
              v-for="p in playerOptions2"
              :key="p.id"
              :label="`${p.name} (${p.teamName})`"
              :value="p.id"
            />
          </el-select>
        </el-col>
      </el-row>

      <!-- 对比结果 -->
      <div v-if="compare" class="compare-content" v-loading="loading">
        <!-- 球员卡片 -->
        <el-row :gutter="20" class="showdown-hero-row">
          <el-col :xs="24" :sm="12">
            <div class="player-card player-card--left glass-card" :style="{ '--p-accent': getTeamPrimaryColor(compare.player1.teamName) }">
              <span class="player-accent-stripe" aria-hidden="true" />
              <img v-if="compare.player1.nbaPlayerId && !brokenPhotos.has(compare.player1.id)" :src="`https://cdn.nba.com/headshots/nba/latest/1040x760/${compare.player1.nbaPlayerId}.png`" class="player-photo" @error="onPhotoError(compare.player1.id)" />
              <div v-else class="player-photo-placeholder player-photo-placeholder--p1">{{ compare.player1.name?.charAt(0) }}</div>
              <div class="player-info">
                <div class="player-sub-meta">
                  <img v-if="getTeamLogo(compare.player1.teamName)" :src="getTeamLogo(compare.player1.teamName)" class="compare-team-logo" alt="" />
                  <span class="compare-team-name">{{ compare.player1.teamName }}</span>
                  <span class="meta-dot">·</span>
                  <span class="pos-badge">{{ compare.player1.position }}</span>
                  <span v-if="compare.player1.jerseyNumber" class="jersey-pill">#{{ compare.player1.jerseyNumber }}</span>
                </div>
                <h3>{{ compare.player1.name }}</h3>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12">
            <div class="player-card player-card--right glass-card" :style="{ '--p-accent': getTeamPrimaryColor(compare.player2.teamName) }">
              <span class="player-accent-stripe" aria-hidden="true" />
              <img v-if="compare.player2.nbaPlayerId && !brokenPhotos.has(compare.player2.id)" :src="`https://cdn.nba.com/headshots/nba/latest/1040x760/${compare.player2.nbaPlayerId}.png`" class="player-photo" @error="onPhotoError(compare.player2.id)" />
              <div v-else class="player-photo-placeholder player-photo-placeholder--p2">{{ compare.player2.name?.charAt(0) }}</div>
              <div class="player-info">
                <div class="player-sub-meta">
                  <img v-if="getTeamLogo(compare.player2.teamName)" :src="getTeamLogo(compare.player2.teamName)" class="compare-team-logo" alt="" />
                  <span class="compare-team-name">{{ compare.player2.teamName }}</span>
                  <span class="meta-dot">·</span>
                  <span class="pos-badge">{{ compare.player2.position }}</span>
                  <span v-if="compare.player2.jerseyNumber" class="jersey-pill">#{{ compare.player2.jerseyNumber }}</span>
                </div>
                <h3>{{ compare.player2.name }}</h3>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 雷达图对比 -->
        <el-card shadow="never" class="chart-card">
          <h3 class="section-title">攻防六维战术能力模型对比 (Tactical Radar)</h3>
          <v-chart class="compare-chart" :option="radarOption" autoresize />
        </el-card>

        <!-- 双向分流对比进度条 (Dual Comparative Bars) -->
        <el-card shadow="never" class="dual-bars-card">
          <div class="bars-card-header">
            <div class="header-player-tag header-player-tag--p1">
              <span class="player-dot dot--p1" />
              <strong>{{ compare.player1.name }}</strong>
            </div>
            <div class="bars-title-center">
              <h3 class="section-title">核心指标双向分流对比 (Comparative Telemetry)</h3>
              <p>各项数据向两侧对称展开 · 领先方指标微光发光</p>
            </div>
            <div class="header-player-tag header-player-tag--p2">
              <strong>{{ compare.player2.name }}</strong>
              <span class="player-dot dot--p2" />
            </div>
          </div>

          <div class="dual-bars-list">
            <div
              v-for="row in compareRows"
              :key="row.label"
              class="dual-bar-row"
              :class="{ 'lead-left': row.winner === 1, 'lead-right': row.winner === 2 }"
            >
              <!-- Player 1 侧 -->
              <div class="bar-side bar-side--left">
                <span class="bar-val font-tabular" :class="{ 'is-winner': row.winner === 1 }">
                  {{ formatVal(row.val1, row.pct, row.alreadyPercent, row.integer) }}
                  <span v-if="row.winner === 1" class="winner-crown" title="领先">&#9650;</span>
                </span>
                <div class="bar-track bar-track--left">
                  <div
                    class="bar-fill bar-fill--left"
                    :class="{ 'is-leading': row.winner === 1 }"
                    :style="{ width: getBarPct(row.val1, getRowMax(row)) + '%' }"
                  />
                </div>
              </div>

              <!-- 中间指标名称 -->
              <div class="bar-label-badge font-tabular">
                {{ row.label }}
              </div>

              <!-- Player 2 侧 -->
              <div class="bar-side bar-side--right">
                <div class="bar-track bar-track--right">
                  <div
                    class="bar-fill bar-fill--right"
                    :class="{ 'is-leading': row.winner === 2 }"
                    :style="{ width: getBarPct(row.val2, getRowMax(row)) + '%' }"
                  />
                </div>
                <span class="bar-val font-tabular" :class="{ 'is-winner': row.winner === 2 }">
                  <span v-if="row.winner === 2" class="winner-crown winner-crown--b" title="领先">&#9650;</span>
                  {{ formatVal(row.val2, row.pct, row.alreadyPercent, row.integer) }}
                </span>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 详细数据对比表：指标名称 | 球员 A | 球员 B -->
        <el-card shadow="never" class="stats-card">
          <h3 class="section-title">全维度常规赛基础与高阶数据对照 (Head-to-Head Metrics)</h3>
          <p class="compare-scroll-hint">左右滑动可查看完整对比</p>
          <el-table :data="compareRows" stripe>
            <el-table-column label="竞技指标 METRIC" prop="label" min-width="140" />
            <el-table-column :label="compare.player1.name" align="right" min-width="100">
              <template #default="{ row }">
                <span class="stat-cell" :class="{ 'winner-left': row.winner === 1 }">{{ formatVal(row.val1, row.pct, row.alreadyPercent, row.integer) }}<span v-if="row.winner === 1" class="win-marker" aria-label="较优">&#9650;</span></span>
              </template>
            </el-table-column>
            <el-table-column :label="compare.player2.name" align="right" min-width="100">
              <template #default="{ row }">
                <span class="stat-cell" :class="{ 'winner-right': row.winner === 2 }">{{ formatVal(row.val2, row.pct, row.alreadyPercent, row.integer) }}<span v-if="row.winner === 2" class="win-marker win-marker--b" aria-label="较优">&#9650;</span></span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading" class="compare-empty">
        <span class="compare-empty__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="26" height="26"><path d="M16 3h5v5"/><path d="M8 21H3v-5"/><path d="M21 3l-7 7"/><path d="M3 21l7-7"/></svg>
        </span>
        <strong>选择两位球员进行数据对比</strong>
        <span>在上方搜索栏分别选择两位球员，即可呈现多维能力雷达与各项技术统计对比</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchPlayers } from '@/api/player'
import { fetchPlayerCompare } from '@/api/playerCompare'
import type { Player } from '@/api/types'
import type { PlayerCompare } from '@/api/playerCompare'
import { useChartTheme } from '@/composables/useChartTheme'
import SportsHeader from '@/components/common/SportsHeader.vue'
import { getTeamLogo } from '@/utils/teamLogos'
import { getTeamPrimaryColor } from '@/utils/teamColors'

function getBarPct(val: number | null | undefined, max: number): number {
  if (val == null || max <= 0) return 0
  return Math.min(Math.round((val / max) * 100), 100)
}

function getRowMax(row: any): number {
  const v1 = Number(row.val1) || 0
  const v2 = Number(row.val2) || 0
  return Math.max(v1, v2, 0.001)
}

use([RadarChart, TitleComponent, TooltipComponent, RadarComponent, CanvasRenderer])

const { colors, tooltipStyle } = useChartTheme()
const route = useRoute()
const router = useRouter()

function goBack() {
  const returnTo = route.query.returnTo as string
  if (returnTo) {
    router.replace(returnTo)
  } else {
    router.replace('/players')
  }
}

const loading = ref(false)
const searching1 = ref(false)
const searching2 = ref(false)
const player1Id = ref<number | null>(null)
const player2Id = ref<number | null>(null)
const compare = ref<PlayerCompare | null>(null)
const playerOptions1 = ref<Player[]>([])
const playerOptions2 = ref<Player[]>([])
const brokenPhotos = ref<Set<number>>(new Set())

// 搜索球员
async function searchPlayers(query: string, slot: number) {
  if (!query || query.length < 1) return

  const searching = slot === 1 ? searching1 : searching2
  const options = slot === 1 ? playerOptions1 : playerOptions2

  searching.value = true
  try {
    const { data } = await fetchPlayers({ page: 0, size: 20, q: query })
    options.value = data.content
  } catch {
    // ignore
  } finally {
    searching.value = false
  }
}

onMounted(async () => {
  // 读取路由参数，预填球员A
  const id1FromQuery = Number(route.query.id1) || null
  if (id1FromQuery) {
    player1Id.value = id1FromQuery
    // 加载球员信息到选项列表，以便显示名称
    try {
      const { data } = await fetchPlayerCompare(id1FromQuery, id1FromQuery)
      if (data?.player1) {
        playerOptions1.value = [{ id: data.player1.id, name: data.player1.name, teamName: data.player1.teamName } as Player]
      }
    } catch { /* ignore */ }
  }
  const id2FromQuery = Number(route.query.id2) || null
  if (id2FromQuery) {
    player2Id.value = id2FromQuery
    // 加载球员信息到选项列表，以便显示名称
    try {
      const { data } = await fetchPlayerCompare(id2FromQuery, id2FromQuery)
      if (data?.player1) {
        playerOptions2.value = [{ id: data.player1.id, name: data.player1.name, teamName: data.player1.teamName } as Player]
      }
    } catch { /* ignore */ }
  }

  // 如果两个球员都已选择，自动加载对比
  if (player1Id.value && player2Id.value) {
    loadCompare()
  }
})

async function loadCompare() {
  if (!player1Id.value || !player2Id.value) return
  loading.value = true
  try {
    const { data } = await fetchPlayerCompare(player1Id.value, player2Id.value)
    compare.value = data
  } catch {
    ElMessage.error('加载对比数据失败')
  } finally {
    loading.value = false
  }
}

const radarOption = computed(() => {
  if (!compare.value) return {}
  const c = colors.value
  const p1 = compare.value.player1
  const p2 = compare.value.player2
  const normalize = (val: number, max: number) => Math.min((val / max) * 100, 100)
  return {
    tooltip: tooltipStyle.value,
    legend: {
      data: [p1.name, p2.name],
      bottom: 0,
      textStyle: { color: c.textMuted, fontSize: 12 },
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
    },
    radar: {
      indicator: [
        { name: '得分终结 (PPG)', max: 100 },
        { name: '篮板冲抢 (RPG)', max: 100 },
        { name: '组织串联 (APG)', max: 100 },
        { name: '外线抢断 (SPG)', max: 100 },
        { name: '护筐盖帽 (BPG)', max: 100 },
        { name: '投篮准星 (FG%)', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: c.textMuted, fontSize: 12 },
      splitLine: { lineStyle: { color: c.axisLine } },
      splitArea: { areaStyle: { color: [c.splitArea1, c.splitArea2] } },
      axisLine: { lineStyle: { color: c.axisLine } },
    },
    series: [{
      type: 'radar',
      data: [
        {
          value: [
            normalize(p1.pointsPerGame, 35),
            normalize(p1.reboundsPerGame, 15),
            normalize(p1.assistsPerGame, 12),
            normalize(p1.stealsPerGame, 3),
            normalize(p1.blocksPerGame, 4),
            normalize(p1.fieldGoalPct, 0.65),
          ],
          name: p1.name,
          areaStyle: { color: `${c.accent}33` },
          lineStyle: { color: c.accent, width: 2 },
          itemStyle: { color: c.accent },
        },
        {
          value: [
            normalize(p2.pointsPerGame, 35),
            normalize(p2.reboundsPerGame, 15),
            normalize(p2.assistsPerGame, 12),
            normalize(p2.stealsPerGame, 3),
            normalize(p2.blocksPerGame, 4),
            normalize(p2.fieldGoalPct, 0.65),
          ],
          name: p2.name,
          areaStyle: { color: `${c.purpleLight}33` },
          lineStyle: { color: c.purpleLight, width: 2 },
          itemStyle: { color: c.purpleLight },
        },
      ],
    }],
  }
})

const compareRows = computed(() => {
  if (!compare.value) return []
  const p1 = compare.value.player1
  const p2 = compare.value.player2
  const stats = [
    { label: '场均得分 PPG', val1: p1.pointsPerGame, val2: p2.pointsPerGame },
    { label: '场均篮板 RPG', val1: p1.reboundsPerGame, val2: p2.reboundsPerGame },
    { label: '场均助攻 APG', val1: p1.assistsPerGame, val2: p2.assistsPerGame },
    { label: '场均抢断 SPG', val1: p1.stealsPerGame, val2: p2.stealsPerGame },
    { label: '场均盖帽 BPG', val1: p1.blocksPerGame, val2: p2.blocksPerGame },
    { label: '场均失误 TO', val1: p1.turnoversPerGame, val2: p2.turnoversPerGame },
    { label: '投篮命中率 FG%', val1: p1.fieldGoalPct, val2: p2.fieldGoalPct, pct: true },
    { label: '三分命中率 3PT%', val1: p1.threePointPct, val2: p2.threePointPct, pct: true },
    { label: '罚球命中率 FT%', val1: p1.freeThrowPct, val2: p2.freeThrowPct, pct: true },
    { label: '综合效率值 EFF', val1: p1.efficiency, val2: p2.efficiency },
    { label: '真实命中率 TS%', val1: p1.trueShootingPct, val2: p2.trueShootingPct, pct: true },
    { label: '回合占有率 USG%', val1: p1.usagePct, val2: p2.usagePct, pct: true, alreadyPercent: true },
    { label: '出场场次 GP', val1: p1.gamesPlayed, val2: p2.gamesPlayed, integer: true },
    { label: '场均时间 MPG', val1: p1.minutesPerGame, val2: p2.minutesPerGame },
  ]
  return stats.map(s => ({
    ...s,
    winner: s.val1 == null || s.val2 == null || s.val1 === s.val2
      ? 0
      : s.label.includes('失误')
        ? (s.val1 < s.val2 ? 1 : 2)
        : (s.val1 > s.val2 ? 1 : 2),
  }))
})

function formatVal(v: number | null | undefined, pct?: boolean, alreadyPercent?: boolean, integer?: boolean) {
  if (v == null) return '—'
  if (pct) return `${(alreadyPercent ? v : v * 100).toFixed(1)}%`
  return integer ? String(Math.round(v)) : v.toFixed(1)
}

function onPhotoError(id: number) {
  brokenPhotos.value.add(id)
}
</script>

<style scoped>
.page { max-width: 1000px; margin: 0 auto; padding: 0 24px; min-height: calc(100vh - 108px); position: relative; border-radius: var(--radius-lg); }
.back-row { margin-bottom: 16px; }
.back-btn { color: var(--text-muted) !important; font-size: 13px !important; }

/* 页面头部 */
.page-header { margin-bottom: 22px; }
.page-header h1 {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.4px;
}
.page-header p { margin: 0; font-size: 14px; color: var(--text-muted); }

.compare-selectors { margin-bottom: 24px; }
.vs-col { display: flex; align-items: center; justify-content: center; }
.vs-badge {
  font-size: 15px;
  font-weight: 800;
  color: var(--accent);
  font-family: var(--font-heading);
  padding: 6px 14px;
  border: 1px solid var(--accent-lighter);
  background: var(--accent-lighter);
  border-radius: var(--radius-full);
  letter-spacing: 1px;
}

.player-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  position: relative;
  overflow: hidden;
  transition: border-color var(--duration-normal) var(--ease-smooth), box-shadow var(--duration-normal) var(--ease-smooth), transform var(--duration-normal) var(--ease-smooth);
}
.player-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.player-card--left {
  border-left: 3px solid var(--p-accent, var(--accent));
}
.player-card--right {
  border-left: 3px solid var(--p-accent, var(--purple-light));
}
.player-accent-stripe {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--p-accent, var(--accent));
  opacity: 0.7;
}
.compare-team-logo {
  width: 16px;
  height: 16px;
  object-fit: contain;
  vertical-align: middle;
}
.compare-team-name {
  font-weight: 600;
}
.player-sub-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--text-muted);
}
.meta-dot {
  opacity: 0.4;
}
.jersey-pill {
  font-family: var(--font-heading);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-tertiary);
  padding: 1px 4px;
}

.player-photo { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; flex-shrink: 0; border: 1px solid var(--border-light); }
.player-photo-placeholder { width: 72px; height: 72px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700; flex-shrink: 0; }
.player-photo-placeholder--p1 { background: var(--accent-lighter); color: var(--accent); }
.player-photo-placeholder--p2 { background: var(--purple-dim); color: var(--purple-light); }
.player-info h3 { margin: 0; font-size: 20px; font-weight: 800; color: var(--text-primary); font-family: var(--font-heading); }

/* 双向分流对比进度条卡片 */
.dual-bars-card {
  margin: 20px 0;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
}
.bars-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
}
.header-player-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}
.player-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot--p1 {
  background: var(--accent);
  box-shadow: 0 0 10px rgba(34, 211, 238, 0.5);
}
.dot--p2 {
  background: #A855F7;
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.5);
}
.bars-title-center {
  text-align: center;
}
.bars-title-center h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.bars-title-center p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.dual-bars-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dual-bar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(148, 163, 184, 0.03);
  transition: background 0.15s ease;
}
.dual-bar-row:hover {
  background: var(--bg-hover);
}

.bar-side {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.bar-side--left {
  justify-content: flex-end;
}
.bar-side--right {
  justify-content: flex-start;
}

.bar-val {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  min-width: 52px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.bar-side--left .bar-val {
  justify-content: flex-end;
  text-align: right;
}
.bar-side--right .bar-val {
  justify-content: flex-start;
  text-align: left;
}
.bar-val.is-winner {
  font-weight: 800;
  color: var(--text-primary);
}
.bar-side--left .bar-val.is-winner {
  color: var(--accent);
}
.bar-side--right .bar-val.is-winner {
  color: #C084FC;
}

.winner-crown {
  font-size: 10px;
  color: var(--accent);
}
.winner-crown--b {
  color: #C084FC;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: var(--bg-hover);
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  border: 1px solid var(--border-light);
}
.bar-track--left {
  display: flex;
  justify-content: flex-end;
}
.bar-track--right {
  display: flex;
  justify-content: flex-start;
}

.bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s var(--ease-out-quart, ease);
}
.bar-fill--left {
  background: rgba(34, 211, 238, 0.28);
}
.bar-fill--left.is-leading {
  background: linear-gradient(270deg, #67E8F9, #0891B2);
  box-shadow: 0 0 10px rgba(34, 211, 238, 0.45);
}
.bar-fill--right {
  background: rgba(168, 85, 247, 0.28);
}
.bar-fill--right.is-leading {
  background: linear-gradient(90deg, #C084FC, #7C3AED);
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.45);
}

.bar-label-badge {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  text-align: center;
  width: 140px;
  padding: 4px 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  letter-spacing: 0.2px;
}

.chart-card { margin: 20px 0; background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; }
.section-title { position: relative; margin: 0 0 16px; padding-left: 12px; font-size: 16px; font-weight: 700; color: var(--text-primary); font-family: var(--font-heading); }
.section-title::before { content: ''; position: absolute; left: 0; top: 50%; transform: translateY(-50%); width: 3px; height: 14px; border-radius: 2px; background: linear-gradient(180deg, var(--accent-light), var(--accent)); }
.compare-chart { height: 350px; }
.stats-card { margin-bottom: 24px; background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; }
.stat-cell { font-variant-numeric: tabular-nums; display: inline-flex; align-items: center; gap: 4px; justify-content: flex-end; }
.win-marker { font-size: 9px; transform: translateY(-1px); }
.winner-left { color: var(--accent); font-weight: 700; }
.winner-left .win-marker { color: var(--accent); }
.winner-right { color: var(--purple-light); font-weight: 700; }
.winner-right .win-marker { color: var(--purple-light); }

[data-theme="light"] .bar-side--right .bar-val.is-winner {
  color: var(--purple, #7C3AED);
}
[data-theme="light"] .winner-crown--b {
  color: var(--purple, #7C3AED);
}
[data-theme="light"] .winner-right {
  color: var(--purple, #7C3AED);
}
[data-theme="light"] .winner-right .win-marker {
  color: var(--purple, #7C3AED);
}
[data-theme="light"] .bar-fill--left.is-leading {
  background: linear-gradient(270deg, #0891B2, #06B6D4);
  box-shadow: 0 0 8px rgba(8, 145, 178, 0.35);
}
[data-theme="light"] .bar-fill--right.is-leading {
  background: linear-gradient(90deg, #8B5CF6, #7C3AED);
  box-shadow: 0 0 8px rgba(124, 58, 237, 0.35);
}
[data-theme="light"] .dual-bar-row {
  background: rgba(148, 163, 184, 0.08);
}
[data-theme="light"] .dual-bar-row:hover {
  background: rgba(148, 163, 184, 0.16);
}
.compare-scroll-hint { display: none; }
@media (max-width: 768px) {
  .page { padding: 0 16px; }
  .compare-selectors { margin-bottom: 16px; }
  .compare-selectors :deep(.el-col) { margin-bottom: 10px; }
  .vs-col { min-height: 40px; }
  .player-card { margin-bottom: 10px; padding: 14px; }
  .player-photo, .player-photo-placeholder { width: 56px; height: 56px; }
  .player-info h3 { font-size: 16px; }
  .compare-chart { height: 280px; }
  .compare-scroll-hint { display: block; margin: 0 0 8px; color: var(--text-muted); font-size: 12px; }
  .compare-empty { margin: 24px auto 40px; padding: 28px 20px; }
}

/* 空状态 */
.compare-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin: 48px auto 64px;
  max-width: 420px;
  padding: 44px 36px 40px;
  text-align: center;
  background: var(--bg-card);
  border: 1px dashed var(--border-medium);
  border-radius: var(--radius-xl);
}
.compare-empty__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 8px;
  border-radius: 50%;
  background: var(--accent-lighter);
  color: var(--accent);
}
.compare-empty strong { font-family: var(--font-heading); font-size: 16px; font-weight: 700; color: var(--text-primary); }
.compare-empty > span { font-size: 13px; line-height: 1.6; color: var(--text-muted); }
</style>
