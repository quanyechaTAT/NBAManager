<template>
  <div class="page">
    <!-- 今日赛事卡片 -->
    <div class="section-header">
      <h2>📅 今日赛事</h2>
      <span class="date">{{ todayStr }}</span>
      <el-button v-if="auth.isAdmin" type="primary" size="small" @click="openCreate">+ 新增资讯</el-button>
    </div>
    <el-row :gutter="16" class="today-games" v-loading="todayLoading">
      <template v-if="todayNews.length">
        <el-col :xs="24" :sm="12" :md="8" v-for="item in todayNews" :key="item.id">
          <el-card shadow="hover" class="game-card" :class="`game-card--${item.status.toLowerCase()}`" @click="showDetail(item)">
            <div class="game-teams">
              <div class="team team-home">
                <span class="team-name">{{ item.homeTeam }}</span>
                <span class="team-score" v-if="item.status === 'FINISHED'">{{ item.homeScore }}</span>
              </div>
              <div class="game-vs">
                <span class="vs-text">VS</span>
                <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <div class="team team-away">
                <span class="team-name">{{ item.awayTeam }}</span>
                <span class="team-score" v-if="item.status === 'FINISHED'">{{ item.awayScore }}</span>
              </div>
            </div>
            <div class="game-info">
              <span class="game-time">🕐 {{ formatTimeRange(item.gameStartTime, item.gameEndTime) }}</span>
            </div>
            <div class="game-title">{{ item.title }}</div>
            <div class="game-summary">{{ item.summary }}</div>
          </el-card>
        </el-col>
      </template>
      <el-empty v-else description="今日暂无赛事安排" />
    </el-row>

    <!-- 全部资讯列表 -->
    <div class="section-header" style="margin-top: 28px;">
      <h2>📰 全部资讯</h2>
    </div>
    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="q" placeholder="搜索标题/球队..." clearable style="width: 280px" @keyup.enter="load" @clear="load" />
      </div>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="280">
          <template #default="{ row }">
            <div class="cell-title" @click="showDetail(row)">
              <el-tag :type="statusType(row.status)" size="small" class="mr-8">{{ statusLabel(row.status) }}</el-tag>
              <span class="cell-link">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="对阵" width="200">
          <template #default="{ row }">
            <span>{{ row.homeTeam }}</span>
            <span v-if="row.status === 'FINISHED'" class="score-inline"> {{ row.homeScore }}</span>
            <span class="mx-4">VS</span>
            <span>{{ row.awayTeam }}</span>
            <span v-if="row.status === 'FINISHED'" class="score-inline"> {{ row.awayScore }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="比赛时间" width="210">
          <template #default="{ row }">{{ formatTimeRange(row.gameStartTime, row.gameEndTime) }}</template>
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
    <el-dialog v-model="dialogVisible" :title="current?.title" width="620px" destroy-on-close>
      <template v-if="current">
        <div class="detail-meta">
          <el-tag :type="statusType(current.status)">{{ statusLabel(current.status) }}</el-tag>
          <span class="detail-time">🕐 {{ formatTimeRange(current.gameStartTime, current.gameEndTime) }}</span>
        </div>
        <div class="detail-score" v-if="current.status === 'FINISHED'">
          <div class="score-box"><strong>{{ current.homeTeam }}</strong><span class="big-score">{{ current.homeScore }}</span></div>
          <span class="score-divider">:</span>
          <div class="score-box"><strong>{{ current.awayTeam }}</strong><span class="big-score">{{ current.awayScore }}</span></div>
        </div>
        <div class="detail-vs" v-else>
          <span class="detail-team">{{ current.homeTeam }}</span><span class="detail-vs-text">VS</span><span class="detail-team">{{ current.awayTeam }}</span>
        </div>
        <el-divider />
        <div class="detail-content">{{ current.content }}</div>
      </template>
    </el-dialog>

    <!-- 新增/编辑对话框（管理员） -->
    <el-dialog v-model="formVisible" :title="formTitle" width="620px" destroy-on-close @closed="resetForm">
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
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="gameStartTime">
              <el-date-picker v-model="form.gameStartTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="gameEndTime">
              <el-date-picker v-model="form.gameEndTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchNews, fetchTodayNews, createNews, updateNews, deleteNews, type GameNewsPayload } from '@/api/news'
import { useAuthStore } from '@/stores/auth'
import type { GameNews } from '@/api/types'

const auth = useAuthStore()

const q = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<GameNews[]>([])
const loading = ref(false)
const todayNews = ref<GameNews[]>([])
const todayLoading = ref(false)
const dialogVisible = ref(false)
const current = ref<GameNews | null>(null)

// 表单相关
const formVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<GameNewsPayload>({
  title: '', summary: '', content: '', homeTeam: '', awayTeam: '',
  homeScore: null, awayScore: null, gameStartTime: '', gameEndTime: '',
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
  loading.value = true
  try {
    const res = await fetchNews({ q: q.value || '', page: page.value - 1, size: size.value })
    list.value = res.data.content
    total.value = res.data.totalElements
  } catch {
    ElMessage.error('加载赛事资讯失败')
  } finally {
    loading.value = false
  }
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

function showDetail(item: GameNews) {
  current.value = item
  dialogVisible.value = true
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

onMounted(() => {
  loadToday()
  load()
})
</script>

<style scoped>
.page {
  padding: 4px 0;
}
.section-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.section-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
  position: relative;
}
.section-header h2::after {
  content: '';
  position: absolute;
  bottom: -13px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent);
  border-radius: 1px;
}
.date {
  color: var(--text-muted);
  font-size: 13px;
}
.today-games {
  min-height: 60px;
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
}
.game-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  z-index: 1;
}
.game-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 230, 118, 0.02) 0%, transparent 40%);
  pointer-events: none;
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}
.game-card--scheduled::before {
  background: linear-gradient(90deg, var(--warning), #FFB74D);
  box-shadow: 0 0 12px var(--warning-glow);
}
.game-card--live::before {
  background: linear-gradient(90deg, var(--danger), #FF6B7A);
  box-shadow: 0 0 12px var(--danger-glow);
  animation: livePulse 1.5s ease-in-out infinite;
}
.game-card--finished::before {
  background: var(--border-medium);
}
@keyframes livePulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 12px var(--danger-glow); }
  50% { opacity: 0.5; box-shadow: 0 0 20px var(--danger-glow); }
}
.game-card:hover {
  transform: translateY(-3px);
  border-color: var(--accent) !important;
  box-shadow: 0 8px 32px rgba(0, 230, 118, 0.12) !important;
}
.game-card:hover::after {
  opacity: 1;
}
.game-teams {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.team {
  text-align: center;
  flex: 1;
}
.team-name {
  display: block;
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.team-score {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
  margin-top: 4px;
  font-family: var(--font-heading);
  text-shadow: 0 0 20px var(--accent-glow);
}
.game-vs {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
}
.vs-text {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-dim);
  letter-spacing: 2px;
}
.game-info {
  margin-bottom: 8px;
}
.game-time {
  font-size: 12px;
  color: var(--text-muted);
  padding: 4px 10px;
  background: rgba(0, 230, 118, 0.04);
  border-radius: var(--radius-sm);
  display: inline-block;
  border: 1px solid rgba(0, 230, 118, 0.06);
}
.game-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  margin-bottom: 6px;
  letter-spacing: 0.2px;
  line-height: 1.4;
}
.game-summary {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.toolbar {
  margin-bottom: 16px;
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
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
:deep(.el-card) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-lg) !important;
}
:deep(.el-input__wrapper) {
  background: #1C2333 !important;
  border: 1px solid var(--border-light) !important;
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
}
.detail-time {
  color: var(--text-muted);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.detail-score {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  padding: 20px 0;
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
  font-size: 15px;
  color: var(--text-primary);
  margin-bottom: 8px;
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
}
.big-score {
  font-size: 44px;
  font-weight: 700;
  color: var(--accent);
  font-family: var(--font-heading);
  text-shadow: 0 0 30px var(--accent-glow);
  line-height: 1;
}
.score-divider {
  font-size: 32px;
  font-weight: 300;
  color: var(--text-dim);
}
.detail-vs {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32px;
  padding: 24px 0;
  background: linear-gradient(135deg, rgba(0, 230, 118, 0.02) 0%, transparent 100%);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
}
.detail-team {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.detail-vs-text {
  font-size: 14px;
  color: var(--text-dim);
  font-weight: 700;
  letter-spacing: 3px;
  text-transform: uppercase;
}
.detail-content {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-secondary);
  white-space: pre-wrap;
}
</style>
