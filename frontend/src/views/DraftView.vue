<template>
  <div class="page-wrapper animated-bg">
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <div class="bg-grid"></div>

    <div class="page-inner">
      <el-card shadow="never" class="card slide-up-enter">
        <div class="section-head">
          <div>
            <h2>选秀数据库</h2>
            <p>浏览和管理 NBA 历年选秀数据记录。</p>
          </div>
          <div class="head-actions">
            <SyncButton v-if="auth.isAdmin" module="draft" label="同步选秀" @sync-success="load" />
            <el-button v-if="auth.isAdmin" type="primary" @click="openCreate">新建选秀记录</el-button>
          </div>
        </div>

        <!-- 统计卡片 -->
        <div class="stats-row stagger-in">
          <div class="mini-stat">
            <span class="mini-stat-value">{{ totalPicks }}</span>
            <span class="mini-stat-label">总选秀数</span>
          </div>
          <div class="mini-stat">
            <span class="mini-stat-value">{{ round1Count }}</span>
            <span class="mini-stat-label">第一轮</span>
          </div>
          <div class="mini-stat">
            <span class="mini-stat-value">{{ round2Count }}</span>
            <span class="mini-stat-label">第二轮</span>
          </div>
          <div class="mini-stat">
            <span class="mini-stat-value">{{ topPickTeam }}</span>
            <span class="mini-stat-label">最多选秀球队</span>
          </div>
        </div>

        <!-- 年份选择器 -->
        <div class="toolbar">
          <span class="toolbar-label">选秀年份</span>
          <el-select v-model="selectedYear" style="width: 140px" @change="load">
            <el-option v-for="y in yearOptions" :key="y" :label="y + ' 年'" :value="y" />
          </el-select>
          <el-button type="primary" plain @click="load">刷新</el-button>
        </div>

        <!-- 第一轮 -->
        <div class="round-section" v-if="round1.length > 0">
          <div class="round-header">
            <span class="round-badge round-1">R1</span>
            <span class="round-title">第一轮选秀</span>
            <span class="round-count">{{ round1.length }} 人</span>
          </div>
          <el-table :data="round1" border stripe v-loading="loading" class="draft-table">
            <el-table-column label="顺位" width="80" align="center">
              <template #default="{ row }">
                <span class="pick-number" :class="{ 'lottery-pick': row.pickNumber <= 14 }">{{ row.pickNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="playerName" label="球员" min-width="160">
              <template #default="{ row }">
                <span class="player-name-cell">{{ row.playerName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="teamName" label="选中球队" min-width="140" />
            <el-table-column prop="notes" label="备注" min-width="160">
              <template #default="{ row }">
                <span class="text-muted">{{ row.notes || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="auth.isAdmin" label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 第二轮 -->
        <div class="round-section" v-if="round2.length > 0" style="margin-top: 24px;">
          <div class="round-header">
            <span class="round-badge round-2">R2</span>
            <span class="round-title">第二轮选秀</span>
            <span class="round-count">{{ round2.length }} 人</span>
          </div>
          <el-table :data="round2" border stripe class="draft-table">
            <el-table-column label="顺位" width="80" align="center">
              <template #default="{ row }">
                <span class="pick-number">{{ row.pickNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="playerName" label="球员" min-width="160">
              <template #default="{ row }">
                <span class="player-name-cell">{{ row.playerName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="teamName" label="选中球队" min-width="140" />
            <el-table-column prop="notes" label="备注" min-width="160">
              <template #default="{ row }">
                <span class="text-muted">{{ row.notes || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="auth.isAdmin" label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-empty v-if="!loading && rows.length === 0" description="暂无选秀数据" />
      </el-card>

      <!-- 新建/编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="editingId ? '编辑选秀记录' : '新建选秀记录'" width="580px" destroy-on-close class="dialog-light">
        <el-form :model="form" label-width="90px">
          <div class="form-row">
            <el-form-item label="年份" class="form-item-half">
              <el-input-number v-model="form.year" :min="2020" :max="2026" style="width: 100%" />
            </el-form-item>
            <el-form-item label="轮次" class="form-item-half">
              <el-select v-model="form.round" style="width: 100%">
                <el-option label="第一轮" :value="1" />
                <el-option label="第二轮" :value="2" />
              </el-select>
            </el-form-item>
          </div>
          <div class="form-row">
            <el-form-item label="顺位" class="form-item-half">
              <el-input-number v-model="form.pickNumber" :min="1" :max="60" style="width: 100%" />
            </el-form-item>
            <el-form-item label="选中球队" class="form-item-half">
              <el-input v-model="form.teamName" placeholder="选中球队" />
            </el-form-item>
          </div>
          <el-form-item label="球员姓名">
            <el-input v-model="form.playerName" placeholder="球员全名" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="备注信息" />
          </el-form-item>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchDraftPicks, createDraftPick, updateDraftPick, deleteDraftPick } from '@/api/draft'
import type { DraftPick } from '@/api/types'
import SyncButton from '@/components/common/SyncButton.vue'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<DraftPick[]>([])
const selectedYear = ref(new Date().getFullYear())
const positionOptions = ['控卫', '分卫', '小前锋', '大前锋', '中锋']

const yearOptions = computed(() => {
  const years: number[] = []
  for (let y = 2026; y >= 2020; y--) years.push(y)
  return years
})

const round1 = computed(() => rows.value.filter(r => r.round === 1).sort((a, b) => a.pickNumber - b.pickNumber))
const round2 = computed(() => rows.value.filter(r => r.round === 2).sort((a, b) => a.pickNumber - b.pickNumber))
const totalPicks = computed(() => rows.value.length)
const round1Count = computed(() => round1.value.length)
const round2Count = computed(() => round2.value.length)
const topPickTeam = computed(() => {
  const counts: Record<string, number> = {}
  rows.value.forEach(r => {
    counts[r.teamName] = (counts[r.teamName] || 0) + 1
  })
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1])
  return sorted.length > 0 ? sorted[0][0] : '—'
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  year: new Date().getFullYear(),
  round: 1,
  pickNumber: 1,
  teamName: '',
  playerName: '',
  notes: '',
})

async function load() {
  loading.value = true
  try {
    const { data } = await fetchDraftPicks({ year: selectedYear.value })
    rows.value = data
  } catch {
    ElMessage.error('加载选秀数据失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.year = selectedYear.value
  form.round = 1
  form.pickNumber = 1
  form.teamName = ''
  form.playerName = ''
  form.notes = ''
  dialogVisible.value = true
}

function openEdit(row: DraftPick) {
  editingId.value = row.id
  form.year = row.year
  form.round = row.round
  form.pickNumber = row.pickNumber
  form.teamName = row.teamName
  form.playerName = row.playerName
  form.notes = row.notes
  dialogVisible.value = true
}

async function save() {
  if (!form.playerName || !form.teamName) {
    ElMessage.warning('请填写球员姓名和选中球队')
    return
  }
  saving.value = true
  try {
    const payload = {
      year: form.year,
      round: form.round,
      pickNumber: form.pickNumber,
      teamName: form.teamName,
      playerName: form.playerName,
      notes: form.notes,
    }
    if (editingId.value) {
      await updateDraftPick(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createDraftPick(payload)
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

async function onDelete(row: DraftPick) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.playerName}」的选秀记录？`, '提示', { type: 'warning' })
    await deleteDraftPick(row.id)
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

onMounted(() => load())
</script>

<style scoped>
.page-wrapper {
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  padding: 0;
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
.head-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
}
.toolbar-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
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
.card {
  max-width: 1300px;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  z-index: 1;
  overflow: hidden;
}
.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--purple), var(--accent), var(--cyan));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}

/* Stats */
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
.mini-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  transition: all var(--duration-fast) var(--ease-smooth);
}
.mini-stat:hover {
  border-color: var(--purple);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--purple-glow);
}
.mini-stat-value {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mini-stat-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

/* Round section */
.round-section {
  margin-bottom: 8px;
}
.round-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 8px 0;
}
.round-badge {
  font-family: var(--font-heading);
  font-size: 11px;
  font-weight: 800;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  letter-spacing: 1px;
}
.round-1 {
  background: linear-gradient(135deg, var(--purple), var(--purple-light));
  color: #fff;
}
.round-2 {
  background: var(--bg-hover);
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
}
.round-title {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}
.round-count {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: auto;
}

/* Pick number styling */
.pick-number {
  font-family: var(--font-heading);
  font-weight: 700;
  color: var(--text-secondary);
}
.lottery-pick {
  color: var(--accent);
  font-weight: 800;
}
.player-name-cell {
  font-weight: 600;
  color: var(--text-primary);
}
.text-muted {
  color: var(--text-muted);
}

/* Form */
.form-row {
  display: flex;
  gap: 16px;
}
.form-item-half {
  flex: 1;
  min-width: 0;
}

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

:deep(.el-select .el-select__wrapper) {
  background: var(--bg-input) !important;
  border: 1.5px solid var(--border-light) !important;
  box-shadow: none !important;
  border-radius: 8px !important;
}
:deep(.el-select .el-select__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-select .el-select__wrapper.is-focused) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px var(--accent-glow) !important;
}
:deep(.el-select .el-select__placeholder),
:deep(.el-select .el-select__selected-item span) {
  color: var(--text-primary) !important;
}
:deep(.el-select .el-select__placeholder.is-transparent) {
  color: var(--text-dim) !important;
}
</style>
