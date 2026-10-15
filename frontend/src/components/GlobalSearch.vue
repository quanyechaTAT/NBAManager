<template>
  <div ref="element" class="global-search" @focusin="cancelClose" @focusout="closeLater" @keydown.escape="opened = false">
    <form role="search" @submit.prevent="allResults"><span aria-hidden="true">⌕</span><input v-model="query" type="search" aria-label="全站搜索" placeholder="球员、球队、比赛、资讯…" autocomplete="off" @input="schedule" @focus="opened = true" /></form>
    <div v-if="opened && query.trim()" class="suggestions" @mousedown.prevent>
      <p v-if="loading">正在查找…</p>
      <button v-for="(item, position) in result?.items || []" :key="item.itemKey" type="button" @click="open(item, position)"><span>{{ discoveryLabels[item.type] }}</span><strong>{{ item.title }}</strong></button>
      <p v-if="!loading && !result?.items.length">按回车查看完整搜索结果</p>
      <button class="all" @click="allResults">搜索“{{ query }}”的全部结果 <span>↵</span></button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { discover, discoveryLabels, type DiscoveryItem, type DiscoveryResponse } from '@/api/discovery'
import { useAuthStore } from '@/stores/auth'
import { attribution, sendDiscoveryEvent } from '@/utils/discoveryTelemetry'
const router = useRouter(), auth = useAuthStore()
const element = ref<HTMLElement>()
const query = ref(''), opened = ref(false), loading = ref(false), result = ref<DiscoveryResponse | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined, blurTimer: ReturnType<typeof setTimeout> | undefined, controller: AbortController | undefined
function schedule() {
  clearTimeout(timer); controller?.abort(); result.value = null; loading.value = false
  if (!query.value.trim()) return
  opened.value = true
  timer = setTimeout(async () => {
    const pending = new AbortController(); controller = pending; loading.value = true
    try { const { data } = await discover('suggest', { q: query.value.trim(), limit: 8 }, pending.signal); if (!pending.signal.aborted) result.value = data }
    catch { /* Enter still opens the searchable results page. */ }
    finally { if (controller === pending) loading.value = false }
  }, 200)
}
function cancelClose() { clearTimeout(blurTimer) }
function closeLater() {
  cancelClose()
  blurTimer = setTimeout(() => { if (!element.value?.contains(document.activeElement)) opened.value = false }, 150)
}
function allResults() { opened.value = false; clearTimeout(timer); controller?.abort(); if (query.value.trim()) void router.push({ path: '/search', query: { q: query.value.trim() } }) }
async function open(item: DiscoveryItem, position: number) {
  opened.value = false
  if (result.value) void sendDiscoveryEvent(attribution(result.value.requestId, item.itemKey, position), 'CLICK')
  await router.push(item.target)
}
watch(() => auth.token, () => { controller?.abort(); clearTimeout(timer); result.value = null; query.value = ''; opened.value = false })
onBeforeUnmount(() => { controller?.abort(); clearTimeout(timer); clearTimeout(blurTimer) })
</script>
<style scoped>
.global-search{position:relative;flex:1;max-width:340px;min-width:110px}.global-search form{display:flex;gap:9px;align-items:center;border:1px solid var(--border-light);border-radius:999px;background:var(--bg-input);padding:8px 14px}.global-search form:focus-within{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-lighter)}.global-search form>span{font-size:23px;line-height:16px;color:var(--text-muted)}input{background:transparent;border:0;outline:0;color:var(--text-primary);font-size:13px;min-width:0;width:100%}.suggestions{position:absolute;top:calc(100% + 9px);left:0;width:max(100%,300px);max-width:calc(100vw - 28px);padding:8px;border:1px solid var(--border-light);border-radius:14px;background:var(--bg-card);box-shadow:0 14px 44px #0003;z-index:2100}.suggestions button{display:flex;text-align:left;align-items:center;gap:10px;border:0;padding:11px 10px;width:100%;background:transparent;color:var(--text-primary);cursor:pointer;border-radius:7px}.suggestions button:hover{background:var(--bg-input)}.suggestions button>span{color:var(--accent);font-size:11px;flex-shrink:0}.suggestions strong{font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.suggestions p{font-size:12px;color:var(--text-muted);padding:6px 10px}.suggestions .all{border-top:1px solid var(--border-light);border-radius:0;justify-content:space-between;font-size:12px}@media(max-width:768px){.global-search{max-width:180px}.global-search form{padding:7px 10px}.suggestions{left:auto;right:0}}
</style>
