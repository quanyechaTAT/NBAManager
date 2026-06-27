<template>
  <div class="news-page">
    <section class="news-hero">
      <div>
        <h1>赛事资讯</h1>
        <p>{{ todayStr }} · 今日赛程、联盟动态与赛后深度</p>
      </div>
      <div class="news-hero-actions">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input v-model="q" class="news-search" type="text" placeholder="搜索标题、球队、专题" @keyup.enter="load" />
          <button v-if="q" @click="q = ''; load()" class="search-clear" aria-label="清除搜索">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
        </div>
        <el-button type="primary" plain size="small" @click="refreshAll" :loading="refreshing">刷新</el-button>
        <el-button v-if="auth.isAdmin" type="primary" size="small" @click="openCreate">新增资讯</el-button>
      </div>
    </section>

    <section class="score-strip" v-loading="todayLoading">
      <div class="score-strip-header">
        <span>今日赛事</span>
        <small>横向滑动查看更多</small>
      </div>
      <div class="today-scroll">
        <article
          v-for="item in playableTodayGames"
          :key="item.id"
          class="today-game-card"
          @click="goToMatchDetail(item)"
        >
          <div class="game-card-top">
            <span class="status-pill" :class="item.status?.toLowerCase()">{{ statusLabel(item.status) }}</span>
            <span class="game-time">{{ formatGameTime(item.gameStartTime) }}</span>
          </div>
          <div class="game-matchup">
            <div class="game-team">
              <img v-if="teamLogo(item.homeTeam)" :src="teamLogo(item.homeTeam)" class="team-logo" />
              <span class="team-fallback" v-else>{{ fallbackTeamInitial(item.homeTeam) }}</span>
              <strong>{{ item.homeTeam }}</strong>
            </div>
            <div class="game-score">
              <span :class="{ winner: isWinner(item, 'home') }">{{ displayScore(item.homeScore) }}</span>
              <em>-</em>
              <span :class="{ winner: isWinner(item, 'away') }">{{ displayScore(item.awayScore) }}</span>
            </div>
            <div class="game-team">
              <img v-if="teamLogo(item.awayTeam)" :src="teamLogo(item.awayTeam)" class="team-logo" />
              <span class="team-fallback" v-else>{{ fallbackTeamInitial(item.awayTeam) }}</span>
              <strong>{{ item.awayTeam }}</strong>
            </div>
          </div>
        </article>
        <div v-if="!todayLoading && playableTodayGames.length === 0" class="today-empty">
          今日暂无可查看详情的赛事
        </div>
      </div>
    </section>

    <section class="news-layout">
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
                <span>{{ fallbackBadge(row) }}</span>
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
          <el-button v-if="current.nbaGameId" type="primary" size="small" @click="goToMatchDetail(current)">📊 查看比赛详细数据</el-button>
          <el-tooltip v-else content="该新闻未关联NBA比赛ID，无法查看比赛详情" placement="top">
            <el-button type="info" plain size="small" disabled>📊 比赛数据（暂无）</el-button>
          </el-tooltip>
        </div>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchNews, fetchNewsDetail, fetchTodayNews, createNews, updateNews, deleteNews, toggleNewsFavorite, type GameNewsPayload } from '@/api/news'
import { recordBrowse } from '@/api/browseHistory'
import { useAuthStore } from '@/stores/auth'
import { sanitizeHtml } from '@/utils/sanitize'
import ShareButton from '@/components/ShareButton.vue'
import type { GameNews } from '@/api/types'
import request from '@/utils/request'
import { getTeamLogo } from '@/utils/teamLogos'

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
  const map: Record<string, string> = { game: 'primary', trade: 'success', injury: 'danger', draft: 'warning' }
  return map[c] || 'info'
}
function categoryLabel(c: string) {
  const map: Record<string, string> = { game: '赛事', trade: '交易', injury: '伤病', draft: '选秀', general: '综合' }
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

function teamLogo(name: string | null | undefined): string | null {
  if (!name) return null
  return getTeamLogo(name) || null
}

function fallbackTeamInitial(name: string | null | undefined): string {
  return name ? name.charAt(0) : '?'
}
function formatTeamName(name: string | null | undefined): string {
  if (!name || name === '待定' || name === 'TBD') return '待定'
  return name
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
function fallbackBadge(item: GameNews) {
  const home = formatTeamName(item.homeTeam)
  if (home && home !== '待定') return home.slice(0, 2)
  return categoryLabel(item.category || 'general').slice(0, 2)
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

async function load() {
  newsLoading.value = true
  try {
    const res = await fetchNews({ q: q.value || '', page: page.value - 1, size: size.value })
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

// 刷新所有数据（从API同步最新数据）
async function refreshAll() {
  refreshing.value = true
  try {
    // 同时触发新闻和比赛同步
    await Promise.all([
      request.post('/admin/sync/news'),
      request.post('/admin/sync/matches')
    ])
    ElMessage.success('数据同步已启动')

    // 等待后端同步完成后重新加载（轮询最多10秒）
    for (let i = 0; i < 5; i++) {
      await new Promise(r => setTimeout(r, 2000))
      await Promise.all([loadToday(), load()])
      // 如果今日赛事已加载，提前退出
      if (todayNews.value.length > 0) break
    }
    refreshing.value = false
  } catch (error: any) {
    console.error('同步失败:', error)
    const msg = error.response?.data?.message || error.message || '同步失败'
    ElMessage.error(`同步失败: ${msg}`)
    await Promise.all([loadToday(), load()])
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

function openEdit(item: GameNews) {
  editingId.value = item.id
  formTitle.value = '编辑赛事资讯'
  form.title = item.title
  form.summary = item.summary
  form.content = item.content
  form.homeTeam = item.homeTeam
  form.awayTeam = item.awayTeam
  form.homeScore = item.homeScore
  form.awayScore = item.awayScore
  form.gameStartTime = item.gameStartTime
  form.gameEndTime = item.gameEndTime
  form.nbaGameId = item.nbaGameId || ''
  form.category = item.category || 'general'
  form.sourceUrl = item.sourceUrl || ''
  form.imageUrl = item.imageUrl || ''
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

async function handleDelete(id: number) {
  try {
    await deleteNews(id)
    ElMessage.success('删除成功')
    loadToday()
    load()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadToday(), load()])
  // 如果 URL 中有 newsId 参数，自动打开对应的资讯详情
  const newsId = route.query.newsId
  if (newsId) {
    const id = Number(newsId)
    const found = list.value.find(n => n.id === id) || todayNews.value.find(n => n.id === id)
    if (found) {
      showDetail(found)
    } else {
      // 如果列表中没有，直接从 API 获取（不重复调用 showDetail 避免浏览量+2）
      try {
        const { data } = await fetchNewsDetail(id)
        current.value = data
        dialogVisible.value = true
        if (auth.token) {
          recordBrowse('NEWS', id).catch(() => {})
        }
      } catch { /* ignore */ }
    }
  }
})
</script>

<style scoped>
/* 防止 stagger-in 动画与 el-dialog 关闭动画冲突导致抖动 */
:deep(.el-overlay) { animation: none !important; opacity: 1 !important; transform: none !important; }

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
.today-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  white-space: nowrap;
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
  max-width: 1180px;
  margin: 0 auto;
  padding: 8px 20px 32px;
  color: var(--text-primary);
}
.news-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 10px 0 18px;
  border-bottom: 1px solid var(--border-light);
}
.news-hero h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 800;
  letter-spacing: 0;
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
  color: #fff !important;
  box-shadow: 0 8px 18px rgba(232, 93, 38, 0.18);
}
.news-hero-actions :deep(.el-button--primary.is-plain) {
  background: rgba(232, 93, 38, .08) !important;
  border-color: rgba(232, 93, 38, .24) !important;
  color: var(--accent) !important;
  box-shadow: none;
}
.news-hero-actions :deep(.el-button--primary:hover),
.news-hero-actions :deep(.el-button--primary:focus) {
  background: #d94f1d !important;
  border-color: #d94f1d !important;
  color: #fff !important;
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
  box-shadow: 0 0 0 3px rgba(232, 93, 38, 0.10);
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
  min-height: 130px;
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
.status-pill.scheduled { color: var(--accent); background: rgba(232,93,38,.08); }
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
.today-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  white-space: nowrap;
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
  border-radius: 8px;
  background: var(--bg-card);
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
  transition: background .15s ease;
}
.news-item:hover {
  background: var(--bg-hover);
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
  background: rgba(232, 93, 38, .10);
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
  color: var(--accent);
  background: linear-gradient(135deg, rgba(232, 93, 38, .10), rgba(16, 185, 129, .08));
  font-size: 18px;
  font-weight: 800;
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
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

/* 浅色主题覆盖 */
[data-theme="light"] .game-card {
  background: linear-gradient(135deg, var(--bg-card) 0%, #FAFAF8 100%);
}
[data-theme="light"] .game-card:hover {
  box-shadow: 0 8px 24px rgba(232, 93, 38, 0.12) !important;
}
[data-theme="light"] .detail-score {
  background: linear-gradient(135deg, rgba(232, 93, 38, 0.04) 0%, rgba(232, 93, 38, 0.01) 100%);
  border-color: rgba(232, 93, 38, 0.12);
}
[data-theme="light"] .detail-vs {
  background: linear-gradient(135deg, rgba(232, 93, 38, 0.03) 0%, transparent 100%);
}
[data-theme="light"] .search-input-wrap {
  background: var(--bg-card);
  border-color: var(--border-medium);
}
[data-theme="light"] .cell-link:hover {
  color: #D14E1F;
}
</style>
