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
    <div class="section-head">
      <div>
        <h2>球员资料</h2>
        <p>维护球员所属球队、位置与详细场均数据。</p>
      </div>
      <div class="head-actions">
        <el-button type="success" plain @click="exportExcel">📥 导出 Excel</el-button>
        <el-button v-if="auth.isAdmin" type="primary" @click="openCreate">新建球员</el-button>
      </div>
    </div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索球员姓名" clearable style="width: 200px" @keyup.enter="load" />
      <el-select v-model="filterTeamId" clearable placeholder="筛选球队" style="width: 160px" @change="onFilterChange">
        <el-option v-for="t in teamOptions" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-select v-model="filterPosition" clearable placeholder="筛选位置" style="width: 140px" @change="onFilterChange">
        <el-option v-for="p in positionOptions" :key="p" :label="p" :value="p" />
      </el-select>
      <el-button type="primary" plain @click="load">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>
    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column label="姓名" min-width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="goToPlayerDetail(row)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="teamName" label="球队" width="100" />
      <el-table-column prop="position" label="位置" width="70" />
      <el-table-column prop="jerseyNumber" label="球衣" width="60" align="center" />
      <el-table-column prop="pointsPerGame" label="得分" width="70" align="center" sortable />
      <el-table-column prop="reboundsPerGame" label="篮板" width="70" align="center" sortable />
      <el-table-column prop="assistsPerGame" label="助攻" width="70" align="center" sortable />
      <el-table-column prop="stealsPerGame" label="抢断" width="65" align="center" sortable />
      <el-table-column prop="blocksPerGame" label="盖帽" width="65" align="center" sortable />
      <el-table-column label="命中率" width="90" align="center">
        <template #default="{ row }">
          {{ (row.fieldGoalPct * 100).toFixed(1) }}%
        </template>
      </el-table-column>
      <el-table-column label="三分" width="80" align="center">
        <template #default="{ row }">
          {{ (row.threePointPct * 100).toFixed(1) }}%
        </template>
      </el-table-column>
      <el-table-column prop="efficiency" label="效率" width="65" align="center" sortable />
      <el-table-column v-if="auth.isAdmin" label="操作" width="160" fixed="right">
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
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑球员' : '新建球员'" width="680px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-divider content-position="left">基本信息</el-divider>
        <div class="form-row">
          <el-form-item label="姓名" class="form-item-full">
            <el-input v-model="form.name" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="球队" class="form-item-half">
            <el-select v-model="form.teamId" filterable style="width: 100%" :loading="teamsLoading">
              <el-option v-for="t in teamOptions" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="位置" class="form-item-half">
            <el-select v-model="form.position" style="width: 100%">
              <el-option v-for="p in positionOptions" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="球衣号码" class="form-item-half">
            <el-input-number v-model="form.jerseyNumber" :min="0" :max="99" style="width: 100%" />
          </el-form-item>
          <el-form-item label="国籍" class="form-item-half">
            <el-input v-model="form.country" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="身高" class="form-item-half">
            <el-input v-model="form.height" placeholder="如 6-8" />
          </el-form-item>
          <el-form-item label="体重(磅)" class="form-item-half">
            <el-input-number v-model="form.weight" :min="0" :max="400" style="width: 100%" />
          </el-form-item>
        </div>

        <el-divider content-position="left">场均数据</el-divider>
        <div class="form-row">
          <el-form-item label="得分" class="form-item-third">
            <el-input-number v-model="form.pointsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="篮板" class="form-item-third">
            <el-input-number v-model="form.reboundsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="助攻" class="form-item-third">
            <el-input-number v-model="form.assistsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="抢断" class="form-item-third">
            <el-input-number v-model="form.stealsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="盖帽" class="form-item-third">
            <el-input-number v-model="form.blocksPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="失误" class="form-item-third">
            <el-input-number v-model="form.turnoversPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="出场次数" class="form-item-third">
            <el-input-number v-model="form.gamesPlayed" :min="0" :max="82" style="width: 100%" />
          </el-form-item>
          <el-form-item label="上场时间" class="form-item-third">
            <el-input-number v-model="form.minutesPerGame" :min="0" :max="48" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="效率值" class="form-item-third">
            <el-input-number v-model="form.efficiency" :min="0" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
        </div>

        <el-divider content-position="left">命中率</el-divider>
        <div class="form-row">
          <el-form-item label="投篮%" class="form-item-third">
            <el-input-number v-model="form.fieldGoalPct" :min="0" :max="1" :step="0.001" :precision="3" style="width: 100%" />
          </el-form-item>
          <el-form-item label="三分%" class="form-item-third">
            <el-input-number v-model="form.threePointPct" :min="0" :max="1" :step="0.001" :precision="3" style="width: 100%" />
          </el-form-item>
          <el-form-item label="罚球%" class="form-item-third">
            <el-input-number v-model="form.freeThrowPct" :min="0" :max="1" :step="0.001" :precision="3" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="真实命中率" class="form-item-half">
            <el-input-number v-model="form.trueShootingPct" :min="0" :max="1" :step="0.001" :precision="3" style="width: 100%" />
          </el-form-item>
          <el-form-item label="使用率" class="form-item-half">
            <el-input-number v-model="form.usagePct" :min="0" :max="100" :step="0.1" :precision="1" style="width: 100%" />
          </el-form-item>
        </div>
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
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { useAuthStore } from '@/stores/auth'
import { createPlayer, deletePlayer, fetchPlayers, updatePlayer } from '@/api/player'
import { fetchTeams } from '@/api/team'
import type { Player, Team } from '@/api/types'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const teamsLoading = ref(false)
const rows = ref<Player[]>([])
const teamOptions = ref<Team[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterTeamId = ref<number | undefined>(undefined)
const filterPosition = ref<string | undefined>(undefined)
const positionOptions = ['控卫', '分卫', '小前锋', '大前锋', '中锋']

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  teamId: 0 as number,
  position: '控卫',
  pointsPerGame: 15,
  reboundsPerGame: 4,
  assistsPerGame: 3,
  stealsPerGame: 1.0,
  gamesPlayed: 65,
  minutesPerGame: 28.0,
  fieldGoalPct: 0.460,
  threePointPct: 0.350,
  freeThrowPct: 0.800,
  blocksPerGame: 0.5,
  turnoversPerGame: 1.5,
  efficiency: 15.0,
  trueShootingPct: 0.570,
  usagePct: 20.0,
  jerseyNumber: 0,
  height: '6-6',
  weight: 210,
  country: '美国',
})

async function loadTeams() {
  teamsLoading.value = true
  try {
    const { data } = await fetchTeams({ page: 0, size: 100 })
    teamOptions.value = data.content
    if (!form.teamId && data.content.length) {
      form.teamId = data.content[0].id
    }
  } catch {
    ElMessage.error('加载球队列表失败')
  } finally {
    teamsLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await fetchPlayers({
      q: keyword.value || undefined,
      teamId: filterTeamId.value || undefined,
      position: filterPosition.value || undefined,
      page: page.value - 1,
      size: size.value,
    })
    rows.value = data.content
    total.value = data.totalElements
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

function resetFilters() {
  keyword.value = ''
  filterTeamId.value = undefined
  filterPosition.value = undefined
  page.value = 1
  load()
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.position = '控卫'
  form.pointsPerGame = 15
  form.reboundsPerGame = 4
  form.assistsPerGame = 3
  form.stealsPerGame = 1.0
  form.gamesPlayed = 65
  form.minutesPerGame = 28.0
  form.fieldGoalPct = 0.460
  form.threePointPct = 0.350
  form.freeThrowPct = 0.800
  form.blocksPerGame = 0.5
  form.turnoversPerGame = 1.5
  form.efficiency = 15.0
  form.trueShootingPct = 0.570
  form.usagePct = 20.0
  form.jerseyNumber = 0
  form.height = '6-6'
  form.weight = 210
  form.country = '美国'
  if (teamOptions.value.length) {
    form.teamId = teamOptions.value[0].id
  }
  dialogVisible.value = true
}

function openEdit(row: Player) {
  editingId.value = row.id
  form.name = row.name
  form.teamId = row.teamId
  form.position = row.position
  form.pointsPerGame = row.pointsPerGame
  form.reboundsPerGame = row.reboundsPerGame
  form.assistsPerGame = row.assistsPerGame
  form.stealsPerGame = row.stealsPerGame
  form.gamesPlayed = row.gamesPlayed
  form.minutesPerGame = row.minutesPerGame
  form.fieldGoalPct = row.fieldGoalPct
  form.threePointPct = row.threePointPct
  form.freeThrowPct = row.freeThrowPct
  form.blocksPerGame = row.blocksPerGame
  form.turnoversPerGame = row.turnoversPerGame
  form.efficiency = row.efficiency
  form.trueShootingPct = row.trueShootingPct
  form.usagePct = row.usagePct
  form.jerseyNumber = row.jerseyNumber
  form.height = row.height
  form.weight = row.weight
  form.country = row.country
  dialogVisible.value = true
}

async function save() {
  if (!form.teamId) {
    ElMessage.warning('请选择球队')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      teamId: form.teamId,
      position: form.position,
      pointsPerGame: form.pointsPerGame,
      reboundsPerGame: form.reboundsPerGame,
      assistsPerGame: form.assistsPerGame,
      stealsPerGame: form.stealsPerGame,
      gamesPlayed: form.gamesPlayed,
      minutesPerGame: form.minutesPerGame,
      fieldGoalPct: form.fieldGoalPct,
      threePointPct: form.threePointPct,
      freeThrowPct: form.freeThrowPct,
      blocksPerGame: form.blocksPerGame,
      turnoversPerGame: form.turnoversPerGame,
      efficiency: form.efficiency,
      trueShootingPct: form.trueShootingPct,
      usagePct: form.usagePct,
      jerseyNumber: form.jerseyNumber,
      height: form.height,
      weight: form.weight,
      country: form.country,
    }
    if (editingId.value) {
      await updatePlayer(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createPlayer(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
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

async function onDelete(row: Player) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '提示', { type: 'warning' })
    await deletePlayer(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    if (e === 'cancel') return
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '删除失败')
  }
}

/* -------- 导出 Excel -------- */
async function exportExcel() {
  ElMessage.info('正在导出…')
  try {
    const { data } = await fetchPlayers({
      q: keyword.value || undefined,
      teamId: filterTeamId.value || undefined,
      position: filterPosition.value || undefined,
      page: 0,
      size: 9999,
    })

    const header = ['姓名', '球队', '位置', '球衣', '身高', '体重', '国籍',
      '场均得分', '场均篮板', '场均助攻', '场均抢断', '场均盖帽', '场均失误',
      '出场次数', '场均时间', '投篮%', '三分%', '罚球%', '真实命中率%', '效率值', '使用率%']
    const rows4Excel = data.content.map((p: Player) => [
      p.name, p.teamName, p.position, p.jerseyNumber, p.height, p.weight, p.country,
      p.pointsPerGame, p.reboundsPerGame, p.assistsPerGame, p.stealsPerGame,
      p.blocksPerGame, p.turnoversPerGame, p.gamesPlayed, p.minutesPerGame,
      (p.fieldGoalPct * 100).toFixed(1), (p.threePointPct * 100).toFixed(1),
      (p.freeThrowPct * 100).toFixed(1), (p.trueShootingPct * 100).toFixed(1),
      p.efficiency, (p.usagePct).toFixed(1),
    ])

    const ws = XLSX.utils.aoa_to_sheet([header, ...rows4Excel])
    ws['!cols'] = [
      { wch: 16 }, { wch: 10 }, { wch: 8 }, { wch: 6 }, { wch: 6 }, { wch: 6 }, { wch: 10 },
      { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 },
      { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 10 }, { wch: 8 }, { wch: 8 },
    ]

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '球员数据')

    const buf = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
    const blob = new Blob([buf], { type: 'application/octet-stream' })

    const now = new Date()
    const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
    saveAs(blob, `球员数据_${dateStr}.xlsx`)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

function goToPlayerDetail(row: Player) {
  router.push({ path: '/players/detail', query: { id: String(row.id) } })
}

onMounted(async () => {
  await loadTeams()
  await load()
})
</script>

<style scoped>
.page-wrapper {
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  padding: 0;
}
.head-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
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
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.card {
  max-width: 1300px;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  z-index: 1;
}
.form-row {
  display: flex;
  gap: 16px;
}
.form-item-full {
  flex: 1;
}
.form-item-half {
  flex: 1;
  min-width: 0;
}
.form-item-third {
  flex: 1;
  min-width: 0;
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
  border-color: var(--purple) !important;
  box-shadow: 0 0 0 2px var(--purple-glow) !important;
}
:deep(.el-input__inner) { color: var(--text-primary) !important; font-family: var(--font-body); }
:deep(.el-input__inner::placeholder) { color: var(--text-dim) !important; }

:deep(.el-select .el-select__wrapper) {
  background: #1C2333 !important;
  border: 1px solid var(--border-light) !important;
  box-shadow: none !important;
  border-radius: var(--radius-sm) !important;
}
:deep(.el-select .el-select__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-select .el-select__wrapper.is-focused) {
  border-color: var(--purple) !important;
  box-shadow: 0 0 0 2px var(--purple-glow) !important;
}
:deep(.el-select .el-select__placeholder),
:deep(.el-select .el-select__selected-item span) {
  color: var(--text-primary) !important;
}
:deep(.el-select .el-select__placeholder.is-transparent) {
  color: var(--text-dim) !important;
}
:deep(.el-select .el-select__suffix),
:deep(.el-select .el-select__caret) {
  color: var(--text-muted) !important;
}
:deep(.el-select .el-tag) {
  background: var(--purple-dim) !important;
  border-color: rgba(108, 92, 231, 0.25) !important;
  color: var(--purple-light) !important;
}
:deep(.el-divider__text) {
  background: var(--bg-card) !important;
  color: var(--text-secondary) !important;
  font-size: 13px;
}
:deep(.el-divider) {
  border-color: var(--border-light) !important;
}
</style>
