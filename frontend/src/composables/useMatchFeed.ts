import { computed, ref } from 'vue'
import type { MatchRecord } from '@/api/types'
import { fetchMatchRecords } from '@/api/matchRecord'
import { buildTickerMatches, classifyMatches, liveMatches } from '@/utils/matchFeed'

/**
 * 全局共享的赛事展示状态。
 * 首页转播面板与顶部赛况条复用同一份 /match-records 结果，避免重复请求与口径不一致。
 */
const matches = ref<MatchRecord[]>([])
const loading = ref(false)
const failed = ref(false)
let inflight: Promise<void> | null = null

async function refresh(): Promise<void> {
  if (inflight) return inflight
  loading.value = true
  failed.value = false
  inflight = (async () => {
    try {
      const { data } = await fetchMatchRecords()
      matches.value = Array.isArray(data) ? data : []
    } catch {
      // 保持上一次成功的数据，仅标记失败供 UI 提示
      failed.value = true
    } finally {
      loading.value = false
      inflight = null
    }
  })()
  return inflight
}

export function useMatchFeed() {
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
