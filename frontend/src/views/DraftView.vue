<template>
  <div class="page-wrapper">
    <div class="page-inner">
      <div class="draft-content">
        <SportsHeader
          eyebrow="NBA DRAFT CLASS ARCHIVE"
          title="历届选秀大会"
          subtitle="查阅历届选秀顺位、乐透区新秀与球员选秀记录"
        />

        <!-- 紧凑统计摘要 -->
        <div class="stats-inline">
          <span class="stat"><strong>{{ totalPicks }}</strong>本届新秀总数</span>
          <span class="stat"><strong>{{ round1Count }}</strong>首轮新秀 (R1)</span>
          <span class="stat"><strong>{{ round2Count }}</strong>次轮新秀 (R2)</span>
          <span class="stat"><strong>{{ topPickTeam }}</strong>选秀权最多球队</span>
        </div>

        <!-- 统一工具栏 -->
        <div class="toolbar">
          <span class="toolbar-label">选秀年份</span>
          <el-select v-model="selectedYear" style="width: 140px" @change="load">
            <el-option v-for="y in yearOptions" :key="y" :label="y + ' 年'" :value="y" />
          </el-select>
          <el-button plain @click="load">刷新</el-button>
          <span class="toolbar-spacer" />
          <SyncButton v-if="auth.isAdmin" module="draft" label="同步选秀" @sync-success="load" />
          <el-button v-if="auth.isAdmin" type="primary" @click="openCreate">新建选秀记录</el-button>
        </div>

        <!-- 第一轮 -->
        <div class="round-section" v-if="round1.length > 0">
          <div class="round-header">
            <span class="round-badge round-1">R1</span>
            <span class="round-title">第一轮选秀</span>
            <span class="round-count">{{ round1.length }} 人</span>
          </div>
          <el-table :data="round1" stripe v-loading="loading" class="draft-table">
            <el-table-column label="顺位" width="115" align="center">
              <template #default="{ row }">
                <span v-if="row.pickNumber === 1" class="pick-badge pick-top1">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12" class="pick-icon"><path d="M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5m14 3c0 .6-.4 1-1 1H6c-.6 0-1-.4-1-1v-1h14v1z"/></svg>
                  #1 状元
                </span>
                <span v-else-if="row.pickNumber === 2" class="pick-badge pick-top2">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12" class="pick-icon"><path d="M12 2L4 5v6.09c0 5.05 3.41 9.76 8 10.91 4.59-1.15 8-5.86 8-10.91V5l-8-3z"/></svg>
                  #2 榜眼
                </span>
                <span v-else-if="row.pickNumber === 3" class="pick-badge pick-top3">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12" class="pick-icon"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>
                  #3 探花
                </span>
                <span v-else-if="row.pickNumber <= 14" class="pick-badge pick-lottery">#{{ row.pickNumber }} 乐透</span>
                <span v-else class="pick-number">#{{ row.pickNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="playerName" label="球员" min-width="160">
              <template #default="{ row }">
                <div class="player-name-cell" :class="{ 'clickable': row.nbaPlayerId }" @click="row.nbaPlayerId && goPlayerDetail(row.nbaPlayerId)">
                  <div class="player-avatar-sm">
                    <img v-if="row.nbaPlayerId" :src="getHeadshotUrl(row.nbaPlayerId)" :alt="row.playerName" class="headshot-sm" @error="onHeadshotError" loading="lazy" />
                    <span class="player-avatar-placeholder" :style="row.nbaPlayerId ? { display: 'none' } : undefined">{{ row.playerName?.charAt(0) }}</span>
                  </div>
                  <div>
                    <span class="player-name-cn">{{ row.playerName }}</span>
                    <span v-if="row.playerNameEn" class="player-name-en">{{ row.playerNameEn }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="选中球队" min-width="150">
              <template #default="{ row }">
                <div class="pick-team-cell">
                  <img v-if="getTeamLogo(row.teamName)" :src="getTeamLogo(row.teamName)" class="pick-team-logo" alt="" />
                  <span>{{ row.teamName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="notes" label="备注" min-width="160">
              <template #default="{ row }">
                <span class="text-muted">{{ displayNotes(row.notes) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="auth.isAdmin" label="操作" width="110" fixed="right" align="center">
              <template #default="{ row }">
                <el-tooltip content="编辑" placement="top">
                  <button class="row-action-btn" type="button" aria-label="编辑选秀记录" @click="openEdit(row)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
                  </button>
                </el-tooltip>
                <el-dropdown trigger="click" @command="(cmd: string | number | object) => cmd === 'delete' && onDelete(row)">
                  <button class="row-action-btn" type="button" aria-label="更多操作">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15"><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="delete">
                        <span class="dropdown-danger">删除记录</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
          <el-table :data="round2" stripe class="draft-table">
            <el-table-column label="顺位" width="80" align="center">
              <template #default="{ row }">
                <span class="pick-number">#{{ row.pickNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="playerName" label="球员" min-width="160">
              <template #default="{ row }">
                <div class="player-name-cell" :class="{ 'clickable': row.nbaPlayerId }" @click="row.nbaPlayerId && goPlayerDetail(row.nbaPlayerId)">
                  <div class="player-avatar-sm">
                    <img v-if="row.nbaPlayerId" :src="getHeadshotUrl(row.nbaPlayerId)" :alt="row.playerName" class="headshot-sm" @error="onHeadshotError" loading="lazy" />
                    <span class="player-avatar-placeholder" :style="row.nbaPlayerId ? { display: 'none' } : undefined">{{ row.playerName?.charAt(0) }}</span>
                  </div>
                  <div>
                    <span class="player-name-cn">{{ row.playerName }}</span>
                    <span v-if="row.playerNameEn" class="player-name-en">{{ row.playerNameEn }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="选中球队" min-width="150">
              <template #default="{ row }">
                <div class="pick-team-cell">
                  <img v-if="getTeamLogo(row.teamName)" :src="getTeamLogo(row.teamName)" class="pick-team-logo" alt="" />
                  <span>{{ row.teamName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="notes" label="备注" min-width="160">
              <template #default="{ row }">
                <span class="text-muted">{{ displayNotes(row.notes) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="auth.isAdmin" label="操作" width="110" fixed="right" align="center">
              <template #default="{ row }">
                <el-tooltip content="编辑" placement="top">
                  <button class="row-action-btn" type="button" aria-label="编辑选秀记录" @click="openEdit(row)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
                  </button>
                </el-tooltip>
                <el-dropdown trigger="click" @command="(cmd: string | number | object) => cmd === 'delete' && onDelete(row)">
                  <button class="row-action-btn" type="button" aria-label="更多操作">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15"><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="delete">
                        <span class="dropdown-danger">删除记录</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 手机端：顺位、球员、球队优先，避免横向表格遮住关键信息。 -->
        <div class="draft-mobile-list">
          <section v-for="round in mobileRounds" :key="round.number" v-show="round.picks.length" class="draft-mobile-round">
            <div class="round-header">
              <span class="round-badge" :class="round.number === 1 ? 'round-1' : 'round-2'">R{{ round.number }}</span>
              <span class="round-title">{{ round.label }}</span>
              <span class="round-count">{{ round.picks.length }} 人</span>
            </div>
            <article v-for="row in round.picks" :key="row.id" class="draft-pick-card">
              <div class="draft-pick-card__top">
                <div class="draft-pick-card__number">
                  <span v-if="row.pickNumber === 1" class="pick-badge pick-top1">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12" class="pick-icon"><path d="M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5m14 3c0 .6-.4 1-1 1H6c-.6 0-1-.4-1-1v-1h14v1z"/></svg>
                    状元秀
                  </span>
                  <span v-else-if="row.pickNumber === 2" class="pick-badge pick-top2">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12" class="pick-icon"><path d="M12 2L4 5v6.09c0 5.05 3.41 9.76 8 10.91 4.59-1.15 8-5.86 8-10.91V5l-8-3z"/></svg>
                    榜眼秀
                  </span>
                  <span v-else-if="row.pickNumber === 3" class="pick-badge pick-top3">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12" class="pick-icon"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>
                    探花秀
                  </span>
                  <span v-else-if="row.pickNumber <= 14" class="pick-badge pick-lottery">
                    乐透秀 #{{ row.pickNumber }}
                  </span>
                  <span v-else>第 {{ row.pickNumber }} 顺位</span>
                </div>
                <span class="draft-pick-card__team">
                  <img v-if="getTeamLogo(row.teamName)" :src="getTeamLogo(row.teamName)" alt="" />
                  <span class="draft-pick-team-name">{{ row.teamName }}</span>
                </span>
              </div>
              <div class="draft-pick-card__player">
                <span class="player-avatar-sm">
                  <img v-if="row.nbaPlayerId" :src="getHeadshotUrl(row.nbaPlayerId)" :alt="row.playerName" class="headshot-sm" loading="lazy" @error="onHeadshotError" />
                  <span class="player-avatar-placeholder" :style="row.nbaPlayerId ? { display: 'none' } : undefined">{{ row.playerName?.charAt(0) }}</span>
                </span>
                <span class="draft-pick-card__name">
                  <button v-if="row.nbaPlayerId" type="button" @click="goPlayerDetail(row.nbaPlayerId)">{{ row.playerName }}</button>
                  <strong v-else>{{ row.playerName }}</strong>
                  <small v-if="row.playerNameEn">{{ row.playerNameEn }}</small>
                </span>
              </div>
              <p v-if="displayNotes(row.notes) !== '—'" class="draft-pick-card__note">{{ displayNotes(row.notes) }}</p>
              <div v-if="auth.isAdmin" class="draft-pick-card__actions">
                <button type="button" @click="openEdit(row)">编辑</button>
                <button type="button" class="is-danger" @click="onDelete(row)">删除</button>
              </div>
            </article>
          </section>
        </div>

        <AppEmpty v-if="!loading && rows.length === 0" description="暂无选秀数据" />
      </div>

      <!-- 新建/编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="editingId ? '编辑选秀记录' : '新建选秀记录'" width="580px" destroy-on-close class="dialog-light" :append-to-body="true" :center="true">
        <el-form :model="form" label-width="90px">
          <div class="form-row">
            <el-form-item label="年份" class="form-item-half">
              <el-input-number v-model="form.year" :min="currentYear - 4" :max="currentYear" style="width: 100%" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchDraftPicks, createDraftPick, updateDraftPick, deleteDraftPick } from '@/api/draft'
import AppEmpty from '@/components/common/AppEmpty.vue'
import SportsHeader from '@/components/common/SportsHeader.vue'
import type { DraftPick } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'
import SyncButton from '@/components/common/SyncButton.vue'

/** 备注展示：与顺位机械重复的默认文案归一为 — */
function displayNotes(notes: string | null | undefined): string {
  if (!notes) return '—'
  if (/^选秀顺位第\d+位$/.test(notes.trim())) return '—'
  return notes
}

const router = useRouter()

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<DraftPick[]>([])
const selectedYear = ref(new Date().getFullYear())
const currentYear = new Date().getFullYear()
const yearOptions = computed(() => {
  const years: number[] = []
  for (let y = currentYear; y >= currentYear - 4; y--) years.push(y)
  return years
})

const round1 = computed(() => rows.value.filter(r => r.round === 1).sort((a, b) => a.pickNumber - b.pickNumber))
const round2 = computed(() => rows.value.filter(r => r.round === 2).sort((a, b) => a.pickNumber - b.pickNumber))
const mobileRounds = computed(() => [
  { number: 1, label: '第一轮选秀', picks: round1.value },
  { number: 2, label: '第二轮选秀', picks: round2.value },
])
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

function getHeadshotUrl(nbaPlayerId: number): string {
  return `https://cdn.nba.com/headshots/nba/latest/260x190/${nbaPlayerId}.png`
}

function onHeadshotError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
  const fallback = img.parentElement?.querySelector('.player-avatar-placeholder') as HTMLElement
  if (fallback) fallback.style.display = 'flex'
}

function goPlayerDetail(playerId: number) {
  router.push({ path: '/players/detail', query: { id: String(playerId) } })
}

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
/* 紧凑统计摘要 */
.stats-inline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 28px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
}
.stats-inline .stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
.stats-inline .stat strong {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 800;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

/* 工具栏布局 */
.toolbar-spacer { flex: 1; }

/* 行内操作按钮 */
.row-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin: 0 2px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.row-action-btn:hover,
.row-action-btn:focus-visible {
  background: var(--bg-hover);
  color: var(--accent);
}
.row-action-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}
.dropdown-danger { color: var(--danger); }

.page-wrapper {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 24px;
  min-height: calc(100vh - 108px);
  position: relative;
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
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--shadow-md) !important;
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
  background: var(--accent);
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
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--accent-glow);
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
.draft-mobile-list { display: none; }
.draft-pick-card { background: var(--bg-card); }
@media (max-width: 768px) {
  .page-wrapper { padding: 0 16px; }
  .round-section { display: none; }
  .draft-mobile-list { display: block; }
  .draft-mobile-round { margin-bottom: 24px; }
  .draft-pick-card {
    margin-bottom: 10px;
    padding: 14px 16px;
    border: 1px solid var(--border-light);
    border-radius: 12px;
  }
  .draft-pick-card__top,
  .draft-pick-card__team,
  .draft-pick-card__player,
  .draft-pick-card__actions { display: flex; align-items: center; }
  .draft-pick-card__top { justify-content: space-between; gap: 10px; margin-bottom: 12px; }
  .draft-pick-card__number { color: var(--accent); font-size: 12px; font-weight: 800; white-space: nowrap; flex-shrink: 0; }
  .draft-pick-card__team { gap: 6px; min-width: 0; color: var(--text-secondary); font-size: 12px; text-align: right; overflow: hidden; }
  .draft-pick-team-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 140px; }
  .draft-pick-card__team img { width: 22px; height: 22px; object-fit: contain; flex-shrink: 0; }
  .draft-pick-card__player { gap: 12px; }
  .draft-pick-card__player .player-avatar-sm { width: 44px; height: 44px; }
  .draft-pick-card__name { display: grid; gap: 2px; min-width: 0; }
  .draft-pick-card__name button { padding: 0; border: 0; background: none; color: var(--text-primary); font: inherit; font-weight: 700; text-align: left; cursor: pointer; }
  .draft-pick-card__name strong { color: var(--text-primary); font-size: 14px; }
  .draft-pick-card__name small { color: var(--text-muted); font-size: 11px; }
  .draft-pick-card__note { margin: 10px 0 0; color: var(--text-secondary); font-size: 12px; }
  .draft-pick-card__actions { justify-content: flex-end; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--border-light); }
  .draft-pick-card__actions button { min-width: 44px; min-height: 36px; padding: 0 10px; border: 1px solid var(--border-light); border-radius: 8px; background: var(--bg-hover); color: var(--text-primary); cursor: pointer; }
  .draft-pick-card__actions .is-danger { color: var(--danger); }
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
  background: var(--accent);
  color: var(--accent-contrast, #04202A);
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
.pick-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: 4px;
  font-family: var(--font-heading);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.3px;
  font-variant-numeric: tabular-nums;
}
.pick-icon {
  flex-shrink: 0;
}
.pick-top1 {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.22), rgba(245, 158, 11, 0.08));
  color: #F59E0B;
  border: 1px solid rgba(245, 158, 11, 0.45);
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.2);
}
.pick-top2 {
  background: linear-gradient(135deg, rgba(148, 163, 184, 0.22), rgba(148, 163, 184, 0.08));
  color: #94A3B8;
  border: 1px solid rgba(148, 163, 184, 0.45);
}
.pick-top3 {
  background: linear-gradient(135deg, rgba(217, 119, 6, 0.22), rgba(217, 119, 6, 0.08));
  color: #D97706;
  border: 1px solid rgba(217, 119, 6, 0.45);
}
.pick-lottery {
  background: var(--bg-hover);
  color: var(--accent);
  border: 1px solid var(--border-light);
}

[data-theme="light"] .pick-top1 {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.18), rgba(245, 158, 11, 0.06));
  color: #B45309;
  border-color: rgba(217, 119, 6, 0.4);
}
[data-theme="light"] .pick-top2 {
  background: linear-gradient(135deg, rgba(100, 116, 139, 0.16), rgba(100, 116, 139, 0.06));
  color: #475569;
  border-color: rgba(100, 116, 139, 0.35);
}
[data-theme="light"] .pick-top3 {
  background: linear-gradient(135deg, rgba(180, 83, 9, 0.16), rgba(180, 83, 9, 0.06));
  color: #92400E;
  border-color: rgba(180, 83, 9, 0.35);
}
[data-theme="light"] .pick-lottery {
  background: rgba(8, 145, 178, 0.08);
  color: #0891B2;
  border-color: rgba(8, 145, 178, 0.25);
}

/* 选中球队单元格 */
.pick-team-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pick-team-logo {
  width: 22px;
  height: 22px;
  object-fit: contain;
  flex-shrink: 0;
}
.lottery-pick {
  color: var(--accent);
  font-weight: 800;
}
.player-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.player-name-cell.clickable {
  cursor: pointer;
}
.player-name-cell.clickable:hover .player-name-cn {
  color: var(--accent);
}
.player-avatar-sm {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
}
.headshot-sm {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.player-avatar-placeholder {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-heading);
}
.player-name-cn {
  font-weight: 600;
  color: var(--text-primary);
  display: block;
}
.player-name-en {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 400;
  display: block;
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
