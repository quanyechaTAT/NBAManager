<template>
  <div class="page" :class="{ 'page--table': viewMode === 'table' }">
    <div class="page-inner">
      <!-- 页面头部 -->
      <SportsHeader
        eyebrow="NBA ROSTER & METRICS"
        title="球员数据中心"
        subtitle="浏览 30 支球队现役球员名单、赛季场均数据与综合效率指标"
      >
        <template #actions>
          <div class="view-toggle" aria-label="球员展示方式">
            <button class="toggle-btn" type="button" aria-label="卡片视图" :aria-pressed="viewMode === 'cards'" :class="{ active: viewMode === 'cards' }" @click="viewMode = 'cards'; load()">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
            </button>
            <button class="toggle-btn" type="button" aria-label="表格视图" :aria-pressed="viewMode === 'table'" :class="{ active: viewMode === 'table' }" @click="viewMode = 'table'; load()">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
            </button>
          </div>
          <el-button plain @click="exportExcel" size="small">导出 Excel</el-button>
          <el-button v-if="auth.isAdmin && viewMode === 'table'" type="primary" @click="openCreate">新建球员</el-button>
        </template>
      </SportsHeader>

      <!-- 搜索筛选 -->
      <div class="filter-bar">
        <div class="filter-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input v-model="keyword" type="text" placeholder="搜索球员姓名、位置或球队..." @keyup.enter="load" />
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
          <div
            v-for="row in rows"
            :key="row.id"
            class="player-card glass-card hoverable"
            :style="{ '--team-accent': getTeamPrimaryColor(row.teamName) }"
            role="link"
            tabindex="0"
            @click="goToPlayerDetail(row)"
            @keydown.enter="goToPlayerDetail(row)"
          >
            <!-- 球队品牌色微细条纹 -->
            <span class="player-card-stripe" aria-hidden="true" />

            <!-- 背景艺术化半透明球衣号水印 -->
            <span v-if="row.jerseyNumber" class="player-jersey-watermark" aria-hidden="true">
              #{{ row.jerseyNumber }}
            </span>

            <div class="player-card-top">
              <div class="player-card-avatar">
                <img
                  v-if="row.nbaPlayerId && !brokenAvatars.has(row.id)"
                  :src="`https://cdn.nba.com/headshots/nba/latest/260x190/${row.nbaPlayerId}.png`"
                  :alt="row.name"
                  loading="lazy"
                  @error="onThumbError(row)"
                />
                <span v-else class="player-card-avatar-fallback">{{ row.name?.charAt(0) }}</span>
              </div>
              <div class="player-card-info">
                <div class="player-card-badges">
                  <span class="pos-badge" :class="getPosBadgeClass(row.position)">{{ getPosBadgeLabel(row.position) }}</span>
                  <span v-if="row.jerseyNumber" class="jersey-badge">#{{ row.jerseyNumber }}</span>
                </div>
                <span class="player-card-name" :title="row.name">{{ row.name }}</span>
                <span class="player-card-meta">
                  <img v-if="getTeamLogo(row.teamName)" :src="getTeamLogo(row.teamName)" class="player-card-team-logo" alt="" />
                  <span class="player-card-team-name">{{ row.teamName }}</span>
                </span>
              </div>
            </div>

            <!-- 三项核心数据对齐底座 -->
            <div class="player-card-stats-dock">
              <div class="player-stat-col">
                <span class="stat-val font-tabular">{{ row.pointsPerGame?.toFixed(1) ?? '—' }}</span>
                <span class="stat-lbl">得分 PTS</span>
              </div>
              <span class="dock-sep" aria-hidden="true" />
              <div class="player-stat-col">
                <span class="stat-val font-tabular">{{ row.reboundsPerGame?.toFixed(1) ?? '—' }}</span>
                <span class="stat-lbl">篮板 REB</span>
              </div>
              <span class="dock-sep" aria-hidden="true" />
              <div class="player-stat-col">
                <span class="stat-val font-tabular">{{ row.assistsPerGame?.toFixed(1) ?? '—' }}</span>
                <span class="stat-lbl">助攻 AST</span>
              </div>
            </div>
          </div>
          <AppEmpty v-if="!loading && rows.length === 0" description="暂无球员数据" />
        </div>
        <div class="pager" v-if="total > 0">
          <el-pagination background layout="total, prev, pager, next" :total="total" v-model:current-page="page" :page-size="21" @current-change="load" />
        </div>
      </template>

      <!-- 表格视图：所有用户可读，编辑操作仍仅限管理员。 -->
      <template v-if="viewMode === 'table'">
        <div class="card table-card">
          <p class="player-table-hint">左右滑动可查看全部球员数据</p>
          <el-table class="player-table" :data="rows" border stripe v-loading="loading" @sort-change="onSortChange" style="width: 100%;">
            <el-table-column label="姓名" min-width="160">
              <template #default="{ row }">
                <div class="player-cell" @click="goToPlayerDetail(row)">
                  <img v-if="row.nbaPlayerId && !brokenAvatars.has(row.id)" :src="`https://cdn.nba.com/headshots/nba/latest/260x190/${row.nbaPlayerId}.png`" class="player-thumb" @error="onThumbError(row)" />
                  <span v-else class="player-thumb player-thumb-fallback">{{ row.name?.charAt(0) }}</span>
                  <span class="player-name-link">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="teamName" label="球队" min-width="90" />
            <el-table-column prop="position" label="位置" min-width="70" align="center" />
            <el-table-column prop="jerseyNumber" label="球衣" min-width="64" align="center" />
            <el-table-column prop="gamesPlayed" label="出场" min-width="68" align="center" sortable="custom" />
            <el-table-column prop="minutesPerGame" label="时间" width="72" align="right" sortable="custom">
              <template #default="{ row }">{{ row.minutesPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="pointsPerGame" label="得分" width="72" align="right" sortable="custom">
              <template #default="{ row }"><span class="stat-highlight">{{ row.pointsPerGame?.toFixed(1) }}</span></template>
            </el-table-column>
            <el-table-column prop="reboundsPerGame" label="篮板" width="72" align="right" sortable="custom">
              <template #default="{ row }">{{ row.reboundsPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="assistsPerGame" label="助攻" width="72" align="right" sortable="custom">
              <template #default="{ row }">{{ row.assistsPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="stealsPerGame" label="抢断" width="72" align="right" sortable="custom">
              <template #default="{ row }">{{ row.stealsPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="blocksPerGame" label="盖帽" width="72" align="right" sortable="custom">
              <template #default="{ row }">{{ row.blocksPerGame?.toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="投篮命中率" width="110" align="right" sortable="custom">
              <template #default="{ row }">{{ (row.fieldGoalPct * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="三分命中率" width="110" align="right" sortable="custom">
              <template #default="{ row }">{{ (row.threePointPct * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="罚球命中率" width="110" align="right" sortable="custom">
              <template #default="{ row }">{{ (row.freeThrowPct * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column prop="efficiency" label="效率值" width="92" align="center" sortable="custom" />
            <el-table-column label="操作" min-width="120" v-if="auth.isAdmin">
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
import { useAuthStore } from '@/stores/auth'
import { createPlayer, deletePlayer, fetchPlayers, updatePlayer } from '@/api/player'
import { fetchTeams } from '@/api/team'
import type { Player, Team } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import { getTeamPrimaryColor } from '@/utils/teamColors'
import AppEmpty from '@/components/common/AppEmpty.vue'
import SportsHeader from '@/components/common/SportsHeader.vue'

function getPosBadgeLabel(pos?: string): string {
  if (!pos) return 'UTIL'
  if (pos.includes('控') || pos === 'PG' || pos.startsWith('PG')) return 'PG 控卫'
  if (pos.includes('分') || pos === 'SG' || pos.startsWith('SG')) return 'SG 分卫'
  if (pos.includes('小前') || pos === 'SF' || pos.startsWith('SF')) return 'SF 小前'
  if (pos.includes('大前') || pos === 'PF' || pos.startsWith('PF')) return 'PF 大前'
  if (pos.includes('中') || pos === 'C' || pos.startsWith('C')) return 'C 中锋'
  if (pos === 'G' || pos.includes('后卫')) return 'G 后卫'
  if (pos === 'F' || pos.includes('前锋')) return 'F 前锋'
  return pos
}

function getPosBadgeClass(pos?: string): string {
  if (!pos) return 'pos-badge--util'
  if (pos.includes('控') || pos === 'PG' || pos.startsWith('PG')) return 'pos-badge--pg'
  if (pos.includes('分') || pos === 'SG' || pos.startsWith('SG')) return 'pos-badge--sg'
  if (pos.includes('小前') || pos === 'SF' || pos.startsWith('SF')) return 'pos-badge--sf'
  if (pos.includes('大前') || pos === 'PF' || pos.startsWith('PF')) return 'pos-badge--pf'
  if (pos.includes('中') || pos === 'C' || pos.startsWith('C')) return 'pos-badge--c'
  if (pos === 'G' || pos.includes('后卫')) return 'pos-badge--sg'
  if (pos === 'F' || pos.includes('前锋')) return 'pos-badge--sf'
  return 'pos-badge--util'
}

const auth = useAuthStore()
const router = useRouter()
const viewMode = ref<'cards' | 'table'>('cards')
const loading = ref(false)
const saving = ref(false)
const rows = ref<Player[]>([])
const brokenAvatars = ref<Set<number>>(new Set())
const teamOptions = ref<Team[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const cardSize = ref(21)
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
    const pageSize = viewMode.value === 'cards' ? cardSize.value : size.value
    const params: any = { page: page.value - 1, size: pageSize }
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
function onThumbError(row: Player) { brokenAvatars.value.add(row.id) }

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
    const [XLSX, { default: saveAs }] = await Promise.all([import('xlsx'), import('file-saver')])
    // 后端分页上限为 100，导出改为逐页拉取，避免 size 超限被 400 拒绝
    const EXPORT_PAGE_SIZE = 100
    const allPlayers: Player[] = []
    let page = 0
    let totalPages = 1
    do {
      const { data } = await fetchPlayers({
        q: keyword.value || undefined,
        teamId: filterTeamId.value || undefined,
        position: filterPosition.value || undefined,
        page,
        size: EXPORT_PAGE_SIZE,
      })
      allPlayers.push(...(data.content || []))
      totalPages = data.totalPages || 1
      page += 1
    } while (page < totalPages)

    const header = ['姓名', '球队', '位置', '得分', '篮板', '助攻', '抢断', '盖帽', '命中率%', '三分%']
    const rows4Excel = allPlayers.map((p: Player) => [p.name, p.teamName, p.position, p.pointsPerGame, p.reboundsPerGame, p.assistsPerGame, p.stealsPerGame, p.blocksPerGame, (p.fieldGoalPct * 100).toFixed(1), (p.threePointPct * 100).toFixed(1)])
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
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  min-height: calc(100vh - 108px);
  animation: pageFadeIn 0.3s ease forwards;
  opacity: 0;
  transform: translateY(8px);
}

.page--table {
  max-width: min(1680px, calc(100vw - 96px));
}

@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-header h1 {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.4px;
}
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
  padding: 8px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  flex: 1;
  max-width: 300px;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s var(--ease-out-quart, ease);
}
.filter-search:focus-within {
  border-color: rgba(34, 211, 238, 0.5);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.1);
  transform: translateY(-1px);
}
.filter-search svg { color: var(--text-muted); flex-shrink: 0; }
.filter-search input { background: none; border: none; outline: none; color: var(--text-primary); font-size: 13px; width: 100%; }
.filter-search input::placeholder { color: var(--text-dim); }

.filter-bar :deep(.el-select__wrapper) {
  min-height: 40px;
  background: var(--bg-input) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: 6px !important;
  box-shadow: none !important;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.filter-bar :deep(.el-select__wrapper:hover) {
  border-color: var(--border-strong) !important;
  background: var(--bg-hover) !important;
}

.filter-bar :deep(.el-select__wrapper.is-focused) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.05) !important;
}

.filter-bar :deep(.el-select__placeholder),
.filter-bar :deep(.el-select__selected-item span) {
  color: var(--text-secondary) !important;
  font-size: 13px;
  font-weight: 500;
}

.filter-bar :deep(.el-select__placeholder.is-transparent) {
  color: var(--text-dim) !important;
}

.filter-bar :deep(.el-select__caret) {
  color: var(--text-muted) !important;
}

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

[data-theme="light"] .filter-bar :deep(.el-select__wrapper) {
  background: var(--bg-input) !important;
  border-color: var(--border-light) !important;
  box-shadow: none !important;
}

[data-theme="light"] .filter-bar :deep(.el-select__wrapper:hover) {
  border-color: var(--border-medium) !important;
  background: var(--bg-input) !important;
}

[data-theme="light"] .filter-bar :deep(.el-select__wrapper.is-focused) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.1) !important;
}

/* 球员卡片网格 */
.player-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.player-table-hint { display: none; }
@media (max-width: 768px) {
  .player-table-hint { display: block; margin: 0 0 8px; color: var(--text-muted); font-size: 12px; }
}
.player-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px 18px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  cursor: pointer;
  transition:
    transform 0.22s var(--ease-out-quart, ease),
    border-color 0.18s ease,
    box-shadow 0.22s var(--ease-out-quart, ease);
  position: relative;
  overflow: hidden;
  min-height: 146px;
}
.player-card:hover {
  border-color: rgba(34, 211, 238, 0.4);
  box-shadow: 0 8px 24px -6px rgba(0, 0, 0, 0.35), 0 0 12px -2px rgba(34, 211, 238, 0.18);
  transform: translateY(-3px);
}
.player-card:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

/* 球队品牌色微细条纹 */
.player-card-stripe {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--team-accent, var(--accent));
  opacity: 0.85;
}

/* 背景半透明球衣号水印 */
.player-jersey-watermark {
  position: absolute;
  right: 12px;
  top: 6px;
  font-family: var(--font-heading);
  font-size: 46px;
  font-weight: 900;
  line-height: 1;
  color: var(--text-primary);
  opacity: 0.045;
  pointer-events: none;
  user-select: none;
  letter-spacing: -2px;
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.player-card:hover .player-jersey-watermark {
  opacity: 0.09;
  transform: scale(1.06) translateX(-2px);
}

/* 顶部球员主信息 */
.player-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.player-card-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-light);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}
.player-card-avatar img { width: 100%; height: 100%; object-fit: cover; }
.player-card-avatar-fallback { font-size: 18px; font-weight: 700; color: var(--text-muted); }

.player-card-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.player-card-badges { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }

/* 位置色彩编码微标签 */
.pos-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: var(--font-mono, monospace);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.4px;
  line-height: 1.4;
  text-transform: uppercase;
}
.pos-badge--pg {
  background: rgba(34, 211, 238, 0.14);
  color: #22D3EE;
  border: 1px solid rgba(34, 211, 238, 0.35);
}
.pos-badge--sg {
  background: rgba(139, 92, 246, 0.14);
  color: #A78BFA;
  border: 1px solid rgba(139, 92, 246, 0.35);
}
.pos-badge--sf {
  background: rgba(16, 185, 129, 0.14);
  color: #34D399;
  border: 1px solid rgba(16, 185, 129, 0.35);
}
.pos-badge--pf {
  background: rgba(245, 158, 11, 0.14);
  color: #FBBF24;
  border: 1px solid rgba(245, 158, 11, 0.35);
}
.pos-badge--c {
  background: rgba(244, 63, 94, 0.14);
  color: #FB7185;
  border: 1px solid rgba(244, 63, 94, 0.35);
}
.pos-badge--util {
  background: var(--bg-hover);
  color: var(--text-muted);
  border: 1px solid var(--border-light);
}

[data-theme="light"] .pos-badge--pg { background: rgba(8, 145, 178, 0.1); color: #0891B2; border-color: rgba(8, 145, 178, 0.28); }
[data-theme="light"] .pos-badge--sg { background: rgba(124, 58, 237, 0.1); color: #7C3AED; border-color: rgba(124, 58, 237, 0.28); }
[data-theme="light"] .pos-badge--sf { background: rgba(5, 150, 105, 0.1); color: #059669; border-color: rgba(5, 150, 105, 0.28); }
[data-theme="light"] .pos-badge--pf { background: rgba(217, 119, 6, 0.1); color: #D97706; border-color: rgba(217, 119, 6, 0.28); }
[data-theme="light"] .pos-badge--c  { background: rgba(225, 29, 72, 0.1); color: #E11D48; border-color: rgba(225, 29, 72, 0.28); }

.jersey-badge {
  font-family: var(--font-heading);
  font-size: 10px;
  font-weight: 700;
  color: var(--text-tertiary);
  padding: 1px 4px;
}

.player-card-name { font-size: 14px; font-weight: 700; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.player-card-meta { display: flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-muted); }
.player-card-team-logo { width: 14px; height: 14px; object-fit: contain; }
.player-card-team-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 三项核心数据对齐底座 */
.player-card-stats-dock {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 6px 8px;
  margin-top: 12px;
}
.player-stat-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  flex: 1;
}
.dock-sep {
  width: 1px;
  height: 18px;
  background: var(--border-light);
}
.stat-val { font-size: 13px; font-weight: 700; color: var(--text-primary); font-family: var(--font-heading); font-variant-numeric: tabular-nums; }
.stat-lbl { font-size: 9px; font-weight: 600; color: var(--text-tertiary); text-transform: uppercase; letter-spacing: 0.3px; }

/* 表格视图 */
.card { background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-lg); padding: 16px; }
.table-card {
  overflow-x: auto;
}
.table-card :deep(.el-table) {
  min-width: 1430px;
}
.table-card :deep(.el-table__inner-wrapper),
.table-card :deep(.el-scrollbar__view) {
  min-width: 1430px;
}
.table-card :deep(.el-table__header .cell) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  overflow: visible;
  text-overflow: clip;
}
.table-card :deep(.caret-wrapper) {
  flex: 0 0 auto;
}
.card :deep(.el-table__header th),
.card :deep(.el-table__header .cell),
.card :deep(.el-table__body .cell) { white-space: nowrap; }
.player-cell { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.player-thumb { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; background: var(--bg-hover); flex-shrink: 0; }
.player-thumb-fallback { display: inline-flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; color: var(--text-muted); }
.player-name-link { color: var(--accent); cursor: pointer; font-weight: 500; }
.player-name-link:hover { color: var(--accent-light); }
.stat-highlight { font-weight: 700; color: var(--accent); }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }

/* 移动端 */
@media (max-width: 768px) {
  .page,
  .page--table { max-width: 100%; padding: 0 16px; }
  .player-grid { grid-template-columns: repeat(2, 1fr); }
  .player-card-stats-dock { padding: 4px 6px; }
  .stat-val { font-size: 12px; }
  .stat-lbl { font-size: 8px; }
  .filter-search { max-width: 100%; }
}
@media (max-width: 480px) {
  .player-grid { grid-template-columns: 1fr; }
}
</style>
