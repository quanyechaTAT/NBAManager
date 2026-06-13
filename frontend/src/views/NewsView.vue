<template>
  <div class="page animated-bg">
    <!-- 浮动光晕粒子 -->
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <!-- 科技网格线 -->
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
    <!-- 今日赛事卡片 -->
    <div class="section-header">
      <div class="section-header-left">
        <h2>📅 今日赛事</h2>
        <span class="date">{{ todayStr }}</span>
      </div>
      <div class="section-header-right">
        <el-button type="primary" plain size="small" @click="refreshAll" :loading="refreshing">🔄 刷新</el-button>
        <el-button v-if="auth.isAdmin" type="primary" size="small" @click="openCreate">+ 新增资讯</el-button>
      </div>
    </div>
    <el-row :gutter="16" class="today-games stagger-in" v-loading="todayLoading">
      <template v-if="todayNews.length">
        <el-col :xs="24" :sm="12" :md="8" v-for="item in todayNews" :key="item.id">
          <el-card shadow="hover" class="game-card" :class="`game-card--${item.status.toLowerCase()}`" @click="showDetail(item)">
            <div class="game-teams">
              <div class="team team-home">
                <span class="team-name">{{ item.homeTeam || '待定' }}</span>
                <span class="team-score" v-if="item.status === 'LIVE' || item.status === 'FINISHED'">{{ item.homeScore }}</span>
              </div>
              <div class="game-vs">
                <span class="vs-text">VS</span>
                <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <div class="team team-away">
                <span class="team-name">{{ item.awayTeam || '待定' }}</span>
                <span class="team-score" v-if="item.status === 'LIVE' || item.status === 'FINISHED'">{{ item.awayScore }}</span>
              </div>
            </div>
            <div class="game-info">
              <span class="game-time">🕐 {{ formatTimeRange(item.gameStartTime, item.gameEndTime) }}</span>
            </div>
            <div class="game-title">{{ item.title }}</div>
            <div class="game-summary">{{ item.summary }}</div>
            <div class="game-stats">
              <span>👁 {{ item.viewCount || 0 }}</span>
              <span>❤️ {{ item.favoriteCount || 0 }}</span>
              <el-button v-if="auth.token" :type="item.favoritedByMe ? 'danger' : ''" link size="small" @click.stop="toggleFav(item)">
                {{ item.favoritedByMe ? '★ 已收藏' : '☆ 收藏' }}
              </el-button>
            </div>
            <div class="game-actions" v-if="item.nbaGameId">
              <el-button type="primary" plain size="small" class="match-detail-btn" @click.stop="goToMatchDetail(item)">
                📊 比赛详细数据
              </el-button>
            </div>
          </el-card>
        </el-col>
      </template>
      <el-empty v-else description="今日暂无赛事安排" />
    </el-row>

    <!-- 全部资讯列表 -->
    <div class="section-header slide-up-enter" style="margin-top: 28px; animation-delay: 0.3s;">
      <div class="section-header-left">
        <h2>📰 全部资讯</h2>
        <span class="section-sub">赛事资讯汇总</span>
      </div>
    </div>
    <el-card shadow="never" class="news-list-card">
      <div class="toolbar">
        <div class="search-input-wrap">
          <svg class="search-input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input v-model="q" class="search-input" type="text" placeholder="搜索标题/球队..." @keyup.enter="load" />
          <button v-if="q" class="search-clear" @click="q = ''; load()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
        </div>
      </div>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column label="标题" min-width="320">
          <template #default="{ row }">
            <div class="cell-title" @click="showDetail(row)">
              <el-tag :type="statusType(row.status)" size="small" class="mr-8">{{ statusLabel(row.status) }}</el-tag>
              <el-tag v-if="row.nbaGameId && row.category && row.category !== 'general'" :type="categoryType(row.category)" size="small" class="mr-8">{{ categoryLabel(row.category) }}</el-tag>
              <span class="cell-link">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="对阵" width="200">
          <template #default="{ row }">
            <span>{{ row.homeTeam || '待定' }}</span>
            <span v-if="row.status === 'FINISHED' && row.homeScore != null" class="score-inline"> {{ row.homeScore }}</span>
            <span class="mx-4">VS</span>
            <span>{{ row.awayTeam || '待定' }}</span>
            <span v-if="row.status === 'FINISHED' && row.awayScore != null" class="score-inline"> {{ row.awayScore }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="比赛时间" width="210">
          <template #default="{ row }">{{ formatTimeRange(row.gameStartTime, row.gameEndTime) }}</template>
        </el-table-column>
        <el-table-column label="比赛数据" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.nbaGameId" type="primary" link size="small" @click.stop="goToMatchDetail(row)">📊 详情</el-button>
            <span v-else class="no-data-text">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="auth.isAdmin" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该资讯？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link size="small" @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="load" />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="dialogVisible" :title="current?.title" width="680px" class="dialog-light" destroy-on-close>
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
    <el-dialog v-model="formVisible" :title="formTitle" width="680px" destroy-on-close @closed="resetForm" class="dialog-light">
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

const auth = useAuthStore()
const route = useRoute()

const q = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<GameNews[]>([])
const loading = ref(false)
const newsLoading = ref(false)
const refreshing = ref(false)
const todayNews = ref<GameNews[]>([])
const todayLoading = ref(false)
const dialogVisible = ref(false)
const current = ref<GameNews | null>(null)
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
  await loadToday()
  await load()
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
.today-games {
  min-height: 80px;
}
.game-card {
  cursor: pointer;
  margin-bottom: 16px;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-lg) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  overflow: hidden;
  animation: gameCardIn 0.5s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
  padding: 16px !important;
}
.game-card:nth-child(1) { animation-delay: 0.05s; }
.game-card:nth-child(2) { animation-delay: 0.1s; }
.game-card:nth-child(3) { animation-delay: 0.15s; }
@keyframes gameCardIn { to { opacity: 1; transform: translateY(0); } }
.game-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  z-index: 1;
}
.game-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 230, 118, 0.03) 0%, transparent 40%);
  pointer-events: none;
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}
.game-card--scheduled::before {
  background: linear-gradient(90deg, var(--warning), #FFB74D);
  box-shadow: 0 0 12px var(--warning-glow);
}
.game-card--scheduled .team-score {
  color: var(--warning);
  text-shadow: 0 0 16px var(--warning-glow);
}
.game-card--live::before {
  background: linear-gradient(90deg, var(--danger), #FF6B7A);
  box-shadow: 0 0 16px var(--danger-glow);
  animation: livePulse 1.5s ease-in-out infinite;
}
.game-card--live .team-score {
  animation: livePulse 1.5s ease-in-out infinite;
}
.game-card--finished::before {
  background: var(--border-medium);
  opacity: 0.6;
}
.game-card--finished .team-score {
  color: var(--text-secondary);
  text-shadow: none;
  font-size: 24px;
}
@keyframes livePulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 16px var(--danger-glow); }
  50% { opacity: 0.6; box-shadow: 0 0 24px var(--danger-glow); }
}
.game-card:hover {
  transform: translateY(-4px) !important;
  border-color: var(--accent) !important;
  box-shadow: 0 12px 40px rgba(0, 230, 118, 0.18) !important;
  animation: none;
  opacity: 1;
}
.game-card:hover::after {
  opacity: 1;
}
.game-card:hover .game-title {
  color: var(--accent-light);
}
.game-card:hover .team-name {
  color: var(--accent);
  transition: color var(--duration-fast) var(--ease-smooth);
}
.game-card:hover .game-stats {
  color: var(--text-secondary);
  border-top-color: var(--border-medium);
}
.game-teams {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.team {
  text-align: center;
  flex: 1;
}
.team-name {
  display: block;
  font-weight: 700;
  font-size: 16px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.team-score {
  display: block;
  font-size: 30px;
  font-weight: 700;
  color: var(--accent);
  margin-top: 4px;
  font-family: var(--font-heading);
  text-shadow: 0 0 20px var(--accent-glow);
  letter-spacing: -1px;
}
.game-vs {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
}
.vs-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-dim);
  letter-spacing: 2px;
}
.game-info {
  margin-bottom: 10px;
}
.game-time {
  font-size: 12px;
  color: var(--text-muted);
  padding: 4px 10px;
  background: rgba(0, 230, 118, 0.04);
  border-radius: var(--radius-sm);
  display: inline-block;
  border: 1px solid rgba(0, 230, 118, 0.06);
  font-family: var(--font-heading);
}
.game-title {
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  margin-bottom: 6px;
  letter-spacing: 0.2px;
  line-height: 1.4;
}
.game-summary {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  letter-spacing: 0.2px;
}
.game-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0;
  font-size: 12px;
  color: var(--text-dim);
  padding-top: 8px;
  border-top: 1px solid var(--border-light);
}
.game-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.match-detail-btn {
  border-radius: var(--radius-md) !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.3px;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}
.match-detail-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 230, 118, 0.25);
}
.no-data-text {
  color: var(--text-dim);
  font-size: 12px;
  font-style: italic;
}
.toolbar {
  margin-bottom: 20px;
}
.news-list-card {
  overflow: visible;
  position: relative;
}
.news-list-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--cyan), var(--purple));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  z-index: 1;
}
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
  color: #33EB91;
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
  background: linear-gradient(135deg, rgba(0, 230, 118, 0.03) 0%, rgba(0, 230, 118, 0.01) 100%);
  border-radius: var(--radius-lg);
  border: 1px solid rgba(0, 230, 118, 0.08);
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
  background: linear-gradient(135deg, rgba(0, 230, 118, 0.02) 0%, transparent 100%);
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
</style>
