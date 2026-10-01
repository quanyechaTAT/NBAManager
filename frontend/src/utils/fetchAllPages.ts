/**
 * 分页遍历工具。
 *
 * 后端 `size` 上限为 100（安全测试建议值），个人中心收藏/导出这类
 * "需要全量数据"的场景不能再靠 `size: 1000` 一次拉取，改为按页循环。
 * 这样既不会触发 400，也避免单次请求把大结果集读进内存。
 */
export const MAX_PAGE_SIZE = 100

interface PageLike<T> {
  content?: T[]
  totalPages?: number
  last?: boolean
}

export interface FetchAllOptions {
  /** 每页条数，默认 100（后端上限） */
  pageSize?: number
  /** 安全上限：最多拉取多少条，防止总量异常时无限循环 */
  maxItems?: number
}

/**
 * 逐页调用 `fetchPage` 直到取完（或达到 maxItems），返回拼接后的数组。
 * 调用方负责传入自己的查询参数，这里只接管 page/size。
 */
export async function fetchAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<PageLike<T>>,
  options: FetchAllOptions = {},
): Promise<T[]> {
  const pageSize = Math.max(1, Math.min(options.pageSize ?? MAX_PAGE_SIZE, MAX_PAGE_SIZE))
  const maxItems = options.maxItems ?? 5000

  const all: T[] = []
  let page = 0
  let totalPages = 1

  do {
    const data = await fetchPage(page, pageSize)
    all.push(...(data?.content ?? []))
    // 后端返回 totalPages；缺失时用 last 标志兜底，再退化为"本页不满即结束"
    if (typeof data?.totalPages === 'number' && data.totalPages > 0) {
      totalPages = data.totalPages
    } else if (data?.last === true || (data?.content?.length ?? 0) < pageSize) {
      totalPages = page + 1
    } else {
      totalPages = page + 2
    }
    page += 1
  } while (page < totalPages && all.length < maxItems)

  return all.slice(0, maxItems)
}
