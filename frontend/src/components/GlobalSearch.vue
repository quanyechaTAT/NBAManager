<template>
  <div class="global-search" ref="searchRef">
    <div class="search-wrap" :class="{ 'search-wrap--focus': focused }">
      <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>
      </svg>
      <input v-model="query" class="search-input" type="text" placeholder="搜索球员、球队、资讯..."
        @input="onInput" @focus="onFocus" @blur="onBlur" @keydown.escape="showDropdown = false" />
    </div>
    <Teleport to="body">
      <div v-if="showDropdown && query.length >= 1" class="search-dropdown" :style="dropdownStyle" @mousedown.prevent>
        <div v-if="searching" class="search-loading">搜索中...</div>
        <template v-else-if="hasResults">
          <div v-if="playerResults.length" class="search-group">
            <div class="search-group-title">🏀 球员</div>
            <div v-for="p in playerResults" :key="'p'+p.id" class="search-item" @mousedown.prevent="goPlayer(p.id)">
              <span>{{ p.name }}</span>
              <span class="search-sub">{{ p.teamName }} · {{ p.position }}</span>
            </div>
          </div>
          <div v-if="teamResults.length" class="search-group">
            <div class="search-group-title">🏟️ 球队</div>
            <div v-for="t in teamResults" :key="'t'+t.id" class="search-item" @mousedown.prevent="goTeam(t.name)">
              <span>{{ t.name }}</span>
              <span class="search-sub">{{ t.city }} · {{ t.conference }}</span>
            </div>
          </div>
          <div v-if="newsResults.length" class="search-group">
            <div class="search-group-title">📰 资讯</div>
            <div v-for="n in newsResults" :key="'n'+n.id" class="search-item" @mousedown.prevent="goNews">
              <span>{{ n.title.slice(0, 40) }}</span>
            </div>
          </div>
        </template>
        <div v-else-if="query.length >= 1 && !searching" class="search-empty">无搜索结果</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPlayers } from '@/api/player'
import { fetchTeams } from '@/api/team'
import { fetchNews } from '@/api/news'
import type { Player, Team, GameNews } from '@/api/types'

const router = useRouter()
const query = ref('')
const showDropdown = ref(false)
const focused = ref(false)
const searching = ref(false)
const searchRef = ref<HTMLElement | null>(null)
const playerResults = ref<Player[]>([])
const teamResults = ref<Team[]>([])
const newsResults = ref<GameNews[]>([])

let timer: ReturnType<typeof setTimeout> | null = null

const hasResults = computed(() => playerResults.value.length + teamResults.value.length + newsResults.value.length > 0)

const dropdownStyle = computed(() => {
  if (!searchRef.value) return { position: 'fixed' as const, top: '0px', left: '0px', width: '300px' }
  const rect = searchRef.value.getBoundingClientRect()
  return {
    position: 'fixed' as const,
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
  }
})

function onFocus() {
  focused.value = true
  showDropdown.value = true
}

function onBlur() {
  focused.value = false
  setTimeout(() => { showDropdown.value = false }, 200)
}

function onInput() {
  if (timer) clearTimeout(timer)
  if (!query.value.trim()) {
    playerResults.value = []; teamResults.value = []; newsResults.value = []
    return
  }
  showDropdown.value = true
  timer = setTimeout(search, 300)
}

async function search() {
  searching.value = true
  const q = query.value.trim()
  try {
    const [players, teams, news] = await Promise.all([
      fetchPlayers({ q, page: 0, size: 5 }).catch(() => ({ data: { content: [] } })),
      fetchTeams({ q, page: 0, size: 5 }).catch(() => ({ data: { content: [] } })),
      fetchNews({ q, page: 0, size: 5 }).catch(() => ({ data: { content: [] } })),
    ])
    playerResults.value = players.data.content
    teamResults.value = teams.data.content
    newsResults.value = news.data.content
  } catch { /* ignore */ }
  finally { searching.value = false }
}

function goPlayer(id: number) { showDropdown.value = false; query.value = ''; router.push({ path: '/players/detail', query: { id: String(id) } }) }
function goTeam(name: string) { showDropdown.value = false; query.value = ''; router.push({ path: '/teams/detail', query: { name } }) }
function goNews() { showDropdown.value = false; query.value = ''; router.push('/news') }
</script>

<style scoped>
.global-search { position: relative; flex: 1; max-width: 320px; }
.search-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-input);
  border: 1.5px solid var(--border-light);
  border-radius: 10px;
  padding: 7px 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}
.search-wrap--focus {
  border-color: var(--accent, var(--purple)) !important;
  box-shadow: 0 0 0 3px rgba(232, 93, 38, 0.10);
  background: var(--bg-card) !important;
}
.search-icon { color: var(--text-dim); flex-shrink: 0; transition: color 0.2s; }
.search-wrap--focus .search-icon { color: var(--accent, var(--purple)); }
.search-input {
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 13px;
  width: 100%;
  font-family: var(--font-body);
}
.search-input::placeholder { color: var(--text-dim); }

/* ===== 亮色主题搜索框覆盖 ===== */
[data-theme="light"] .search-wrap {
  background: #F5F3F0 !important;
  border-color: #E8E5E0 !important;
}
[data-theme="light"] .search-wrap--focus {
  border-color: #E85D26 !important;
  box-shadow: 0 0 0 3px rgba(232, 93, 38, 0.10) !important;
  background: #FFFFFF !important;
}
[data-theme="light"] .search-icon { color: #A8A29E !important; }
[data-theme="light"] .search-wrap--focus .search-icon { color: #E85D26 !important; }
[data-theme="light"] .search-input { color: #1A1A1A !important; }
[data-theme="light"] .search-input::placeholder { color: #A8A29E !important; }
</style>

<style>
/* ===== 搜索下拉框（Teleport 到 body，全局样式） ===== */
.search-dropdown {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
  z-index: 9999;
  max-height: 400px;
  overflow-y: auto;
  animation: searchDropIn 0.2s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes searchDropIn {
  from { opacity: 0; transform: translateY(-6px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.search-loading,
.search-empty {
  padding: 20px 16px;
  text-align: center;
  color: var(--text-dim);
  font-size: 13px;
}
.search-group { padding: 6px 0; }
.search-group + .search-group { border-top: 1px solid var(--border-light); }
.search-group-title {
  padding: 8px 16px 4px;
  font-size: 11px;
  color: var(--text-dim);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, sans-serif;
}
.search-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary);
  transition: background-color 0.15s ease;
  border-radius: 0;
}
.search-item:hover { background: var(--bg-hover); }
.search-sub { font-size: 11px; color: var(--text-dim); }

/* ===== 亮色主题下拉框覆盖 ===== */
[data-theme="light"] .search-dropdown {
  background: #FFFFFF !important;
  border: 1px solid #E8E5E0 !important;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08), 0 4px 12px rgba(0, 0, 0, 0.04) !important;
  border-radius: 12px !important;
}
[data-theme="light"] .search-group { border-color: #F3F0EB !important; }
[data-theme="light"] .search-group-title { color: #A8A29E !important; }
[data-theme="light"] .search-item { color: #1A1A1A !important; }
[data-theme="light"] .search-item:hover { background: #FAF9F7 !important; }
[data-theme="light"] .search-sub { color: #A8A29E !important; }
[data-theme="light"] .search-loading,
[data-theme="light"] .search-empty { color: #A8A29E !important; }
</style>
