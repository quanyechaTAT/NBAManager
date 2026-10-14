<template>
  <main class="search-page">
    <header><span class="kicker">NBA DISCOVERY</span><h1>找到你的篮球世界</h1><p>球员、球队、比赛、资讯与球迷社区，一次搜索连接起来。</p></header>
    <form class="search-form" role="search" @submit.prevent="submit"><input v-model="draft" aria-label="搜索关键词" placeholder="试试：库里、Lakers、季后赛…" maxlength="200" /><button type="submit">搜索</button></form>
    <nav class="search-types" aria-label="内容类型"><button v-for="type in kinds" :key="type.value" :class="{ selected: filters.types === type.value }" :aria-pressed="filters.types === type.value" @click="filter('types', type.value)">{{ type.label }}</button></nav>
    <div class="search-filters">
      <label>球队<select :value="filters.team" @change="filter('team', ($event.target as HTMLSelectElement).value)"><option value="">全部球队</option><option v-for="team in teams" :key="team.id" :value="`TEAM:${team.id}`">{{ team.name }}</option></select></label>
      <label>赛季<input :value="filters.season" placeholder="例如 2025-26" maxlength="16" @change="filter('season', ($event.target as HTMLInputElement).value)" /></label>
      <label>开始日期<input :value="filters.dateFrom" type="date" @change="filter('dateFrom', ($event.target as HTMLInputElement).value)" /></label>
      <label>结束日期<input :value="filters.dateTo" type="date" @change="filter('dateTo', ($event.target as HTMLInputElement).value)" /></label>
      <button v-if="hasFilters" @click="clearFilters">清除筛选</button>
    </div>
    <div class="search-summary"><span>{{ loading && !rows.length ? '正在搜索…' : `已展示 ${rows.length} 条结果` }}{{ degraded ? ' · 当前使用基础检索' : '' }}</span><RouterLink :to="{ path: '/smart-search', query: { q: filters.q } }">向 AI 助手深入提问 ↗</RouterLink></div>
    <p v-if="failed" class="search-empty" role="alert">搜索暂时无法完成，请检查筛选条件后重试。<button @click="load(true)">重试</button></p>
    <div v-if="loading && !rows.length" class="search-skeleton" aria-busy="true"><div v-for="n in 4" :key="n" /></div>
    <div v-else-if="rows.length" class="search-results"><DiscoveryCard v-for="row in rows" :key="`${row.requestId}:${row.item.itemKey}`" v-bind="row" :query="filters.q" /></div>
    <div v-else-if="!failed" class="search-empty"><h2>暂时没有找到相关内容</h2><p>试试中英文名称、常见昵称，或减少筛选条件。</p></div>
    <button v-if="cursor" class="more" :disabled="loading" @click="load(false)">{{ loading ? '加载中…' : '加载更多结果' }}</button>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { discover, type DiscoveryItem, type DiscoveryQuery } from '@/api/discovery'
import { fetchTeams } from '@/api/team'
import { useAuthStore } from '@/stores/auth'
import DiscoveryCard from '@/components/discovery/DiscoveryCard.vue'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const draft = ref(''), loading = ref(false), failed = ref(false), degraded = ref(false), cursor = ref<string | null>(null)
const teams = ref<{ id: number; name: string }[]>([])
const rows = ref<{ item: DiscoveryItem; requestId: string; position: number }[]>([])
const kinds = [{ value: '', label: '全部' }, { value: 'PLAYER', label: '球员' }, { value: 'TEAM', label: '球队' }, { value: 'MATCH', label: '比赛' }, { value: 'NEWS', label: '资讯' }, { value: 'POST', label: '社区' }]
const filters = computed(() => Object.fromEntries(['q', 'types', 'team', 'season', 'dateFrom', 'dateTo'].map(key => [key, typeof route.query[key] === 'string' ? route.query[key] : ''])) as Record<string, string>)
const hasFilters = computed(() => Object.entries(filters.value).some(([key, value]) => key !== 'q' && value))
let controller: AbortController | undefined
function submit() { void router.replace({ path: '/search', query: { ...filters.value, q: draft.value.trim() } }); if (draft.value.trim() === filters.value.q) void load(true) }
function filter(key: string, value: string) { void router.replace({ path: '/search', query: { ...filters.value, [key]: value } }) }
function clearFilters() { void router.replace({ path: '/search', query: { q: filters.value.q } }) }
async function load(reset: boolean) {
  controller?.abort(); const pending = new AbortController(); controller = pending
  if (reset) { rows.value = []; cursor.value = null }
  if (!auth.token) return
  loading.value = true; failed.value = false
  try {
    const params: DiscoveryQuery = { ...filters.value, limit: 20, cursor: !reset && cursor.value ? cursor.value : undefined }
    const { data } = await discover('search', params, pending.signal)
    if (pending.signal.aborted) return
    const seen = new Set(rows.value.map(row => row.item.itemKey))
    rows.value.push(...data.items.map((item, position) => ({ item, position, requestId: data.requestId })).filter(row => !seen.has(row.item.itemKey)))
    cursor.value = data.nextCursor; degraded.value = data.degraded
  } catch { if (!pending.signal.aborted) failed.value = true }
  finally { if (controller === pending) loading.value = false }
}
watch(() => [route.query, auth.token], () => { draft.value = filters.value.q; void load(true) }, { immediate: true })
void fetchTeams({ page: 0, size: 100 }).then(({ data }) => { teams.value = data.content }).catch(() => {})
onBeforeUnmount(() => controller?.abort())
</script>
<style scoped>
.search-page{max-width:1060px;margin:0 auto;padding:34px 28px 70px}.kicker{font-size:11px;color:var(--accent);font-weight:700;letter-spacing:1.8px}.search-page h1{font-size:32px;letter-spacing:-1px;margin:10px 0}.search-page header p{font-size:14px;color:var(--text-muted);line-height:1.8}.search-form{display:flex;gap:12px;margin:28px 0 22px;padding:8px;border:1px solid var(--border-light);background:var(--bg-card);border-radius:14px;box-shadow:0 4px 20px #00000008}.search-form:focus-within{border-color:var(--accent)}.search-form input{border:0;outline:0;flex:1;min-width:0;background:transparent;color:var(--text-primary);font-size:16px;padding:8px 14px}.search-form button{border:0;border-radius:8px;background:var(--accent);color:#081521;padding:10px 26px;font-weight:700;cursor:pointer}.search-types{display:flex;gap:6px;flex-wrap:wrap;border-bottom:1px solid var(--border-light);padding-bottom:16px}.search-types button{border:0;border-radius:8px;padding:10px 20px;background:transparent;color:var(--text-muted);font-size:14px;cursor:pointer}.search-types button.selected{color:var(--accent);background:var(--accent-lighter);font-weight:700}.search-filters{display:flex;gap:14px;align-items:flex-end;flex-wrap:wrap;margin:20px 0}.search-filters label{display:grid;gap:7px;font-size:11px;color:var(--text-muted)}.search-filters input,.search-filters select{width:145px;min-height:36px;padding:7px 10px;border:1px solid var(--border-light);border-radius:7px;color:var(--text-primary);background:var(--bg-card);font:inherit;box-sizing:border-box}.search-filters button,.more,.search-empty button{padding:9px 14px;border:1px solid var(--border-light);border-radius:8px;background:var(--bg-card);color:var(--text-primary);cursor:pointer}.search-summary{display:flex;justify-content:space-between;gap:16px;font-size:12px;color:var(--text-muted);margin:28px 0 14px}.search-summary a{color:var(--accent);text-decoration:none}.search-results,.search-skeleton{display:grid;gap:12px}.search-skeleton>div{height:136px;border-radius:14px;background:var(--bg-input)}.search-empty{text-align:center;padding:65px 20px;color:var(--text-muted);font-size:14px}.search-empty h2{font-size:20px;color:var(--text-primary)}.search-empty button{margin-left:10px}.more{display:block;margin:24px auto}@media(max-width:600px){.search-page{padding:24px 14px}.search-page h1{font-size:26px}.search-types button{padding:8px 12px}.search-filters{gap:12px}.search-filters label{width:calc(50% - 6px)}.search-filters input,.search-filters select{width:100%}.search-summary{flex-direction:column;gap:10px}}
</style>
