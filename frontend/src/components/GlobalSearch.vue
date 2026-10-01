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
            <div class="search-group-title"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg> 球员</div>
            <div v-for="p in playerResults" :key="'p'+p.id" class="search-item" @mousedown.prevent="goPlayer(p.id)">
              <span>{{ p.name }}</span>
              <span class="search-sub">{{ p.teamName }} · {{ p.position }}</span>
            </div>
          </div>
          <div v-if="teamResults.length" class="search-group">
            <div class="search-group-title"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><path d="M16 3h5v5"/><path d="M8 21H3v-5"/><path d="M21 3l-7 7"/><path d="M3 21l7-7"/></svg> 球队</div>
            <div v-for="t in teamResults" :key="'t'+t.id" class="search-item" @mousedown.prevent="goTeam(t.name)">
              <span>{{ t.name }}</span>
              <span class="search-sub">{{ t.city }} · {{ t.conference }}</span>
            </div>
          </div>
          <div v-if="newsResults.length" class="search-group">
            <div class="search-group-title"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/><path d="M18 14h-8"/><path d="M15 18h-5"/><path d="M10 6h8v4h-8V6Z"/></svg> 资讯</div>
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
.global-search { position: relative; flex: 1; max-width: 340px; }
.search-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  padding: 8px 14px;
  transition:
    border-color var(--duration-nav, 0.18s) var(--ease-smooth),
    box-shadow var(--duration-nav, 0.18s) var(--ease-smooth),
    background-color var(--duration-nav, 0.18s) var(--ease-smooth),
    transform var(--duration-nav, 0.18s) var(--ease-out-quart, ease);
}
.search-wrap--focus {
  border-color: rgba(34, 211, 238, 0.55) !important;
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12), 0 8px 22px rgba(0, 0, 0, 0.12);
  background: var(--bg-card) !important;
  transform: translateY(-1px);
}
.search-icon { color: var(--text-dim); flex-shrink: 0; transition: color 0.2s; }
.search-wrap--focus .search-icon { color: var(--accent); }
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

[data-theme="light"] .search-wrap {
  background: rgba(248, 250, 252, 0.95) !important;
  border-color: var(--border-light) !important;
}
[data-theme="light"] .search-wrap--focus {
  border-color: rgba(34, 211, 238, 0.45) !important;
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.1), 0 8px 20px rgba(15, 23, 42, 0.06) !important;
  background: #FFFFFF !important;
}

@media (max-width: 768px) {
  .global-search {
    max-width: 148px;
    flex: 1;
  }
  .search-wrap {
    padding: 6px 10px;
    gap: 4px;
  }
  .search-input {
    font-size: 12px;
  }
}
</style>

<style>
/* ===== 搜索下拉框（Teleport 到 body，全局样式） ===== */
.search-dropdown {
  background: var(--surface-glass, var(--bg-card));
  backdrop-filter: blur(16px) saturate(150%);
  -webkit-backdrop-filter: blur(16px) saturate(150%);
  border: 1px solid var(--surface-glass-border, var(--border-light));
  border-radius: 14px;
  box-shadow: var(--shadow-lg), 0 16px 40px rgba(0, 0, 0, 0.18);
  z-index: 9999;
  max-height: 400px;
  overflow-y: auto;
  animation: searchDropIn 0.22s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
  overflow: hidden;
}

@keyframes searchDropIn {
  from { opacity: 0; transform: translateY(-8px) scale(0.98); }
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
  font-family: var(--font-heading);
}
.search-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary);
  transition:
    background-color 0.15s ease,
    transform 0.15s ease,
    padding-left 0.15s ease;
}
.search-item:hover {
  background: var(--bg-hover);
  padding-left: 18px;
}
.search-sub { font-size: 11px; color: var(--text-dim); }

[data-theme="light"] .search-dropdown {
  background: rgba(255, 255, 255, 0.96) !important;
  border: 1px solid var(--border-light) !important;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.1), 0 4px 12px rgba(15, 23, 42, 0.04) !important;
}
</style>
