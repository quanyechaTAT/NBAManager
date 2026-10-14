import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { MatchRecord } from '@/api/types'
import { fetchMatchRecords } from '@/api/matchRecord'
import { buildTickerMatches, classifyMatches, liveMatches } from '@/utils/matchFeed'
import { queryCache } from '@/utils/queryCache'

/**
 * 全局共享的赛事展示状态。
 * 首页转播面板与顶部赛况条复用同一份 /match-records 结果，避免重复请求与口径不一致。
 */
const matches = ref<MatchRecord[]>([])
const loading = ref(false)
const failed = ref(false)
let inflight: Promise<void> | null = null
let requestController: AbortController | null = null
let subscribers = 0
let timer: ReturnType<typeof setTimeout> | null = null
let pollGeneration = 0

function feedTtl(data: MatchRecord[]): number {
  return data.some(match => match.status === 'LIVE') ? 10_000 : 60_000
}

function isVisible() { return document.visibilityState === 'visible' }

async function refresh(): Promise<void> {
  if (inflight && !requestController?.signal.aborted) return inflight
  if (document.visibilityState === 'hidden') return
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  failed.value = false
  inflight = (async () => {
    try {
      const data = await queryCache.fetch('match-feed', async signal => {
        const response = await fetchMatchRecords(undefined, signal)
        return Array.isArray(response.data) ? response.data : []
      }, { ttlMs: feedTtl, signal: controller.signal })
      if (!controller.signal.aborted) matches.value = data
    } catch {
      // 保持上一次成功的数据，仅标记失败供 UI 提示
      if (!controller.signal.aborted) failed.value = true
    } finally {
      if (requestController === controller) {
        loading.value = false
        inflight = null
      }
    }
  })()
  return inflight
}

function stopTimer() {
  pollGeneration++
  if (timer) clearTimeout(timer)
  timer = null
}

async function poll() {
  stopTimer()
  const generation = pollGeneration
  if (!subscribers || !isVisible()) return
  await refresh()
  if (generation === pollGeneration && subscribers && isVisible()) {
    timer = setTimeout(() => void poll(), feedTtl(matches.value))
  }
}

function visibilityChanged() {
  stopTimer()
  if (document.visibilityState === 'visible') void poll()
}

export function useMatchFeed() {
  onMounted(() => {
    subscribers++
    if (subscribers === 1) {
      document.addEventListener('visibilitychange', visibilityChanged)
      void poll()
    }
  })
  onUnmounted(() => {
    subscribers--
    if (subscribers === 0) {
      stopTimer()
      requestController?.abort()
      document.removeEventListener('visibilitychange', visibilityChanged)
    }
  })
  /** 赛况条展示列表：直播中 → 今日赛程 → 最近赛果 */
  const tickerMatches = computed(() => buildTickerMatches(matches.value))
  /** 分组（含标签），供需要分类展示的页面使用 */
  const groups = computed(() => classifyMatches(matches.value))
  /** 全部正在直播的比赛 */
  const live = computed(() => liveMatches(matches.value))
  /** 转播面板主角：优先直播，其次今日，再次最近赛果 */
  const headline = computed<MatchRecord | null>(() => tickerMatches.value[0] ?? null)
  /** 主角所属分类标签 */
  const headlineLabel = computed(() => groups.value[0]?.label ?? '')

  return { matches, loading, failed, refresh, tickerMatches, groups, live, headline, headlineLabel }
}
