<template>
  <section class="discovery-feed" aria-label="为你推荐">
    <header><div><span class="kicker">DISCOVER YOUR GAME</span><h2>{{ title || '为你推荐' }}</h2><p>从赛场动态到球迷观点，发现值得一看的内容</p></div><button type="button" :disabled="loading" @click="load(true)">换一批</button></header>
    <p v-if="degraded && cards.length" class="discovery-status" role="status">当前展示基础推荐，稍后可刷新查看。</p>
    <p v-if="dismissed" class="discovery-undo" role="status">已减少这类内容 <button type="button" @click="undo">撤销</button></p>
    <div v-if="loading && !cards.length" class="discovery-grid" aria-busy="true"><div v-for="n in 3" :key="n" class="placeholder" /></div>
    <div v-else-if="cards.length" class="discovery-grid" :class="{ compact }">
      <DiscoveryCard v-for="row in cards" :key="`${row.requestId}:${row.item.itemKey}`" v-bind="row" dismissible @dismiss="dismiss" />
    </div>
    <p v-else class="discovery-status">{{ failed ? '推荐暂时无法加载。' : '还没有可推荐的内容，稍后再来看看。' }} <button v-if="failed" @click="load(true)">重试</button></p>
    <button v-if="cursor && !compact" class="more" :disabled="loading" @click="load(false)">{{ loading ? '加载中…' : '查看更多' }}</button>
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { discover, discoveryFeedback, type DiscoveryItem, type DiscoveryScene } from '@/api/discovery'
import { useAuthStore } from '@/stores/auth'
import DiscoveryCard from './DiscoveryCard.vue'
const props = withDefaults(defineProps<{ scene: DiscoveryScene; itemKey?: string; title?: string; compact?: boolean }>(), { compact: false })
const auth = useAuthStore()
type Row = { item: DiscoveryItem; requestId: string; position: number }
const cards = ref<Row[]>([]), loading = ref(false), failed = ref(false), degraded = ref(false), cursor = ref<string | null>(null)
const dismissed = ref<Row | null>(null)
let controller: AbortController | undefined
let generation = 0
async function load(reset: boolean) {
  controller?.abort(); const pending = new AbortController(); controller = pending
  if (reset) { cards.value = []; cursor.value = null }
  if (!auth.token) return
  loading.value = true; failed.value = false
  try {
    const { data } = await discover('recommendations', { scene: props.scene, itemKey: props.itemKey,
      limit: props.compact ? 6 : 20, cursor: !reset && cursor.value ? cursor.value : undefined }, pending.signal)
    if (pending.signal.aborted) return
    const known = new Set(cards.value.map(row => row.item.itemKey))
    cards.value.push(...data.items.map((item, position) => ({ item, position, requestId: data.requestId })).filter(row => !known.has(row.item.itemKey)))
    cursor.value = data.nextCursor; degraded.value = data.degraded
  } catch (error) {
    if (!pending.signal.aborted) { failed.value = true; if (cards.value.length) ElMessage.info('列表已更新，请点击换一批重新加载') }
  } finally { if (controller === pending) loading.value = false }
}
async function dismiss(item: DiscoveryItem) {
  const pending = generation
  try { await discoveryFeedback(item.itemKey, true); if (pending !== generation) return; dismissed.value = cards.value.find(row => row.item.itemKey === item.itemKey) || null; cards.value = cards.value.filter(row => row.item.itemKey !== item.itemKey) }
  catch { if (pending === generation) ElMessage.error('暂时无法保存，请重试') }
}
async function undo() {
  const row = dismissed.value; if (!row) return
  const pending = generation
  try { await discoveryFeedback(row.item.itemKey, false); if (pending !== generation) return; cards.value.unshift(row); dismissed.value = null }
  catch { if (pending === generation) ElMessage.error('撤销失败，请重试') }
}
const refresh = () => { generation++; dismissed.value = null; void load(true) }
watch(() => [props.scene, props.itemKey, auth.token], refresh, { immediate: true })
window.addEventListener('discovery-preferences-changed', refresh)
onBeforeUnmount(() => { generation++; controller?.abort(); window.removeEventListener('discovery-preferences-changed', refresh) })
</script>
<style scoped>
.discovery-feed{margin:24px 0}.discovery-feed header{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:18px}.kicker{font-size:10px;letter-spacing:1.5px;color:var(--accent);font-weight:700}.discovery-feed h2{margin:6px 0;font-size:23px;letter-spacing:-.5px}.discovery-feed header p{margin:0;color:var(--text-muted);font-size:12px}.discovery-feed button{background:var(--bg-card);color:var(--text-primary);border:1px solid var(--border-light);border-radius:8px;padding:8px 13px;cursor:pointer;white-space:nowrap}.discovery-feed button:disabled{opacity:.5;cursor:wait}.discovery-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.discovery-grid.compact{grid-template-columns:repeat(3,minmax(0,1fr))}.discovery-status,.discovery-undo{padding:14px;color:var(--text-muted);font-size:13px;background:var(--bg-input);border-radius:10px}.discovery-undo button{margin-left:12px}.more{display:block;margin:20px auto 0}.placeholder{height:160px;border-radius:14px;background:var(--bg-input)}@media(max-width:1100px){.discovery-grid.compact{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:700px){.discovery-grid,.discovery-grid.compact{grid-template-columns:1fr}.discovery-feed header p{max-width:240px;line-height:1.6}.discovery-feed h2{font-size:21px}}
</style>
