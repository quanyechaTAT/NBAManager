<template>
  <div class="page-wrapper animated-bg">
    <!-- 浮动光晕粒子 -->
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <!-- 科技网格线 -->
    <div class="bg-grid"></div>

    <div class="page-inner">
    <el-card shadow="never" class="card slide-up-enter">
    <!-- 球队 CRUD 表格（仅管理员可见） -->
    <template v-if="auth.isAdmin">
      <div class="section-head">
        <div>
          <h2>球队战绩</h2>
          <p>维护球队基本信息、胜负场数据。</p>
        </div>
        <div class="section-actions">
          <SyncButton module="teams" label="同步球队" @sync-success="loadTeams" />
          <el-button type="primary" @click="openCreate">新建球队</el-button>
        </div>
      </div>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索球队名称" clearable style="width: 260px" @keyup.enter="loadTeams" />
        <el-button type="primary" plain @click="loadTeams">查询</el-button>
      </div>
      <el-table :data="teamRows" border stripe v-loading="loading">
        <el-table-column prop="name" label="球队名称" min-width="140" />
        <el-table-column prop="city" label="城市" width="120" />
        <el-table-column prop="conference" label="分区" width="80" />
        <el-table-column prop="wins" label="胜场" width="80" />
        <el-table-column prop="losses" label="负场" width="80" />
        <el-table-column label="胜率" width="90">
          <template #default="{ row }">{{ winPct(row.wins, row.losses) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next, sizes"
          :total="total"
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[5, 10, 20]"
          @current-change="loadTeams"
          @size-change="loadTeams"
        />
      </div>
    </template>

    <!-- 分区排名 -->
    <div class="section-head" :style="auth.isAdmin ? 'margin-top: 32px' : ''">
      <div>
        <h2>分区排名</h2>
        <p>东西部分区按净胜场排序，含胜场差。</p>
      </div>
    </div>
    <el-row :gutter="16" class="rankings stagger-in" v-loading="rankLoading">
      <el-col :span="12" v-for="(ranks, conf) in rankings" :key="conf">
        <div class="rank-card">
          <div class="rank-header">
            <span class="rank-title">{{ conf === '东部' ? '🏆' : '🌟' }} {{ conf }}联盟排名</span>
            <span class="rank-sub">按胜率排序</span>
          </div>
          <div class="rank-list">
            <div class="rank-row rank-row-head">
              <span class="rank-no">#</span>
              <span class="rank-name">球队</span>
              <span class="rank-stat">胜</span>
              <span class="rank-stat">负</span>
              <span class="rank-stat">胜率</span>
              <span class="rank-stat">胜场差</span>
            </div>
            <div v-for="(r, i) in ranks" :key="r.teamName" class="rank-row" :class="{ 'rank-first': i === 0 }">
              <span class="rank-no" :class="{ 'rank-no-gold': i === 0 }">{{ i + 1 }}</span>
              <span class="rank-name rank-name--link" @click="goTeamDetail(r.teamName)">
                <img v-if="getTeamLogo(r.teamName)" :src="getTeamLogo(r.teamName)" class="rank-team-logo" alt="" />
                {{ r.teamName }}
              </span>
              <span class="rank-stat">{{ r.wins }}</span>
              <span class="rank-stat">{{ r.losses }}</span>
              <span class="rank-stat">{{ winPct(r.wins, r.losses) }}</span>
              <span class="rank-stat">{{ r.gamesBehind > 0 ? r.gamesBehind.toFixed(1) : '—' }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑球队' : '新建球队'" width="480px" destroy-on-close class="dialog-light" :append-to-body="true" :center="true">
      <el-form :model="form" label-width="80px">
        <el-form-item label="球队名称">
          <el-input v-model="form.name" placeholder="如：湖人" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="如：洛杉矶" />
        </el-form-item>
        <el-form-item label="分区">
          <el-select v-model="form.conference" style="width: 100%">
            <el-option label="东部" value="东部" />
            <el-option label="西部" value="西部" />
          </el-select>
        </el-form-item>
        <el-form-item label="胜场">
          <el-input-number v-model="form.wins" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="负场">
          <el-input-number v-model="form.losses" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
  </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { createTeam, deleteTeam, fetchRankings, fetchTeams, updateTeam } from '@/api/team'
import type { Team, TeamRank } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import SyncButton from '@/components/common/SyncButton.vue'

const auth = useAuthStore()
const router = useRouter()

function goTeamDetail(teamName: string) {
  router.push({ path: '/teams/detail', query: { name: teamName } })
}

/* -------- 球队 CRUD -------- */
const loading = ref(false)
const saving = ref(false)
const teamRows = ref<Team[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  city: '',
  conference: '西部',
  wins: 0,
  losses: 0,
})

async function loadTeams() {
  loading.value = true
  try {
    const { data } = await fetchTeams({
      q: keyword.value || undefined,
      page: page.value - 1,
      size: size.value,
    })
    teamRows.value = data.content
    total.value = data.totalElements
  } catch {
    ElMessage.error('加载球队失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.city = ''
  form.conference = '西部'
  form.wins = 0
  form.losses = 0
  dialogVisible.value = true
}

function openEdit(row: Team) {
  editingId.value = row.id
  form.name = row.name
  form.city = row.city
  form.conference = row.conference
  form.wins = row.wins
  form.losses = row.losses
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = {
      name: form.name,
      city: form.city,
      conference: form.conference,
      wins: form.wins,
      losses: form.losses,
    }
    if (editingId.value) {
      await updateTeam(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createTeam(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadTeams()
    await loadRankings()
  } catch (e: unknown) {
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Team) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '提示', { type: 'warning' })
    await deleteTeam(row.id)
    ElMessage.success('已删除')
    await loadTeams()
    await loadRankings()
  } catch (e: unknown) {
    if (e === 'cancel') return
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '删除失败')
  }
}

/* -------- 分区排名 -------- */
const rankings = ref<Record<string, TeamRank[]>>({})
const rankLoading = ref(false)

async function loadRankings() {
  rankLoading.value = true
  try {
    const { data } = await fetchRankings()
    rankings.value = data
  } finally {
    rankLoading.value = false
  }
}

function winPct(w: number, l: number) {
  const total = w + l
  if (total === 0) return '0.0%'
  return ((w / total) * 100).toFixed(1) + '%'
}

onMounted(async () => {
  const promises: Promise<void>[] = [loadRankings()]
  if (auth.isAdmin) {
    promises.push(loadTeams())
  }
  await Promise.all(promises)
})
</script>

<style scoped>
.page-wrapper {
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  padding: 0;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
  position: relative;
}
.section-head::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, var(--purple), transparent);
  border-radius: 1px;
}
.section-head h2 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.section-head p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.card {
  max-width: 1100px;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  z-index: 1;
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
:deep(.el-input__wrapper) {
  background: var(--bg-input) !important;
  border: 1.5px solid var(--border-light) !important;
  box-shadow: none !important;
  border-radius: 8px !important;
}
:deep(.el-input__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px var(--accent-glow) !important;
}
:deep(.el-input__inner) { color: var(--text-primary) !important; font-family: var(--font-body); }
:deep(.el-input__inner::placeholder) { color: var(--text-dim) !important; }

/* ---- 排名区域 ---- */
.rankings { margin-bottom: 20px; }
.rank-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 20px;
  height: 100%;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  overflow: hidden;
  animation: rankCardIn 0.5s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
.rank-card:nth-child(1) { animation-delay: 0.05s; }
.rank-card:nth-child(2) { animation-delay: 0.1s; }
@keyframes rankCardIn { to { opacity: 1; transform: translateY(0); } }
.rank-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--purple), var(--purple-light), transparent);
  opacity: 0.5;
}
.rank-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.rank-header {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: baseline;
  gap: 8px;
  position: relative;
}
.rank-header::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, var(--purple), transparent);
  border-radius: 2px;
}
.rank-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.rank-sub { font-size: 12px; color: var(--text-muted); }
.rank-row {
  display: flex; align-items: center;
  padding: 7px 8px; border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-smooth);
}
.rank-row:hover {
  background: var(--bg-hover);
  transform: translateX(4px);
}
.rank-row:hover { background: var(--bg-hover); }
.rank-row-head {
  color: var(--text-muted);
  font-size: 12px; font-weight: 600;
  padding: 4px 8px 8px;
}
.rank-row-head:hover { background: transparent; }
.rank-first {
  background: linear-gradient(90deg, rgba(108, 92, 231, 0.1), rgba(108, 92, 231, 0.02));
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--purple);
}
.rank-no { width: 28px; text-align: center; color: var(--text-muted); font-size: 13px; font-weight: 600; transition: color var(--duration-fast) var(--ease-smooth); }
.rank-no-gold { color: var(--accent); }
.rank-name { flex: 1; color: var(--text-primary); font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 6px; }
.rank-team-logo { width: 22px; height: 22px; object-fit: contain; flex-shrink: 0; }
.rank-name--link {
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-smooth);
  position: relative;
}
.rank-name--link:hover {
  color: var(--accent);
}
.rank-name--link::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 1px;
  background: var(--accent);
  transition: width var(--duration-normal) var(--ease-smooth);
}
.rank-name--link:hover::after {
  width: 100%;
}
.rank-stat { width: 52px; text-align: center; color: var(--text-secondary); font-size: 13px; font-family: var(--font-heading); }
</style>
