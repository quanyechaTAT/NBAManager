<template>
  <div class="page animated-bg">
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <!-- 返回按钮 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="router.push('/players')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回球员数据
        </el-button>
      </div>

      <!-- 球员信息卡片 -->
      <div class="player-hero" v-loading="detailLoading">
        <div class="player-hero-inner">
          <div class="player-hero-left">
            <div class="player-avatar" @click="showHeadshotPreview = true" :class="{ 'player-avatar--clickable': player?.nbaPlayerId }">
              <img v-if="player?.nbaPlayerId" :src="headshotUrl" :alt="player?.name" class="player-headshot" @error="onHeadshotError" />
              <span v-else>{{ player?.name?.charAt(0) || '?' }}</span>
              <div v-if="player?.nbaPlayerId" class="avatar-zoom-hint">🔍</div>
            </div>
            <div>
              <h1 class="player-name">{{ player?.name }}</h1>
              <p class="player-sub">{{ player?.teamName }} · {{ player?.position }} · #{{ player?.jerseyNumber }}</p>
              <p class="player-extra">{{ player?.height }} · {{ player?.weight }}磅 · {{ player?.country }}</p>
              <el-button v-if="auth.token" :type="isFollowed ? 'success' : 'primary'" plain size="small" @click="toggleFollow" class="follow-btn">
                {{ isFollowed ? '★ 已关注' : '+ 关注' }}
              </el-button>
            </div>
          </div>
          <div class="player-hero-stats">
            <div class="hero-stat">
              <span class="hero-stat-num hero-stat-num--pts">{{ player?.pointsPerGame?.toFixed(1) }}</span>
              <span class="hero-stat-label">场均得分</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-num">{{ player?.reboundsPerGame?.toFixed(1) }}</span>
              <span class="hero-stat-label">场均篮板</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-num">{{ player?.assistsPerGame?.toFixed(1) }}</span>
              <span class="hero-stat-label">场均助攻</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 关键数据卡片 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="8" :md="4" v-for="item in keyStats" :key="item.label">
          <div class="key-stat-card">
            <span class="key-stat-label">{{ item.label }}</span>
            <strong class="key-stat-num">{{ item.value }}</strong>
          </div>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <el-row :gutter="16" style="margin-top: 20px;">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="chart-card">
            <div class="card-head">
              <div class="card-head-title">
                <span>雷达图</span>
                <small>能力六维分析</small>
              </div>
            </div>
            <div class="chart-body">
              <v-chart class="chart" :option="radarOption" autoresize v-if="detailLoaded" />
              <el-skeleton v-else :rows="6" animated />
            </div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="chart-card">
            <div class="card-head">
              <div class="card-head-title">
                <span>生涯趋势</span>
                <small>场均数据变化</small>
              </div>
            </div>
            <div class="chart-body">
              <v-chart class="chart" :option="careerOption" autoresize v-if="careerLoaded" />
              <el-skeleton v-else :rows="6" animated />
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 近期比赛日志 -->
      <div class="section-header" style="margin-top: 28px;">
        <div class="section-header-left">
          <h2>近期比赛</h2>
        </div>
      </div>
      <el-card shadow="never" class="gamelog-card">
        <el-table :data="gameLog" stripe v-loading="gameLogLoading" empty-text="暂无比赛日志" size="small">
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ formatDate(row.matchDate) }}</template>
          </el-table-column>
          <el-table-column label="主客场" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isHome ? 'success' : 'info'" size="small" effect="light">
                {{ row.isHome ? '主场' : '客场' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="对手" min-width="100" prop="opponent" />
          <el-table-column label="分钟" width="65" align="center" prop="minutes" sortable />
          <el-table-column label="得分" width="65" align="center" prop="points" sortable />
          <el-table-column label="篮板" width="65" align="center" prop="rebounds" sortable />
          <el-table-column label="助攻" width="65" align="center" prop="assists" sortable />
          <el-table-column label="抢断" width="65" align="center" prop="steals" sortable />
          <el-table-column label="盖帽" width="65" align="center" prop="blocks" sortable />
          <el-table-column label="结果" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 'W' ? 'success' : 'danger'" size="small" effect="light">
                {{ row.result === 'W' ? '胜' : '负' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="gameLogTotal"
            v-model:current-page="gameLogPage"
            :page-size="10"
            @current-change="loadGameLog"
          />
        </div>
      </el-card>
    </div>
  </div>

    <!-- 头像放大预览 - 使用 Teleport 移到 body 层级 -->
    <Teleport to="body">
      <Transition name="headshot-fade">
        <div v-if="showHeadshotPreview" class="headshot-overlay" @click.self="showHeadshotPreview = false">
          <div class="headshot-dialog">
            <div class="headshot-dialog-header">
              <span class="headshot-dialog-title">{{ player?.name }}</span>
              <button class="headshot-dialog-close" @click="showHeadshotPreview = false">✕</button>
            </div>
            <div class="headshot-dialog-body">
              <img :src="headshotUrl" :alt="player?.name" class="headshot-preview-img" @error="onHeadshotError" />
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { RadarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchPlayerDetail, fetchPlayerCareer, fetchPlayerGameLog } from '@/api/playerDetail'
import type { PlayerDetail, PlayerCareerStat, PlayerGameLogItem } from '@/api/playerDetail'
import { toggleFavorite, fetchFollowedPlayerIds } from '@/api/favorite'
import { useAuthStore } from '@/stores/auth'

use([RadarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const playerId = computed(() => Number(route.query.id) || 0)
const headshotUrl = computed(() => {
  const id = player.value?.nbaPlayerId
  if (!id) return ''
  // 使用可靠的CDN
  return `https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${id}.png`
})

const player = ref<PlayerDetail | null>(null)
const isFollowed = ref(false)
const career = ref<PlayerCareerStat[]>([])
const gameLog = ref<PlayerGameLogItem[]>([])
const gameLogTotal = ref(0)
const gameLogPage = ref(1)
const gameLogRequestVersion = ref(0) // 用于跟踪最新请求，防止并发冲突

const detailLoading = ref(false)
const showHeadshotPreview = ref(false)
const detailLoaded = ref(false)
const careerLoaded = ref(false)
const gameLogLoading = ref(false)

/* 关键数据卡片 */
const keyStats = computed(() => {
  const p = player.value
  if (!p) return []
  return [
    { label: '得分', value: p.pointsPerGame.toFixed(1) },
    { label: '篮板', value: p.reboundsPerGame.toFixed(1) },
    { label: '助攻', value: p.assistsPerGame.toFixed(1) },
    { label: '命中率', value: (p.fieldGoalPct * 100).toFixed(1) + '%' },
    { label: '三分', value: (p.threePointPct * 100).toFixed(1) + '%' },
    { label: '效率值', value: p.efficiency.toFixed(1) },
  ]
})

/* 雷达图 */
const radarOption = computed(() => {
  const p = player.value
  if (!p) return {}
  const normalize = (val: number, max: number) => Math.min((val / max) * 100, 100)
  return {
    tooltip: {
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
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
      data: [{
        value: [
          normalize(p.pointsPerGame, 35),
          normalize(p.reboundsPerGame, 15),
          normalize(p.assistsPerGame, 12),
          normalize(p.stealsPerGame, 3),
          normalize(p.blocksPerGame, 4),
          normalize(p.fieldGoalPct, 0.65),
        ],
        name: p.name,
        areaStyle: { color: 'rgba(108, 92, 231, 0.25)' },
        lineStyle: { color: '#6C5CE7', width: 2 },
        itemStyle: { color: '#A29BFE' },
      }],
    }],
  }
})

/* 生涯趋势图 */
const careerOption = computed(() => {
  const rows = career.value
  if (!rows.length) return {}
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
    },
    legend: {
      data: ['得分', '篮板', '助攻'],
      textStyle: { color: '#8B949E', fontSize: 12 },
      top: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.season),
      axisLabel: { color: '#6E7681', fontSize: 11 },
      axisLine: { lineStyle: { color: '#30363D' } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6E7681', fontSize: 11 },
      splitLine: { lineStyle: { color: '#1C2333', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        name: '得分',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.pointsPerGame),
        lineStyle: { color: '#6C5CE7', width: 2 },
        itemStyle: { color: '#6C5CE7' },
        symbol: 'circle',
        symbolSize: 6,
      },
      {
        name: '篮板',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.reboundsPerGame),
        lineStyle: { color: '#00D2FF', width: 2 },
        itemStyle: { color: '#00D2FF' },
        symbol: 'circle',
        symbolSize: 6,
      },
      {
        name: '助攻',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.assistsPerGame),
        lineStyle: { color: '#00E676', width: 2 },
        itemStyle: { color: '#00E676' },
        symbol: 'circle',
        symbolSize: 6,
      },
    ],
  }
})

function formatDate(d: string) {
  return new Date(d).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/* 加载数据 */
async function loadDetail() {
  if (!playerId.value) return
  detailLoading.value = true
  try {
    const { data } = await fetchPlayerDetail(playerId.value)
    player.value = data
  } catch {
    ElMessage.error('加载球员详情失败')
  } finally {
    detailLoading.value = false
    detailLoaded.value = true
  }
}

async function loadCareer() {
  if (!playerId.value) return
  try {
    const { data } = await fetchPlayerCareer(playerId.value)
    career.value = data
  } catch {
    ElMessage.error('加载生涯数据失败')
  } finally {
    careerLoaded.value = true
  }
}

async function loadGameLog() {
  if (!playerId.value) return

  // 递增请求版本号，用于跟踪最新请求
  const currentVersion = ++gameLogRequestVersion.value
  gameLogLoading.value = true

  try {
    const { data } = await fetchPlayerGameLog(playerId.value, { page: gameLogPage.value - 1, size: 10 })

    // 检查是否是最新的请求，如果不是则忽略响应
    if (currentVersion !== gameLogRequestVersion.value) {
      return
    }

    gameLog.value = data.content
    gameLogTotal.value = data.totalElements
  } catch {
    // 只有当请求仍然是最新请求时才显示错误
    if (currentVersion === gameLogRequestVersion.value) {
      ElMessage.error('加载比赛日志失败')
    }
  } finally {
    // 只有当请求仍然是最新请求时才更新loading状态
    if (currentVersion === gameLogRequestVersion.value) {
      gameLogLoading.value = false
    }
  }
}

function onHeadshotError(e: Event) {
  const img = e.target as HTMLImageElement
  const id = player.value?.nbaPlayerId
  if (!id) {
    // 显示占位符
    img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHZpZXdCb3g9IjAgMCA4OCA4OCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48Y2lyY2xlIGN4PSI0NCIgY3k9IjQ0IiByPSI0NCIgZmlsbD0iIzJBMkQzNSIvPjx0ZXh0IHg9IjQ0IiB5PSI1MiIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNpemU9IjI0IiBmaWxsPSIjNjY2IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj5OPC90ZXh0Pjwvc3ZnPg=='
    return
  }
  // 尝试备用CDN
  const currentSrc = img.src
  if (currentSrc.includes('cdn.nba.com')) {
    img.src = `https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${id}.png`
  } else if (currentSrc.includes('cms.nba.com')) {
    img.src = `https://www.nba.com/.element/img/2.0/sect/statscube/players/large/${id}.png`
  } else {
    // 显示占位符
    img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHZpZXdCb3g9IjAgMCA4OCA4OCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48Y2lyY2xlIGN4PSI0NCIgY3k9IjQ0IiByPSI0NCIgZmlsbD0iIzJBMkQzNSIvPjx0ZXh0IHg9IjQ0IiB5PSI1MiIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNpemU9IjI0IiBmaWxsPSIjNjY2IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj5OPC90ZXh0Pjwvc3ZnPg=='
  }
}

async function toggleFollow() {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    const { data } = await toggleFavorite('PLAYER', playerId.value)
    isFollowed.value = data.favorited
  } catch { /* ignore */ }
}

async function checkFollowStatus() {
  if (!auth.token) return
  try {
    const { data } = await fetchFollowedPlayerIds()
    isFollowed.value = data.includes(playerId.value)
  } catch { /* ignore */ }
}

watch(playerId, () => {
  if (playerId.value) {
    loadDetail()
    loadCareer()
    loadGameLog()
    checkFollowStatus()
  }
}, { immediate: false })

onMounted(() => {
  loadDetail()
  loadCareer()
  loadGameLog()
  checkFollowStatus()
})
</script>

<style scoped>
.page {
  max-width: 1200px;
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

/* 返回按钮 */
.back-row {
  margin-bottom: 16px;
}
.back-btn {
  color: var(--text-muted) !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 6px 12px !important;
  border-radius: var(--radius-md) !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}
.back-btn:hover {
  color: var(--accent) !important;
  background: var(--accent-lighter) !important;
}
.back-btn svg {
  margin-right: 4px;
}

/* 球员信息卡片 */
.player-hero {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 28px 32px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  animation: heroSlideIn 0.5s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(16px);
}
@keyframes heroSlideIn { to { opacity: 1; transform: translateY(0); } }
.player-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light), var(--purple));
}
.player-hero::after {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--purple-glow) 0%, transparent 70%);
  opacity: 0.3;
  pointer-events: none;
}
.player-hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  flex-wrap: wrap;
  gap: 20px;
}
.player-hero-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.player-avatar {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--purple) 0%, #5A4BD1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
  border: 3px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-smooth);
}
.player-hero:hover .player-avatar {
  border-color: var(--purple);
  box-shadow: 0 0 20px var(--purple-glow);
  transform: scale(1.05);
}
.player-headshot {
  width: 100%;
  height: 100%;
  object-fit: cover;
  color: #fff;
  flex-shrink: 0;
  font-family: var(--font-heading);
}
.player-avatar--clickable {
  cursor: pointer;
  position: relative;
}
.avatar-zoom-hint {
  position: absolute;
  bottom: 4px;
  right: 4px;
  font-size: 14px;
  background: rgba(0,0,0,0.5);
  border-radius: 50%;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-smooth);
}
.player-avatar--clickable:hover .avatar-zoom-hint {
  opacity: 1;
}
.headshot-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px;
}
.headshot-preview-img {
  max-width: 500px;
  max-height: 600px;
  object-fit: contain;
  border-radius: var(--radius-lg);
}

/* 头像预览弹窗 - 全屏遮罩 */
.headshot-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.headshot-dialog {
  background: var(--bg-card);
  border: 1px solid var(--border-medium);
  border-radius: var(--radius-xl);
  box-shadow: 0 32px 100px rgba(0, 0, 0, 0.8), 0 0 2px rgba(0, 255, 136, 0.2);
  overflow: hidden;
  transform: scale(0.95);
  animation: headshotZoomIn 0.3s var(--ease-spring) forwards;
}

@keyframes headshotZoomIn {
  to {
    transform: scale(1);
  }
}

.headshot-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  background: linear-gradient(135deg, var(--accent-lighter), transparent);
}

.headshot-dialog-title {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.5px;
}

.headshot-dialog-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.headshot-dialog-close:hover {
  background: var(--danger);
  border-color: var(--danger);
  color: #fff;
  transform: rotate(90deg);
}

.headshot-dialog-body {
  padding: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 弹窗过渡动画 */
.headshot-fade-enter-active {
  transition: opacity 0.25s var(--ease-smooth);
}
.headshot-fade-leave-active {
  transition: opacity 0.2s var(--ease-smooth);
}
.headshot-fade-enter-from,
.headshot-fade-leave-to {
  opacity: 0;
}
.player-name {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.player-sub {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--text-muted);
}
.player-extra {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--text-dim);
}
.follow-btn { margin-top: 8px !important; }
.player-hero-stats {
  display: flex;
  align-items: center;
  gap: 24px;
}
.hero-stat {
  text-align: center;
}
.hero-stat-num {
  display: block;
  font-size: 34px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  line-height: 1.1;
  letter-spacing: -0.5px;
}
.hero-stat-num--pts {
  color: var(--accent);
  text-shadow: 0 0 20px var(--accent-glow);
}
.hero-stat-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-top: 6px;
  display: block;
  font-weight: 600;
}
.hero-stat-divider {
  width: 1px;
  height: 36px;
  background: var(--border-light);
}

/* 关键数据卡片 */
.stat-row {
  margin-bottom: 0;
}
.key-stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
  text-align: center;
  transition: all var(--duration-normal) var(--ease-smooth);
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}
.key-stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}
.key-stat-card:hover::before {
  opacity: 1;
}
.key-stat-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.key-stat-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
  font-weight: 600;
}
.key-stat-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.5px;
}

/* section header */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.section-header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.section-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
}
.section-sub {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.2px;
}

/* 图表卡片 */
.chart-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  overflow: hidden;
  margin-bottom: 16px;
  position: relative;
}
.chart-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--purple), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}
.chart-card:hover::before {
  opacity: 1;
}
.chart-card:hover {
  border-color: var(--border-medium) !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3) !important;
}
.chart-body {
  padding: 12px 16px 16px;
}
.chart {
  height: 340px;
}

/* 比赛日志卡片 */
.gamelog-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  position: relative;
  overflow: hidden;
}
.gamelog-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--cyan));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
