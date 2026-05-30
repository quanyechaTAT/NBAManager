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
        <p>维护球员所属球队、位置与基础场均数据。</p>
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
      <el-table-column prop="name" label="姓名" min-width="120" />
      <el-table-column prop="teamName" label="球队" width="120" />
      <el-table-column prop="position" label="位置" width="80" />
      <el-table-column prop="pointsPerGame" label="场均得分" width="100" />
      <el-table-column prop="reboundsPerGame" label="场均篮板" width="100" />
      <el-table-column prop="assistsPerGame" label="场均助攻" width="100" />
      <el-table-column prop="stealsPerGame" label="场均抢断" width="100" />
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑球员' : '新建球员'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="球队">
          <el-select v-model="form.teamId" filterable style="width: 100%" :loading="teamsLoading">
            <el-option v-for="t in teamOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置">
          <el-select v-model="form.position" style="width: 100%">
            <el-option v-for="p in positionOptions" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="场均得分">
          <el-input-number v-model="form.pointsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="场均篮板">
          <el-input-number v-model="form.reboundsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="场均助攻">
          <el-input-number v-model="form.assistsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="场均抢断">
          <el-input-number v-model="form.stealsPerGame" :min="0" :step="0.1" :precision="1" style="width: 100%" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { useAuthStore } from '@/stores/auth'
import { createPlayer, deletePlayer, fetchPlayers, updatePlayer } from '@/api/player'
import { fetchTeams } from '@/api/team'
import type { Player, Team } from '@/api/types'

const auth = useAuthStore()
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
    // 请求当前筛选条件下的全部数据（不分页）
    const { data } = await fetchPlayers({
      q: keyword.value || undefined,
      teamId: filterTeamId.value || undefined,
      position: filterPosition.value || undefined,
      page: 0,
      size: 9999,
    })

    const header = ['姓名', '球队', '位置', '场均得分', '场均篮板', '场均助攻', '场均抢断']
    const rows4Excel = data.content.map((p: Player) => [
      p.name,
      p.teamName,
      p.position,
      p.pointsPerGame,
      p.reboundsPerGame,
      p.assistsPerGame,
      p.stealsPerGame,
    ])

    const ws = XLSX.utils.aoa_to_sheet([header, ...rows4Excel])

    // 设置列宽
    ws['!cols'] = [
      { wch: 16 }, { wch: 14 }, { wch: 8 },
      { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 },
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
  max-width: 1100px;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  z-index: 1;
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

/* ---- 筛选下拉框 ---- */
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
</style>
