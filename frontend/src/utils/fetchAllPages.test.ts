import { describe, expect, it, vi } from 'vitest'
import { MAX_PAGE_SIZE, fetchAllPages } from './fetchAllPages'

describe('fetchAllPages', () => {
  it('walks every page and concatenates content in order', async () => {
    const pages = [
      { content: [1, 2], totalPages: 3 },
      { content: [3, 4], totalPages: 3 },
      { content: [5], totalPages: 3 },
    ]
    const fetchPage = vi.fn((page: number, _size: number) => Promise.resolve(pages[page]))

    const all = await fetchAllPages<number>(fetchPage)

    expect(all).toEqual([1, 2, 3, 4, 5])
    expect(fetchPage).toHaveBeenCalledTimes(3)
    // 每页都不得超过后端上限 100
    for (const call of fetchPage.mock.calls) {
      expect(call[1]).toBeLessThanOrEqual(MAX_PAGE_SIZE)
    }
  })

  it('stops after a single short page when totalPages is absent', async () => {
    const fetchPage = vi.fn(() => Promise.resolve({ content: [1, 2, 3] }))

    const all = await fetchAllPages<number>(fetchPage, { pageSize: 100 })

    expect(all).toEqual([1, 2, 3])
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('honours the maxItems safety bound', async () => {
    const fetchPage = vi.fn((page: number) =>
      Promise.resolve({ content: Array.from({ length: 100 }, (_, i) => page * 100 + i), totalPages: 100 }),
    )

    const all = await fetchAllPages<number>(fetchPage, { pageSize: 100, maxItems: 250 })

    expect(all).toHaveLength(250)
    expect(fetchPage).toHaveBeenCalledTimes(3)
  })

  it('never requests more than the backend page-size ceiling', async () => {
    const fetchPage = vi.fn(() => Promise.resolve({ content: [], totalPages: 1 }))

    await fetchAllPages<number>(fetchPage, { pageSize: 5000 })

    expect(fetchPage).toHaveBeenCalledWith(0, MAX_PAGE_SIZE)
  })

  it('treats an empty first page as complete', async () => {
    const fetchPage = vi.fn(() => Promise.resolve({ content: [], totalPages: 1 }))

    await expect(fetchAllPages<number>(fetchPage)).resolves.toEqual([])
  })
})
