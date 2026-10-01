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
        title="双星对决 · 球员深度对比"
        subtitle="并排透视两名现役球星的场均技术统计、效率高低与高维能力画像"
      />

      <!-- 球员选择 -->
      <el-row :gutter="20" class="compare-selectors">
        <el-col :xs="24" :sm="10">
          <el-select
            v-model="player1Id"
            filterable
            remote
            reserve-keyword
            placeholder="输入球星姓名搜索..."
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
            placeholder="输入球星姓名搜索..."
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
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12">
            <div class="player-card player-card--left">
              <img v-if="compare.player1.nbaPlayerId && !brokenPhotos.has(compare.player1.id)" :src="`https://cdn.nba.com/headshots/nba/latest/1040x760/${compare.player1.nbaPlayerId}.png`" class="player-photo" @error="onPhotoError(compare.player1.id)" />
              <div v-else class="player-photo-placeholder player-photo-placeholder--p1">{{ compare.player1.name?.charAt(0) }}</div>
              <div class="player-info">
                <h3>{{ compare.player1.name }}</h3>
                <p>{{ compare.player1.teamName }} · {{ compare.player1.position }} · #{{ compare.player1.jerseyNumber }}</p>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12">
            <div class="player-card player-card--right">
              <img v-if="compare.player2.nbaPlayerId && !brokenPhotos.has(compare.player2.id)" :src="`https://cdn.nba.com/headshots/nba/latest/1040x760/${compare.player2.nbaPlayerId}.png`" class="player-photo" @error="onPhotoError(compare.player2.id)" />
              <div v-else class="player-photo-placeholder player-photo-placeholder--p2">{{ compare.player2.name?.charAt(0) }}</div>
              <div class="player-info">
                <h3>{{ compare.player2.name }}</h3>
                <p>{{ compare.player2.teamName }} · {{ compare.player2.position }} · #{{ compare.player2.jerseyNumber }}</p>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 雷达图对比 -->
        <el-card shadow="never" class="chart-card">
          <h3 class="section-title">攻防六维战术能力模型对比 (Tactical Radar)</h3>
          <v-chart class="compare-chart" :option="radarOption" autoresize />
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
        <strong>锁定两位球星，开启巅峰对决</strong>
        <span>在上方搜索栏分别选择对决双方，即可呈现六维能力雷达与各项数据深度对比</span>
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

.player-card { display: flex; align-items: center; gap: 16px; padding: 20px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-xl); transition: border-color var(--duration-normal) var(--ease-smooth), box-shadow var(--duration-normal) var(--ease-smooth); }
.player-card:hover { box-shadow: var(--shadow-md); }
.player-card--left { border-left: 3px solid var(--accent); }
.player-card--right { border-left: 3px solid var(--purple-light); }
.player-photo { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.player-photo-placeholder { width: 72px; height: 72px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700; flex-shrink: 0; }
.player-photo-placeholder--p1 { background: var(--accent-lighter); color: var(--accent); }
.player-photo-placeholder--p2 { background: var(--purple-dim); color: var(--purple-light); }
.player-info h3 { margin: 0 0 4px; font-size: 18px; color: var(--text-primary); font-family: var(--font-heading); }
.player-info p { margin: 0; font-size: 13px; color: var(--text-muted); }

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
