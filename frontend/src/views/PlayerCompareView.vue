<template>
  <div class="page animated-bg">
    <div class="bg-particles"><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div></div>
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <!-- 返回 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="router.push('/players')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回球员列表
        </el-button>
      </div>

      <h2 class="compare-title">🔄 球员对比</h2>

      <!-- 球员选择 -->
      <el-row :gutter="20" class="compare-selectors">
        <el-col :span="10">
          <el-select
            v-model="player1Id"
            filterable
            remote
            reserve-keyword
            placeholder="输入球员姓名搜索"
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
        <el-col :span="4" class="vs-col">
          <span class="vs-badge">VS</span>
        </el-col>
        <el-col :span="10">
          <el-select
            v-model="player2Id"
            filterable
            remote
            reserve-keyword
            placeholder="输入球员姓名搜索"
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
          <el-col :span="12">
            <div class="player-card player-card--left">
              <img v-if="compare.player1.nbaPlayerId" :src="`https://cdn.nba.com/headshots/nba/latest/1040x760/${compare.player1.nbaPlayerId}.png`" class="player-photo" @error="onPhotoError" />
              <div v-else class="player-photo-placeholder">{{ compare.player1.name?.charAt(0) }}</div>
              <div class="player-info">
                <h3>{{ compare.player1.name }}</h3>
                <p>{{ compare.player1.teamName }} · {{ compare.player1.position }} · #{{ compare.player1.jerseyNumber }}</p>
              </div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="player-card player-card--right">
              <img v-if="compare.player2.nbaPlayerId" :src="`https://cdn.nba.com/headshots/nba/latest/1040x760/${compare.player2.nbaPlayerId}.png`" class="player-photo" @error="onPhotoError" />
              <div v-else class="player-photo-placeholder">{{ compare.player2.name?.charAt(0) }}</div>
              <div class="player-info">
                <h3>{{ compare.player2.name }}</h3>
                <p>{{ compare.player2.teamName }} · {{ compare.player2.position }} · #{{ compare.player2.jerseyNumber }}</p>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 雷达图对比 -->
        <el-card shadow="never" class="chart-card">
          <h3 class="section-title">📊 数据雷达图</h3>
          <v-chart class="compare-chart" :option="radarOption" autoresize />
        </el-card>

        <!-- 详细数据对比表 -->
        <el-card shadow="never" class="stats-card">
          <h3 class="section-title">📋 详细数据对比</h3>
          <el-table :data="compareRows" border stripe>
            <el-table-column label="数据项" prop="label" width="140" />
            <el-table-column label="" width="80" align="center">
              <template #default="{ row }">
                <span :class="{ 'winner-left': row.winner === 1 }">{{ formatVal(row.val1) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="对比项" prop="label" width="140" align="center">
              <template #default><span class="compare-label">VS</span></template>
            </el-table-column>
            <el-table-column label="" width="80" align="center">
              <template #default="{ row }">
                <span :class="{ 'winner-right': row.winner === 2 }">{{ formatVal(row.val2) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <el-empty v-if="!compare && !loading" description="选择两位球员开始对比" />
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

use([RadarChart, TitleComponent, TooltipComponent, RadarComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const searching1 = ref(false)
const searching2 = ref(false)
const player1Id = ref<number | null>(null)
const player2Id = ref<number | null>(null)
const compare = ref<PlayerCompare | null>(null)
const playerOptions1 = ref<Player[]>([])
const playerOptions2 = ref<Player[]>([])

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
  }
  const id2FromQuery = Number(route.query.id2) || null
  if (id2FromQuery) {
    player2Id.value = id2FromQuery
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
  const p1 = compare.value.player1
  const p2 = compare.value.player2
  const normalize = (val: number, max: number) => Math.min((val / max) * 100, 100)
  return {
    tooltip: {
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
    },
    legend: {
      data: [p1.name, p2.name],
      bottom: 0,
      textStyle: { color: '#8B949E', fontSize: 12 },
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
    },
    radar: {
      indicator: [
        { name: '得分', max: 100 },
        { name: '篮板', max: 100 },
        { name: '助攻', max: 100 },
        { name: '抢断', max: 100 },
        { name: '盖帽', max: 100 },
        { name: '命中率', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: '#8B949E', fontSize: 12 },
      splitLine: { lineStyle: { color: '#30363D' } },
      splitArea: { areaStyle: { color: ['rgba(108,92,231,0.02)', 'rgba(108,92,231,0.05)'] } },
      axisLine: { lineStyle: { color: '#30363D' } },
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
          areaStyle: { color: 'rgba(0, 210, 255, 0.2)' },
          lineStyle: { color: '#00D2FF', width: 2 },
          itemStyle: { color: '#00D2FF' },
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
          areaStyle: { color: 'rgba(108, 92, 231, 0.2)' },
          lineStyle: { color: '#6C5CE7', width: 2 },
          itemStyle: { color: '#A29BFE' },
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
    { label: '场均得分', val1: p1.pointsPerGame, val2: p2.pointsPerGame },
    { label: '场均篮板', val1: p1.reboundsPerGame, val2: p2.reboundsPerGame },
    { label: '场均助攻', val1: p1.assistsPerGame, val2: p2.assistsPerGame },
    { label: '场均抢断', val1: p1.stealsPerGame, val2: p2.stealsPerGame },
    { label: '场均盖帽', val1: p1.blocksPerGame, val2: p2.blocksPerGame },
    { label: '场均失误', val1: p1.turnoversPerGame, val2: p2.turnoversPerGame },
    { label: '投篮命中率', val1: p1.fieldGoalPct, val2: p2.fieldGoalPct, pct: true },
    { label: '三分命中率', val1: p1.threePointPct, val2: p2.threePointPct, pct: true },
    { label: '罚球命中率', val1: p1.freeThrowPct, val2: p2.freeThrowPct, pct: true },
    { label: '效率值', val1: p1.efficiency, val2: p2.efficiency },
    { label: '真实命中率', val1: p1.trueShootingPct, val2: p2.trueShootingPct, pct: true },
    { label: '使用率', val1: p1.usagePct, val2: p2.usagePct, pct: true },
    { label: '出场次数', val1: p1.gamesPlayed, val2: p2.gamesPlayed },
    { label: '场均时间', val1: p1.minutesPerGame, val2: p2.minutesPerGame },
  ]
  return stats.map(s => ({
    ...s,
    winner: (s.val1 ?? 0) > (s.val2 ?? 0) ? 1 : (s.val1 ?? 0) < (s.val2 ?? 0) ? 2 : 0,
  }))
})

function formatVal(v: number | null | undefined, pct?: boolean) {
  if (v == null) return '-'
  return pct ? `${(v * 100).toFixed(1)}%` : v.toFixed(1)
}

function onPhotoError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}
</script>

<style scoped>
.page { max-width: 1000px; min-height: calc(100vh - 108px); position: relative; border-radius: var(--radius-lg); }
.back-row { margin-bottom: 16px; }
.back-btn { color: var(--text-muted) !important; font-size: 13px !important; }
.compare-title { margin: 0 0 20px; font-size: 22px; color: var(--text-primary); font-family: var(--font-heading); }
.compare-selectors { margin-bottom: 24px; }
.vs-col { display: flex; align-items: center; justify-content: center; }
.vs-badge { font-size: 20px; font-weight: 800; color: var(--accent); font-family: var(--font-heading); }

.player-card { display: flex; align-items: center; gap: 16px; padding: 20px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-xl); }
.player-card--left { border-left: 3px solid var(--accent); }
.player-card--right { border-left: 3px solid var(--purple); }
.player-photo { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; }
.player-photo-placeholder { width: 72px; height: 72px; border-radius: 50%; background: var(--purple-dim); display: flex; align-items: center; justify-content: center; font-size: 28px; font-weight: 700; color: var(--purple); }
.player-info h3 { margin: 0 0 4px; font-size: 18px; color: var(--text-primary); }
.player-info p { margin: 0; font-size: 13px; color: var(--text-muted); }

.chart-card { margin: 20px 0; background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; }
.section-title { margin: 0 0 16px; font-size: 16px; color: var(--text-primary); }
.compare-chart { height: 350px; }
.stats-card { margin-bottom: 24px; background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; }
.compare-label { font-weight: 700; color: var(--accent); }
.winner-left { color: var(--accent); font-weight: 700; }
.winner-right { color: var(--purple); font-weight: 700; }
</style>
