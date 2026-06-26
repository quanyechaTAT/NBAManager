<template>
  <div class="page">
    <div class="page-inner">
      <!-- 页面头部 -->
      <div class="page-header">
        <div>
          <h1>球员</h1>
          <p>NBA 球员数据与排名</p>
        </div>
        <div class="header-actions">
          <div class="view-toggle" v-if="auth.isAdmin">
            <button class="toggle-btn" :class="{ active: viewMode === 'cards' }" @click="viewMode = 'cards'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
            </button>
            <button class="toggle-btn" :class="{ active: viewMode === 'table' }" @click="viewMode = 'table'; load()">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
            </button>
          </div>
          <el-button type="success" plain @click="exportExcel" size="small">导出 Excel</el-button>
          <el-button v-if="auth.isAdmin && viewMode === 'table'" type="primary" @click="openCreate">新建球员</el-button>
        </div>
      </div>

      <!-- 搜索筛选 -->
      <div class="filter-bar">
        <div class="filter-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input v-model="keyword" type="text" placeholder="搜索球员姓名..." @keyup.enter="load" />
        </div>
        <el-select v-model="filterTeamId" clearable placeholder="球队" style="width: 140px" @change="onFilterChange" size="default">
          <el-option v-for="t in teamOptions" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filterPosition" clearable placeholder="位置" style="width: 120px" @change="onFilterChange" size="default">
          <el-option v-for="p in positionOptions" :key="p" :label="p" :value="p" />
        </el-select>
        <button class="filter-reset" v-if="keyword || filterTeamId || filterPosition" @click="resetFilters">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M18 6L6 18M6 6l12 12"/></svg>
          清除
        </button>
      </div>

      <!-- 卡片视图（默认） -->
      <template v-if="viewMode === 'cards'">
        <div class="player-grid" v-loading="loading">
          <div v-for="row in rows" :key="row.id" class="player-card" @click="goToPlayerDetail(row)">
            <div class="player-card-avatar">
              <img v-if="row.nbaPlayerId" :src="`https://cdn.nba.com/headshots/nba/latest/260x190/${row.nbaPlayerId}.png`" :alt="row.name" loading="lazy" @error="onThumbError($event)" />
              <span v-else class="player-card-avatar-fallback">{{ row.name?.charAt(0) }}</span>
            </div>
            <div class="player-card-info">
              <span class="player-card-name">{{ row.name }}</span>
              <span class="player-card-meta">
                <img v-if="getTeamLogo(row.teamName)" :src="getTeamLogo(row.teamName)" class="player-card-team-logo" alt="" />
                {{ row.teamName }} · {{ row.position }}
              </span>
            </div>
            <div class="player-card-stats">
              <div class="player-card-stat">
                <span class="stat-val">{{ row.pointsPerGame?.toFixed(1) }}</span>
                <span class="stat-lbl">得分</span>
              </div>
              <div class="player-card-stat">
                <span class="stat-val">{{ row.reboundsPerGame?.toFixed(1) }}</span>
                <span class="stat-lbl">篮板</span>
              </div>
              <div class="player-card-stat">
                <span class="stat-val">{{ row.assistsPerGame?.toFixed(1) }}</span>
                <span class="stat-lbl">助攻</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!loading && rows.length === 0" description="暂无球员数据" />
        </div>
        <div class="pager" v-if="total > 0">
          <el-pagination background layout="total, prev, pager, next" :total="total" v-model:current-page="page" :page-size="21" @current-change="load" />
        </div>
      </template>

      <!-- 表格视图（管理员） -->
      <template v-if="viewMode === 'table' && auth.isAdmin">
        <div class="card">
          <el-table :data="rows" border stripe v-loading="loading" @sort-change="onSortChange" style="width: 100%">
            <el-table-column label="姓名" min-width="140">
              <template #default="{ row }">
                <div class="player-cell" @click="goToPlayerDetail(row)">
                  <img v-if="row.nbaPlayerId" :src="`https://cdn.nba.com/headshots/nba/latest/260x190/${row.nbaPlayerId}.png`" class="player-thumb" @error="onThumbError($event)" />
                  <span class="player-name-link">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="teamName" label="球队" min-width="80" />
            <el-table-column prop="position" label="位置" min-width="55" align="center" />
            <el-table-column prop="jerseyNumber" label="球衣" min-width="50" align="center" />
            <el-table-column prop="gamesPlayed" label="出场" min-width="50" align="center" sortable="custom" />
            <el-table-column prop="minutesPerGame" label="时间" min-width="55" align="center" sortable="custom">
              <template #default="{ row }">{{ row.minutesPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="pointsPerGame" label="得分" min-width="60" align="center" sortable="custom">
              <template #default="{ row }"><span class="stat-highlight">{{ row.pointsPerGame?.toFixed(1) }}</span></template>
            </el-table-column>
            <el-table-column prop="reboundsPerGame" label="篮板" min-width="60" align="center" sortable="custom">
              <template #default="{ row }">{{ row.reboundsPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="assistsPerGame" label="助攻" min-width="60" align="center" sortable="custom">
              <template #default="{ row }">{{ row.assistsPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="stealsPerGame" label="抢断" min-width="55" align="center" sortable="custom">
              <template #default="{ row }">{{ row.stealsPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="blocksPerGame" label="盖帽" min-width="55" align="center" sortable="custom">
              <template #default="{ row }">{{ row.blocksPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="投篮%" min-width="60" align="center" sortable="custom">
              <template #default="{ row }">{{ (row.fieldGoalPct * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="三分%" min-width="60" align="center" sortable="custom">
              <template #default="{ row }">{{ (row.threePointPct * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="罚球%" min-width="60" align="center" sortable="custom">
              <template #default="{ row }">{{ (row.freeThrowPct * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column prop="efficiency" label="效率" min-width="55" align="center" sortable="custom" />
            <el-table-column label="操作" min-width="100" v-if="auth.isAdmin">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="total, prev, pager, next, sizes" :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
          </div>
        </div>
      </template>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="editingId ? '编辑球员' : '新建球员'" width="680px" destroy-on-close class="dialog-light" :append-to-body="true" :center="true">
        <el-form :model="form" label-width="100px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="球队"><el-select v-model="form.teamId" filterable style="width:100%"><el-option v-for="t in teamOptions" :key="t.id" :label="t.name" :value="t.id" /></el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="位置"><el-select v-model="form.position" style="width:100%"><el-option v-for="p in positionOptions" :key="p" :label="p" :value="p" /></el-select></el-form-item></el-col>
          </el-row>
          <el-divider content-position="left">场均数据</el-divider>
          <el-row :gutter="16">
            <el-col :span="8"><el-form-item label="得分"><el-input-number v-model="form.pointsPerGame" :min="0" :step="0.1" :precision="1" style="width:100%" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="篮板"><el-input-number v-model="form.reboundsPerGame" :min="0" :step="0.1" :precision="1" style="width:100%" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="助攻"><el-input-number v-model="form.assistsPerGame" :min="0" :step="0.1" :precision="1" style="width:100%" /></el-form-item></el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { useAuthStore } from '@/stores/auth'
import { createPlayer, deletePlayer, fetchPlayers, updatePlayer } from '@/api/player'
import { fetchTeams } from '@/api/team'
import type { Player, Team } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'

const auth = useAuthStore()
const router = useRouter()
const viewMode = ref<'cards' | 'table'>('cards')
const loading = ref(false)
const saving = ref(false)
const rows = ref<Player[]>([])
const teamOptions = ref<Team[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(21)
const keyword = ref('')
const filterTeamId = ref<number | undefined>(undefined)
const filterPosition = ref<string | undefined>(undefined)
const sortField = ref<string | undefined>(undefined)
const sortOrder = ref<string | undefined>(undefined)
const positionOptions = ['控卫', '分卫', '小前锋', '大前锋', '中锋']

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '', teamId: 0, position: '控卫',
  pointsPerGame: 15, reboundsPerGame: 4, assistsPerGame: 3,
  stealsPerGame: 1, blocksPerGame: 0.5, turnoversPerGame: 1.5,
  gamesPlayed: 65, minutesPerGame: 28,
  fieldGoalPct: 0.46, threePointPct: 0.35, freeThrowPct: 0.8,
  efficiency: 15, trueShootingPct: 0.57, usagePct: 20,
  jerseyNumber: '', height: '6-6', weight: 210, country: '美国',
})

async function loadTeams() {
  try {
    const { data } = await fetchTeams({ page: 0, size: 100 })
    teamOptions.value = data.content
    if (!form.teamId && data.content.length) form.teamId = data.content[0].id
  } catch { /* ignore */ }
}

async function load() {
  loading.value = true
  try {
    const params: any = { page: page.value - 1, size: size.value }
    if (keyword.value) params.q = keyword.value
    if (filterTeamId.value) params.teamId = filterTeamId.value
    if (filterPosition.value) params.position = filterPosition.value
    if (sortField.value) params.sort = `${sortField.value},${sortOrder.value === 'ascending' ? 'asc' : 'desc'}`
    const { data } = await fetchPlayers(params)
    rows.value = data.content
    total.value = data.totalElements
  } catch { ElMessage.error('加载球员失败') } finally { loading.value = false }
}

function onFilterChange() { page.value = 1; load() }
function onSortChange({ prop, order }: any) { sortField.value = prop; sortOrder.value = order; load() }
function resetFilters() { keyword.value = ''; filterTeamId.value = undefined; filterPosition.value = undefined; page.value = 1; load() }
function goToPlayerDetail(row: Player) { router.push({ path: '/players/detail', query: { id: String(row.id) } }) }
function onThumbError(e: Event) { (e.target as HTMLImageElement).style.display = 'none' }

function openCreate() {
  editingId.value = null
  form.name = ''; form.teamId = teamOptions.value[0]?.id || 0; form.position = '控卫'
  dialogVisible.value = true
}

function openEdit(row: Player) {
  editingId.value = row.id
  form.name = row.name; form.teamId = row.teamId; form.position = row.position
  form.pointsPerGame = row.pointsPerGame; form.reboundsPerGame = row.reboundsPerGame; form.assistsPerGame = row.assistsPerGame
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) { await updatePlayer(editingId.value, form as any); ElMessage.success('已更新') }
    else { await createPlayer(form as any); ElMessage.success('已创建') }
    dialogVisible.value = false; await load()
  } catch { ElMessage.error('保存失败') } finally { saving.value = false }
}

async function onDelete(row: Player) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '提示', { type: 'warning' })
    await deletePlayer(row.id); ElMessage.success('已删除'); await load()
  } catch { /* cancel */ }
}

async function exportExcel() {
  ElMessage.info('正在导出…')
  try {
    const { data } = await fetchPlayers({ q: keyword.value || undefined, teamId: filterTeamId.value || undefined, position: filterPosition.value || undefined, page: 0, size: 9999 })
    const header = ['姓名', '球队', '位置', '得分', '篮板', '助攻', '抢断', '盖帽', '命中率%', '三分%']
    const rows4Excel = data.content.map((p: Player) => [p.name, p.teamName, p.position, p.pointsPerGame, p.reboundsPerGame, p.assistsPerGame, p.stealsPerGame, p.blocksPerGame, (p.fieldGoalPct * 100).toFixed(1), (p.threePointPct * 100).toFixed(1)])
    const ws = XLSX.utils.aoa_to_sheet([header, ...rows4Excel])
    const wb = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(wb, ws, '球员数据')
    const buf = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
    saveAs(new Blob([buf], { type: 'application/octet-stream' }), `球员数据_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

onMounted(async () => { await loadTeams(); await load() })
</script>

<style scoped>
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

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-header h1 { margin: 0 0 4px; font-size: 24px; font-weight: 700; color: var(--text-primary); font-family: var(--font-heading); }
.page-header p { margin: 0; font-size: 14px; color: var(--text-muted); }
.header-actions { display: flex; align-items: center; gap: 12px; }
.view-toggle { display: flex; border: 1px solid var(--border-light); border-radius: 6px; overflow: hidden; }
.toggle-btn { display: flex; align-items: center; justify-content: center; width: 34px; height: 34px; background: var(--bg-card); border: none; color: var(--text-muted); cursor: pointer; transition: all 0.15s; }
.toggle-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.toggle-btn.active { background: var(--accent-lighter); color: var(--accent); }

/* 搜索筛选栏 */
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  flex: 1;
  max-width: 280px;
  transition: border-color 0.15s;
}
.filter-search:focus-within { border-color: var(--accent); }
.filter-search svg { color: var(--text-muted); flex-shrink: 0; }
.filter-search input { background: none; border: none; outline: none; color: var(--text-primary); font-size: 13px; width: 100%; }
.filter-search input::placeholder { color: var(--text-dim); }
.filter-reset {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-reset:hover { border-color: var(--danger); color: var(--danger); }

/* 球员卡片网格 */
.player-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.player-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
}
.player-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-sm);
}
.player-card-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
}
.player-card-avatar img { width: 100%; height: 100%; object-fit: cover; }
.player-card-avatar-fallback { font-size: 18px; font-weight: 700; color: var(--text-muted); }
.player-card-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.player-card-name { font-size: 14px; font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.player-card-meta { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-muted); }
.player-card-team-logo { width: 14px; height: 14px; object-fit: contain; }
.player-card-stats { display: flex; gap: 12px; flex-shrink: 0; }
.player-card-stat { display: flex; flex-direction: column; align-items: center; gap: 1px; min-width: 36px; }
.stat-val { font-size: 14px; font-weight: 700; color: var(--text-primary); font-family: var(--font-heading); font-variant-numeric: tabular-nums; }
.stat-lbl { font-size: 10px; color: var(--text-muted); }

/* 表格视图 */
.card { background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-lg); padding: 16px; overflow: hidden; }
.player-cell { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.player-thumb { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; background: var(--bg-hover); flex-shrink: 0; }
.player-name-link { color: var(--accent); cursor: pointer; font-weight: 500; }
.player-name-link:hover { color: var(--accent-light); }
.stat-highlight { font-weight: 700; color: var(--accent); }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }

/* 移动端 */
@media (max-width: 768px) {
  .player-grid { grid-template-columns: repeat(2, 1fr); }
  .player-card-stats { gap: 8px; }
  .filter-search { max-width: 100%; }
}
@media (max-width: 480px) {
  .player-grid { grid-template-columns: 1fr; }
}
</style>
