<template>
  <div class="news-page">
    <section class="news-hero">
      <div>
        <span class="news-kicker">NBA GAME CENTER</span>
        <h1>赛事与战报中心</h1>
        <p>{{ todayStr }} · 今日焦点赛况、赛程日程与深度战报复盘</p>
      </div>
      <div class="news-hero-actions">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input v-model="q" class="news-search" type="text" placeholder="搜索比赛焦点、球队、战报..." @keyup.enter="() => load()" />
          <button v-if="q" @click="q = ''; load()" class="search-clear" aria-label="清除搜索">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
        </div>
        <el-button type="primary" plain size="small" @click="refreshAll" :loading="refreshing">刷新</el-button>
        <el-button v-if="auth.isAdmin" type="primary" size="small" @click="openCreate">新增资讯</el-button>
      </div>
    </section>

    <section
      class="score-strip"
      :class="{ 'score-strip--empty': !todayLoading && visibleTodayGames.length === 0 }"
      v-loading="todayLoading"
    >
      <template v-if="todayLoading || visibleTodayGames.length > 0">
      <div class="score-strip-header">
        <span>今日赛事</span>
        <small>横向滑动查看更多</small>
      </div>
      <div class="today-scroll">
        <article
          v-for="item in visibleTodayGames"
          :key="item.id"
          class="today-game-card"
          :class="{ 'is-history': isHistoricalGame(item), 'is-summer': isSummerGame(item), 'is-disabled': !hasMatchDetail(item) }"
          @click="goToMatchDetail(item)"
        >
          <div class="game-card-top">
            <span class="league-pill" :class="{ summer: isSummerGame(item) }">{{ gameLeagueLabel(item) }}</span>
            <span class="game-type">{{ gameTypeLabel(item) }}</span>
            <span class="game-time">{{ formatGameTime(item.gameStartTime) }}</span>
          </div>
          <div class="game-title">{{ item.title }}</div>
          <div class="game-matchup">
            <div class="game-team">
              <img v-if="teamLogo(item.awayTeam)" :src="teamLogo(item.awayTeam)" class="team-logo" />
              <span class="team-fallback" v-else>{{ fallbackTeamInitial(item.awayTeam) }}</span>
              <strong>{{ item.awayTeam }}</strong>
            </div>
            <div class="game-score">
              <span :class="{ winner: isWinner(item, 'away') }">{{ displayScore(item.awayScore) }}</span>
              <em>-</em>
              <span :class="{ winner: isWinner(item, 'home') }">{{ displayScore(item.homeScore) }}</span>
            </div>
            <div class="game-team">
              <img v-if="teamLogo(item.homeTeam)" :src="teamLogo(item.homeTeam)" class="team-logo" />
              <span class="team-fallback" v-else>{{ fallbackTeamInitial(item.homeTeam) }}</span>
              <strong>{{ item.homeTeam }}</strong>
            </div>
          </div>
        </article>
      </div>
      </template>
      <p v-else class="today-inline">今日暂无赛事，赛程同步后在此展示今日比赛</p>
    </section>

    <el-tabs v-model="discoveryTab" aria-label="资讯浏览方式">
      <el-tab-pane label="最新资讯" name="latest" />
      <el-tab-pane label="为你推荐" name="recommended" />
    </el-tabs>
    <DiscoveryFeed v-if="discoveryTab === 'recommended'" scene="NEWS_FEED" />
    <section v-show="discoveryTab === 'latest'" class="news-layout">
      <main class="feed-panel">
        <div class="feed-header">
          <div>
            <h2>最新资讯</h2>
            <p>按时间汇总赛事、交易、伤病与选秀动态</p>
          </div>
          <span class="feed-count">{{ total }} 条</span>
        </div>

        <div class="news-list" v-loading="newsLoading">
          <article v-for="row in list" :key="row.id" class="news-item" @click="showDetail(row)">
            <div class="news-item-main">
              <div class="news-title-row">
                <span v-if="row.category && row.category !== 'general'" class="category-chip">{{ categoryLabel(row.category) }}</span>
                <h3>{{ row.title }}</h3>
              </div>
              <p v-if="row.summary" class="news-summary">{{ row.summary }}</p>
              <div class="news-meta">
                <span>{{ sourceName(row) }}</span>
                <span>{{ row.viewCount ?? 0 }} 浏览</span>
                <span>{{ row.favoriteCount ?? 0 }} 收藏</span>
                <span>{{ relativeTime(row.createTime || row.gameStartTime) }}</span>
                <button v-if="row.nbaGameId" class="inline-link" @click.stop="goToMatchDetail(row)">比赛数据</button>
              </div>
            </div>
            <div class="news-thumb" :class="{ 'news-thumb--placeholder': !thumbnailUrl(row) }">
              <img v-if="thumbnailUrl(row)" :src="thumbnailUrl(row)" :alt="row.title" loading="lazy" @error="onThumbError(row)" />
              <div v-else class="thumb-placeholder">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
              </div>
            </div>
          </article>
          <div v-if="!newsLoading && list.length === 0" class="news-empty">暂无资讯</div>
        </div>
        <div class="pager" v-if="total > 0">
          <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </main>

      <aside class="news-sidebar">
        <section class="sidebar-block">
          <h3>热门阅读</h3>
          <button v-for="(item, index) in hotNews" :key="item.id" class="hot-item" @click="showDetail(item)">
            <span>{{ index + 1 }}</span>
            <strong>{{ item.title }}</strong>
          </button>
        </section>
        <section class="sidebar-block" v-if="playableTodayGames.length">
          <h3>赛事入口</h3>
          <button v-for="item in playableTodayGames.slice(0, 5)" :key="`side-${item.id}`" class="side-game" @click="goToMatchDetail(item)">
            <span>{{ item.homeTeam }}</span>
            <em>{{ displayScore(item.homeScore) }} : {{ displayScore(item.awayScore) }}</em>
            <span>{{ item.awayTeam }}</span>
          </button>
        </section>
      </aside>
    </section>

    <!-- 详情对话框 -->
    <el-dialog v-model="dialogVisible" :title="current?.title" width="680px" class="dialog-light" destroy-on-close :append-to-body="true" :center="true">
      <template v-if="current">
        <div class="detail-meta">
          <el-tag :type="statusType(current.status)">{{ statusLabel(current.status) }}</el-tag>
          <el-tag v-if="current.category && current.category !== 'general'" :type="categoryType(current.category)">{{ categoryLabel(current.category) }}</el-tag>
          <span class="detail-time">🕐 {{ formatTimeRange(current.gameStartTime, current.gameEndTime) }}</span>
        </div>
        <div v-if="current.imageUrl" class="detail-image">
          <img :src="current.imageUrl" :alt="current.title" />
        </div>
        <div class="detail-score" v-if="current.status === 'FINISHED' && current.homeScore != null">
          <div class="score-box"><strong>{{ current.homeTeam }}</strong><span class="big-score">{{ current.homeScore }}</span></div>
          <span class="score-divider">:</span>
          <div class="score-box"><strong>{{ current.awayTeam }}</strong><span class="big-score">{{ current.awayScore }}</span></div>
        </div>
        <div class="detail-vs" v-else>
          <span class="detail-team">{{ current.homeTeam }}</span><span class="detail-vs-text">VS</span><span class="detail-team">{{ current.awayTeam }}</span>
        </div>
        <el-divider />
        <div class="detail-content news-content" v-html="sanitizeHtml(current.content)"></div>
        <div class="detail-stats">
          <span>👁 浏览量：{{ current.viewCount || 0 }}</span>
          <span>❤️ 收藏量：{{ current.favoriteCount || 0 }}</span>
        </div>
        <div class="detail-actions">
          <el-button v-if="auth.token" :type="current.favoritedByMe ? 'danger' : ''" plain size="small" @click="toggleFav(current)">
            {{ current.favoritedByMe ? '★ 已收藏' : '☆ 收藏' }}
          </el-button>
          <ShareButton :url="newsUrl" :title="current.title" :description="current.summary" />
          <el-button v-if="current.sourceUrl" type="success" plain size="small" @click="openSource(current.sourceUrl)">🔗 查看原文</el-button>
          <el-button v-if="current.nbaGameId" type="primary" size="small" @click="goToMatchDetail(current)">查看比赛详细数据</el-button>
          <el-tooltip v-else content="该新闻未关联NBA比赛ID，无法查看比赛详情" placement="top">
            <el-button type="info" plain size="small" disabled>比赛数据（暂无）</el-button>
          </el-tooltip>
        </div>
        <DiscoveryFeed v-if="dialogVisible" :key="current.id" scene="NEWS_DETAIL" :item-key="`NEWS:${current.id}`" title="接着读" compact />
      </template>
    </el-dialog>

    <!-- 新增/编辑对话框（管理员） -->
    <el-dialog v-model="formVisible" :title="formTitle" width="680px" destroy-on-close @closed="resetForm" class="dialog-light" :append-to-body="true" :center="true">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" maxlength="300" show-word-limit type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="详细内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主队" prop="homeTeam">
              <el-input v-model="form.homeTeam" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客队" prop="awayTeam">
              <el-input v-model="form.awayTeam" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主队得分">
              <el-input-number v-model="form.homeScore" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客队得分">
              <el-input-number v-model="form.awayScore" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="NBA比赛ID">
          <el-input v-model="form.nbaGameId" placeholder="10位NBA官方比赛ID，如 0022400001" maxlength="16" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.category" placeholder="选择分类" style="width:100%">
                <el-option label="综合" value="general" />
                <el-option label="赛事" value="game" />
                <el-option label="交易" value="trade" />
                <el-option label="伤病" value="injury" />
                <el-option label="选秀" value="draft" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始时间" prop="gameStartTime">
              <el-date-picker v-model="form.gameStartTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="结束时间" prop="gameEndTime">
              <el-date-picker v-model="form.gameEndTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图片链接">
              <el-input v-model="form.imageUrl" placeholder="新闻图片URL" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源链接">
          <el-input v-model="form.sourceUrl" placeholder="新闻原文链接" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import DiscoveryFeed from '@/components/discovery/DiscoveryFeed.vue'
import { useDiscoveryReading } from '@/composables/useDiscoveryReading'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchNews, fetchNewsDetail, fetchTodayNews, createNews, updateNews, toggleNewsFavorite, type GameNewsPayload } from '@/api/news'
import { recordBrowse } from '@/api/browseHistory'
import { useAuthStore } from '@/stores/auth'
import { sanitizeHtml } from '@/utils/sanitize'
import ShareButton from '@/components/ShareButton.vue'
import type { GameNews } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import { getJob, syncModule, type SyncJobResponse } from '@/api/dataManagement'
import { subscribeDataRefresh, type DataRefreshSignal } from '@/utils/dataRefresh'

const auth = useAuthStore()
const route = useRoute()

const q = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<GameNews[]>([])
const newsLoading = ref(false)
const refreshing = ref(false)
const todayNews = ref<GameNews[]>([])
const todayLoading = ref(false)
const dialogVisible = ref(false)
const current = ref<GameNews | null>(null)
const discoveryTab = ref('latest')
useDiscoveryReading(() => dialogVisible.value && current.value ? `NEWS:${current.value.id}` : null)
const failedThumbs = ref<Record<string, boolean>>({})
const newsUrl = computed(() => `${window.location.origin}/news?newsId=${current.value?.id || ''}`)

// 表单相关
const formVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<GameNewsPayload>({
  title: '', summary: '', content: '', homeTeam: '', awayTeam: '',
  homeScore: null, awayScore: null, gameStartTime: '', gameEndTime: '', nbaGameId: '',
  category: 'general', sourceUrl: '', imageUrl: '',
})

const todayStr = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
const hotNews = computed(() => [...list.value].sort((a, b) => (b.viewCount ?? 0) - (a.viewCount ?? 0)).slice(0, 6))

const formTitle = ref('新增赛事资讯')

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入摘要', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  homeTeam: [{ required: true, message: '请输入主队', trigger: 'blur' }],
  awayTeam: [{ required: true, message: '请输入客队', trigger: 'blur' }],
  gameStartTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  gameEndTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_rule: any, value: string, callback: (err?: Error) => void) => {
        if (value && form.gameStartTime && value <= form.gameStartTime) {
          callback(new Error('结束时间必须晚于开始时间'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

function statusType(s: string) {
  const map: Record<string, string> = { SCHEDULED: 'warning', LIVE: 'danger', FINISHED: 'info' }
  return map[s] || 'info'
}
function statusLabel(s: string) {
  const map: Record<string, string> = { SCHEDULED: '未开始', LIVE: '进行中', FINISHED: '已结束' }
  return map[s] || s
}
function categoryType(c: string) {
  const map: Record<string, string> = { game: 'primary', summer_game: 'primary', trade: 'success', injury: 'danger', draft: 'warning' }
  return map[c] || 'info'
}
function categoryLabel(c: string) {
  const map: Record<string, string> = { game: '赛事', summer_game: '夏季联赛', trade: '交易', injury: '伤病', draft: '选秀', general: '综合' }
  return map[c] || c
}
function openSource(url: string) {
  window.open(url, '_blank')
}

// ── 今日赛事过滤与辅助函数 ──
const INVALID_TEAMS = new Set(['待定', 'TBD', 'tbd', ''])

function isValidTeamName(name: string | null | undefined): boolean {
  return !!name && !INVALID_TEAMS.has(name.trim())
}

function hasMatchDetail(item: GameNews): boolean {
  return !!item.nbaGameId && isValidTeamName(item.homeTeam) && isValidTeamName(item.awayTeam)
}

const playableTodayGames = computed(() => {
  return todayNews.value.filter(item => hasMatchDetail(item))
})

const visibleTodayGames = computed(() => {
  return todayNews.value.filter(item => isValidTeamName(item.homeTeam) && isValidTeamName(item.awayTeam))
})

function isHistoricalGame(item: GameNews): boolean {
  return item.category === 'historical_game' || item.sourceUrl === 'match_records'
}

function isSummerGame(item: GameNews): boolean {
  return item.category === 'summer_game' || item.sourceUrl === 'ESPN_NBA_SUMMER'
}

function gameLeagueLabel(item: GameNews): string {
  if (isSummerGame(item)) return '夏联'
  if (isHistoricalGame(item)) return '历史'
  return 'NBA'
}

function gameTypeLabel(item: GameNews): string {
  if (isSummerGame(item)) return 'NBA夏季联赛'
  if (isHistoricalGame(item)) return '真实比赛补位'
  return '今日赛事'
}

function displayScore(score: number | null | undefined): string {
  return (score != null && typeof score === 'number') ? String(score) : '-'
}

function isWinner(item: GameNews, side: 'home' | 'away'): boolean {
  if (item.status !== 'FINISHED') return false
  const s = side === 'home' ? item.homeScore : item.awayScore
  const o = side === 'home' ? item.awayScore : item.homeScore
  return s != null && o != null && s > o
}

function formatGameTime(t: string | null | undefined): string {
  if (!t) return ''
  const d = new Date(t)
  const month = d.getMonth() + 1
  const day = d.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const today = new Date()
  if (d.toDateString() === today.toDateString()) return `${hh}:${mm}`
  return `${month}月${day}日 ${hh}:${mm}`
}

function teamLogo(name: string | null | undefined): string | undefined {
  if (!name) return undefined
  return getTeamLogo(name) || undefined
}

function fallbackTeamInitial(name: string | null | undefined): string {
  return name ? name.charAt(0) : '?'
}
function sourceName(item: GameNews): string {
  if (item.sourceUrl) {
    try {
      const host = new URL(item.sourceUrl).hostname.replace(/^www\./, '')
      if (host.includes('espn')) return 'ESPN'
      if (host.includes('nba')) return 'NBA'
      return host
    } catch { /* ignore invalid source url */ }
  }
  return categoryLabel(item.category || 'general')
}
function relativeTime(t: string) {
  if (!t) return ''
  const diff = Date.now() - new Date(t).getTime()
  const minutes = Math.max(0, Math.floor(diff / 60000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return formatDate(t)
}
function rawThumbnailUrl(item: GameNews) {
  return item.imageUrl || getTeamLogo(item.homeTeam) || getTeamLogo(item.awayTeam) || ''
}
function thumbnailKey(item: GameNews, url = rawThumbnailUrl(item)) {
  return `${item.id}:${url}`
}
function thumbnailUrl(item: GameNews) {
  const url = rawThumbnailUrl(item)
  if (!url || failedThumbs.value[thumbnailKey(item, url)]) return ''
  return url
}
function onThumbError(item: GameNews) {
  const url = rawThumbnailUrl(item)
  if (!url) return
  failedThumbs.value = { ...failedThumbs.value, [thumbnailKey(item, url)]: true }
}
function formatTimeSingle(t: string) {
  return new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
function formatDate(t: string) {
  return new Date(t).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
function formatHM(t: string) {
  return new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
function formatTimeRange(start: string, end: string) {
  const sDate = formatDate(start)
  const eDate = formatDate(end)
  if (sDate === eDate) {
    return `${sDate} ${formatHM(start)} - ${formatHM(end)}`
  }
  return `${formatTimeSingle(start)} - ${formatTimeSingle(end)}`
}

async function load(forceFresh = false) {
  newsLoading.value = true
  try {
    const res = await fetchNews({
      q: q.value || '',
      page: page.value - 1,
      size: size.value,
      cacheNonce: forceFresh ? Date.now() : undefined,
    })
    list.value = res.data.content
    total.value = res.data.totalElements
  } catch {
    ElMessage.error('加载赛事资讯失败')
  } finally {
    newsLoading.value = false
  }
}

async function toggleFav(item: GameNews) {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    const { data } = await toggleNewsFavorite(item.id)
    item.favoritedByMe = data.favorited
    item.favoriteCount += data.favorited ? 1 : -1
  } catch { /* ignore */ }
}

async function loadToday() {
  todayLoading.value = true
  try {
    const res = await fetchTodayNews()
    todayNews.value = res.data
  } catch {
    // ignore
  } finally {
    todayLoading.value = false
  }
}

const REFRESH_TERMINAL_STATUSES = new Set([
  'SUCCESS', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED', 'INTERRUPTED'
])
let refreshDisposed = false

function wait(ms: number): Promise<void> {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

async function waitForSyncJob(jobId: number): Promise<SyncJobResponse> {
  const deadline = Date.now() + 15 * 60 * 1000
  while (!refreshDisposed && Date.now() < deadline) {
    const { data: job } = await getJob(jobId)
    if (REFRESH_TERMINAL_STATUSES.has(job.status)) return job
    await wait(2000)
  }
  if (refreshDisposed) throw new Error('页面已关闭')
  throw new Error('同步任务等待超时，请到数据管理中心查看任务状态')
}

// 新闻和今日赛事均通过统一 Coordinator 提交，并等待真实任务终态。
async function refreshAll() {
  refreshing.value = true
  try {
    const modules = ['NEWS', 'TODAY_GAMES'] as const
    const submissionResults = await Promise.allSettled([
      syncModule('NEWS'),
      syncModule('TODAY_GAMES')
    ])
    const submissions = submissionResults.flatMap((result, index) =>
      result.status === 'fulfilled'
        ? [{ module: modules[index], response: result.value }]
        : []
    )
    const submissionFailures = submissionResults.flatMap((result, index) => {
      if (result.status === 'fulfilled') return []
      const reason: any = result.reason
      const message = reason?.response?.data?.message || reason?.message || '提交失败'
      return [`${modules[index]}: ${message}`]
    })
    if (submissions.length === 0) {
      const firstFailure = submissionResults.find(result => result.status === 'rejected')
      if (firstFailure?.status === 'rejected') {
        throw firstFailure.reason
      }
      throw new Error('同步任务提交失败')
    }
    ElMessage.info('数据同步已启动，正在等待任务完成')

    const jobs = await Promise.all(
      submissions.map(({ response }) => waitForSyncJob(response.data.jobId))
    )
    page.value = 1
    await Promise.all([loadToday(), load(true)])

    const failed = jobs.filter(job => job.status !== 'SUCCESS')
    if (submissionFailures.length > 0 || failed.length > 0) {
      const details = submissionFailures.concat(failed
        .map(job => `${job.module}: ${job.errorMessage || job.status}`)
      ).join('；')
      ElMessage.error(`部分同步未完成：${details}`)
    } else {
      const inserted = jobs.reduce((sum, job) => sum + (job.insertedCount || 0), 0)
      const updated = jobs.reduce((sum, job) => sum + (job.updatedCount || 0), 0)
      if (inserted === 0) {
        ElMessage.info(`同步完成：上游暂无新资讯，更新 ${updated} 条比赛数据`)
      } else {
        ElMessage.success(`数据同步完成：新增 ${inserted} 条，更新 ${updated} 条`)
      }
    }
  } catch (error: any) {
    if (refreshDisposed) return
    console.error('同步失败:', error)
    const msg = error.response?.data?.message || error.message || '同步失败'
    ElMessage.error(`同步失败: ${msg}`)
    await Promise.all([loadToday(), load(true)])
  } finally {
    refreshing.value = false
  }
}

async function showDetail(item: GameNews) {
  dialogVisible.value = true
  if (auth.token) {
    recordBrowse('NEWS', item.id).catch(() => {})
  }
  // 调用详情API增加浏览量
  try {
    const { data } = await fetchNewsDetail(item.id)
    current.value = data
    // 同步更新列表中的浏览量
    const idx = list.value.findIndex(n => n.id === item.id)
    if (idx >= 0) { list.value[idx].viewCount = data.viewCount; list.value[idx].favoriteCount = data.favoriteCount }
    const tidx = todayNews.value.findIndex(n => n.id === item.id)
    if (tidx >= 0) { todayNews.value[tidx].viewCount = data.viewCount; todayNews.value[tidx].favoriteCount = data.favoriteCount }
  } catch {
    current.value = item
  }
}

const router = useRouter()
function goToMatchDetail(item: GameNews) {
  if (!item.nbaGameId) {
    ElMessage.warning('该比赛暂无详细数据（缺少NBA比赛ID）')
    return
  }
  router.push({
    path: '/match-detail',
    query: {
      gameId: item.nbaGameId,
      status: item.status,
      homeTeam: item.homeTeam,
      awayTeam: item.awayTeam,
      homeScore: item.homeScore != null ? String(item.homeScore) : undefined,
      awayScore: item.awayScore != null ? String(item.awayScore) : undefined,
      returnTo: '/news'
    }
  })
}

// --- 管理员操作 ---
function openCreate() {
  editingId.value = null
  formTitle.value = '新增赛事资讯'
  resetForm()
  formVisible.value = true
}

function resetForm() {
  form.title = ''
  form.summary = ''
  form.content = ''
  form.homeTeam = ''
  form.awayTeam = ''
  form.homeScore = null
  form.awayScore = null
  form.gameStartTime = ''
  form.gameEndTime = ''
  form.nbaGameId = ''
  form.category = 'general'
  form.sourceUrl = ''
  form.imageUrl = ''
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateNews(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createNews({ ...form })
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    loadToday()
    load()
  } catch {
    ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
  }
}

let unsubscribeDataRefresh = () => {}

async function handleDataRefresh(signal: DataRefreshSignal) {
  if (signal.module === 'NEWS') {
    page.value = 1
    await load(true)
  } else if (signal.module === 'TODAY_GAMES') {
    await loadToday()
  }
}

let detailRequest = 0
async function openNewsFromRoute() {
  const id = Number(route.query.newsId)
  const sequence = ++detailRequest
  if (!Number.isSafeInteger(id) || id <= 0) return
  try {
    const { data } = await fetchNewsDetail(id)
    if (sequence !== detailRequest || refreshDisposed) return
    current.value = data
    dialogVisible.value = true
    if (auth.token) void recordBrowse('NEWS', id).catch(() => {})
  } catch { if (sequence === detailRequest) ElMessage.info('该资讯不存在或已被删除') }
}
watch(() => route.query.newsId, () => { void openNewsFromRoute() })

onMounted(async () => {
  unsubscribeDataRefresh = subscribeDataRefresh(handleDataRefresh)
  await Promise.all([loadToday(), load()])
  await openNewsFromRoute()
})

onUnmounted(() => {
  refreshDisposed = true
  unsubscribeDataRefresh()
})
</script>

<style scoped>
/* 防止 stagger-in 动画与 el-dialog 关闭动画冲突导致抖动 */
:deep(.el-overlay) { animation: none !important; opacity: 1 !important; transform: none !important; }

.news-kicker {
  display: block;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 1.2px;
  margin-bottom: 2px;
}

.page {
  padding: 4px 0;
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
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
.section-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
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
.date {
  color: var(--text-muted);
  font-size: 13px;
  font-family: var(--font-heading);
  letter-spacing: 0.2px;
}
/* ===== 今日赛事网格 ===== */
.score-strip--empty {
  padding: 10px 16px;
}
.today-inline {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}
.today-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 1;
  min-height: 96px;
  padding: 20px;
  text-align: center;
  white-space: normal;
}
.today-empty svg {
  margin-bottom: 6px;
  color: var(--text-dim);
}
.today-empty strong {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--text-secondary);
}
.today-empty span {
  font-size: 12px;
  color: var(--text-muted);
}

/* ===== 资讯卡片 ===== */
.news-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.news-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all .15s;
}
.news-card:hover { border-color: var(--accent); transform: translateY(-2px); box-shadow: var(--shadow-md); }
.news-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.news-status { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: 600; }
.news-status.live { background: rgba(239,68,68,0.1); color: var(--danger); }
.news-status.finished { background: rgba(34,197,94,0.1); color: var(--success); }
.news-status.scheduled { background: var(--bg-hover); color: var(--text-muted); }
.news-category { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: 600; background: var(--accent-lighter); color: var(--accent); }
.news-time { font-size: 11px; color: var(--text-dim); margin-left: auto; }
.news-card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.news-card-meta { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: var(--text-muted); margin-bottom: 8px; }
.news-score { font-weight: 700; color: var(--accent); }
.news-card-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 8px; border-top: 1px solid var(--border-light); }
.news-views { font-size: 11px; color: var(--text-dim); }
.news-detail-link { font-size: 12px; color: var(--accent); font-weight: 500; }
.news-detail-link:hover { text-decoration: underline; }
.news-empty { grid-column: 1 / -1; text-align: center; padding: 32px; color: var(--text-muted); font-size: 13px; }

/* ===== Sports portal news feed ===== */
.news-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 8px 20px 32px;
  color: var(--text-primary);
}
.news-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 12px 0 20px;
  border-bottom: 1px solid var(--border-light);
  position: relative;
}
.news-hero::after {
  content: none;
}
.news-hero h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: -0.4px;
  color: var(--text-primary);
}
.news-hero p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-muted);
}
.news-hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.news-hero-actions :deep(.el-button--primary) {
  background: var(--accent) !important;
  border-color: var(--accent) !important;
  color: var(--accent-contrast, #04202A) !important;
  box-shadow: 0 8px 18px rgba(34, 211, 238, 0.18);
}
.news-hero-actions :deep(.el-button--primary.is-plain) {
  background: rgba(34, 211, 238, .08) !important;
  border-color: rgba(34, 211, 238, .24) !important;
  color: var(--accent) !important;
  box-shadow: none;
}
.news-hero-actions :deep(.el-button--primary:hover),
.news-hero-actions :deep(.el-button--primary:focus) {
  background: var(--accent-hover) !important;
  border-color: var(--accent-hover) !important;
  color: var(--accent-contrast, #04202A) !important;
}
.search-wrap {
  width: 280px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-muted);
}
.search-wrap:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.10);
}
.news-search {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
}
.score-strip {
  margin: 16px 0 20px;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-card);
}
.score-strip-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}
.score-strip-header span {
  font-size: 15px;
  font-weight: 800;
}
.score-strip-header small {
  color: var(--text-muted);
  font-size: 12px;
}
.today-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: thin;
}
/* ── 赛事比分卡 ── */
.today-game-card {
  flex: 0 0 220px;
  min-height: 108px;
  padding: 10px 14px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-card);
  cursor: pointer;
  transition: border-color .12s, background .12s;
}
.today-game-card:hover {
  border-color: var(--accent);
  background: var(--bg-hover);
}
.game-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.status-pill {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-hover);
}
.status-pill.live { color: #dc2626; background: rgba(220,38,38,.08); }
.status-pill.finished { color: var(--text-dim); background: var(--bg-hover); }
.status-pill.scheduled { color: var(--accent); background: rgba(34, 211, 238,.08); }
.game-time { font-size: 12px; color: var(--text-muted); }
.game-matchup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.game-team {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.game-team strong {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.team-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}
.team-fallback {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-hover);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}
.game-score {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.game-score em {
  font-style: normal;
  color: var(--text-dim);
  font-size: 14px;
}
.game-score .winner {
  color: var(--accent);
}
.today-game-card {
  flex-basis: 216px;
  min-height: 136px;
  padding: 12px 10px 10px;
  transition: border-color .12s, background .12s, box-shadow .12s, transform .12s;
}
.today-game-card:hover {
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}
.today-game-card.is-history {
  border-style: dashed;
}
.today-game-card.is-disabled {
  cursor: not-allowed;
  opacity: .72;
}
.today-game-card .game-card-top {
  justify-content: flex-start;
  gap: 6px;
  margin-bottom: 8px;
}
.league-pill {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 4px;
  background: #f8d99b;
  color: #7c4a03;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.2;
}
.league-pill.summer {
  background: rgba(14, 165, 233, 0.14);
  color: #0369a1;
}
.game-type {
  min-width: 0;
  flex: 1;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.today-game-card .game-time {
  flex: 0 0 auto;
  font-size: 11px;
}
.game-title {
  height: 34px;
  margin-bottom: 8px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.today-game-card .game-matchup {
  gap: 8px;
}
.today-game-card .team-logo,
.today-game-card .team-fallback {
  width: 34px;
  height: 34px;
}
.today-game-card .game-score {
  gap: 5px;
}
.today-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  white-space: normal;
}
.news-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 24px;
  align-items: start;
}
.feed-panel {
  min-width: 0;
  border: 1px solid var(--border-light);
  border-radius: 14px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  position: relative;
}
.feed-panel::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.55), transparent);
  opacity: 0.55;
  pointer-events: none;
}
.feed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-light);
}
.feed-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}
.feed-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}
.feed-count {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 12px;
}
.news-list {
  min-height: 240px;
}
.news-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 156px;
  gap: 18px;
  padding: 18px;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  background: var(--bg-card);
  transition:
    background .18s ease,
    transform .18s var(--ease-out-quart, ease);
}
.news-item:hover {
  background: var(--bg-hover);
  transform: translateX(2px);
}
.news-item-main {
  min-width: 0;
}
.news-title-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.news-title-row h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  line-height: 1.55;
  font-weight: 700;
  letter-spacing: 0;
  text-wrap: pretty;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.category-chip {
  flex-shrink: 0;
  margin-top: 3px;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--accent);
  background: rgba(34, 211, 238, .10);
  font-size: 11px;
  font-weight: 700;
}
.news-summary {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.news-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
}
.inline-link {
  border: 0;
  background: transparent;
  color: var(--accent);
  padding: 0;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.news-thumb {
  width: 156px;
  height: 96px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
}
.news-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--text-dim);
  background: var(--bg-hover);
}
.thumb-placeholder svg {
  width: 22px;
  height: 22px;
}
.news-sidebar {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 82px;
}
.sidebar-block {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-card);
  padding: 14px;
}
.sidebar-block h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 800;
}
.hot-item,
.side-game {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: var(--text-primary);
}
.hot-item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid var(--border-light);
}
.hot-item:first-of-type {
  border-top: 0;
}
.hot-item span {
  color: var(--accent);
  font-weight: 800;
}
.hot-item strong {
  font-size: 13px;
  line-height: 1.5;
  font-weight: 650;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.side-game {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 8px;
  align-items: center;
  padding: 9px 0;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
}
.side-game:first-of-type {
  border-top: 0;
}
.side-game em {
  font-style: normal;
  font-weight: 800;
  color: var(--text-primary);
}

@media (max-width: 1080px) {
  .news-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .news-sidebar {
    display: none;
  }
}

@media (max-width: 760px) {
  .news-page {
    padding: 4px 12px 84px;
  }
  .news-hero {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
    padding-bottom: 14px;
  }
  .news-hero h1 {
    font-size: 22px;
  }
  .news-hero-actions {
    justify-content: flex-start;
  }
  .search-wrap {
    width: 100%;
  }
  .score-strip {
    margin: 12px 0 14px;
    padding: 10px;
  }
  .score-strip-header small {
    display: none;
  }
  .today-game-card {
    flex-basis: 190px;
    min-height: 110px;
    padding: 8px 10px;
  }
  .today-game-card .game-score { font-size: 16px; }
  .today-game-card .team-logo { width: 28px; height: 28px; }
  .today-game-card .team-fallback { width: 28px; height: 28px; font-size: 12px; }
  .feed-header {
    padding: 12px;
  }
  .feed-header h2 {
    font-size: 16px;
  }
  .feed-header p {
    display: none;
  }
  .news-item {
    grid-template-columns: minmax(0, 1fr) 112px;
    gap: 12px;
    padding: 14px 12px;
  }
  .news-title-row {
    gap: 6px;
  }
  .category-chip {
    padding: 1px 5px;
    font-size: 10px;
  }
  .news-title-row h3 {
    font-size: 15px;
    line-height: 1.45;
  }
  .news-summary {
    display: none;
  }
  .news-meta {
    gap: 8px;
    margin-top: 10px;
    font-size: 11px;
  }
  .news-meta span:nth-child(3),
  .inline-link {
    display: none;
  }
  .news-thumb {
    width: 112px;
    height: 72px;
  }
  .thumb-placeholder {
    font-size: 15px;
  }
  .pager {
    justify-content: center;
    padding: 0 8px;
  }
  .pager :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }
}

@media (max-width: 420px) {
  .news-item {
    grid-template-columns: minmax(0, 1fr) 96px;
  }
  .news-thumb {
    width: 96px;
    height: 66px;
  }
  .news-meta span:nth-child(2) {
    display: none;
  }
}

.toolbar { margin-bottom: 16px; }
/* 搜索栏 */
.search-input-wrap {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  transition: all var(--duration-fast) var(--ease-smooth);
  max-width: 320px;
}
.search-input-wrap:hover {
  border-color: var(--border-medium);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.search-input-wrap:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-glow);
}
.search-input-icon {
  width: 16px;
  height: 16px;
  color: var(--text-muted);
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 13px;
  font-family: var(--font-body);
  letter-spacing: 0.2px;
  min-width: 180px;
}
.search-input::placeholder {
  color: var(--text-dim);
}
.search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: var(--border-light);
  border-radius: 50%;
  cursor: pointer;
  padding: 0;
  transition: all var(--duration-fast) var(--ease-smooth);
  flex-shrink: 0;
}
.search-clear svg {
  width: 11px;
  height: 11px;
  color: var(--text-muted);
}
.search-clear:hover {
  background: var(--border-medium);
}
.search-clear:hover svg {
  color: var(--text-primary);
}
.cell-title {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.cell-link {
  color: var(--accent);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  font-weight: 500;
  position: relative;
  letter-spacing: 0.2px;
}
.cell-link::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 0;
  height: 1px;
  background: var(--accent);
  transition: width var(--duration-normal) var(--ease-smooth);
}
.cell-link:hover {
  color: var(--accent-light);
}
.cell-link:hover::after {
  width: 100%;
}
.mr-8 { margin-right: 8px; }
.mx-4 { margin: 0 4px; color: var(--text-dim); font-weight: 600; }
.score-inline {
  color: var(--accent);
  font-weight: 700;
  font-family: var(--font-heading);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
:deep(.el-card) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-lg) !important;
}
:deep(.el-input__wrapper) {
  background: var(--bg-input) !important;
  border: 1.5px solid var(--border-light) !important;
  box-shadow: none !important;
  border-radius: var(--radius-sm) !important;
}
:deep(.el-input__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 2px var(--accent-glow) !important;
}
:deep(.el-input__inner) { color: var(--text-primary) !important; font-family: var(--font-body); }
:deep(.el-input__inner::placeholder) { color: var(--text-dim) !important; }
/* 详情弹窗 */
.detail-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.detail-time {
  color: var(--text-muted);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-heading);
}
.detail-score {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  padding: 24px 0;
  background: linear-gradient(135deg, var(--accent-lighter) 0%, transparent 100%);
  border-radius: var(--radius-lg);
  border: 1px solid var(--accent-glow);
}
.score-box {
  text-align: center;
  min-width: 80px;
}
.score-box strong {
  display: block;
  font-size: 16px;
  color: var(--text-primary);
  margin-bottom: 8px;
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
}
.big-score {
  font-size: 48px;
  font-weight: 700;
  color: var(--accent);
  font-family: var(--font-heading);
  text-shadow: 0 0 30px var(--accent-glow);
  line-height: 1;
  letter-spacing: -1px;
}
.score-divider {
  font-size: 36px;
  font-weight: 300;
  color: var(--text-dim);
}
.detail-vs {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32px;
  padding: 28px 0;
  background: linear-gradient(135deg, var(--accent-lighter) 0%, transparent 100%);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
}
.detail-team {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.detail-vs-text {
  font-size: 15px;
  color: var(--text-dim);
  font-weight: 700;
  letter-spacing: 3px;
  text-transform: uppercase;
}
.detail-content {
  font-size: 15px;
  line-height: 1.9;
  color: var(--text-secondary);
  white-space: pre-wrap;
  letter-spacing: 0.2px;
}
.detail-stats {
  display: flex;
  gap: 20px;
  margin-top: 16px;
  padding: 14px 18px;
  background: var(--bg-hover);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-muted);
  position: relative;
  overflow: hidden;
}
.detail-stats::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), var(--purple));
  border-radius: 1px;
}
.detail-image {
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.detail-image img {
  width: 100%;
  height: auto;
  max-height: 300px;
  object-fit: cover;
  display: block;
}
.detail-actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.detail-actions :deep(.el-button),
.detail-actions :deep(.share-trigger),
.detail-actions :deep(.el-tooltip__trigger) {
  height: 36px !important;
  min-height: 36px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  box-sizing: border-box !important;
  margin: 0 !important;
  line-height: 1 !important;
  vertical-align: middle !important;
}

.detail-actions :deep(.el-button > span),
.detail-actions :deep(.share-trigger > span) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  line-height: 1;
}

.detail-actions :deep(svg) {
  display: block;
  flex-shrink: 0;
}

/* 浅色主题覆盖 */
[data-theme="light"] .game-card {
  background: linear-gradient(135deg, var(--bg-card) 0%, #FAFAF8 100%);
}
[data-theme="light"] .game-card:hover {
  box-shadow: 0 8px 24px rgba(34, 211, 238, 0.12) !important;
}
[data-theme="light"] .detail-score {
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.04) 0%, rgba(34, 211, 238, 0.01) 100%);
  border-color: rgba(34, 211, 238, 0.12);
}
[data-theme="light"] .detail-vs {
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.03) 0%, transparent 100%);
}
[data-theme="light"] .search-input-wrap {
  background: var(--bg-card);
  border-color: var(--border-medium);
}
[data-theme="light"] .cell-link:hover {
  color: #0E7490;
}
</style>
