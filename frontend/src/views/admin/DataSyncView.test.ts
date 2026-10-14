import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DataSyncView from './DataSyncView.vue'
import * as api from '@/api/dataManagement'
import { publishDataRefresh } from '@/utils/dataRefresh'
import type { SyncJobResponse, SyncJobStepResponse, SyncOverviewResponse } from '@/api/dataManagement'

vi.mock('@/api/dataManagement', () => ({
  getSyncOverview: vi.fn(), getRecentJobs: vi.fn(), getJobHistory: vi.fn(),
  getJob: vi.fn(), getJobDetail: vi.fn(), syncAllModules: vi.fn(),
  syncModule: vi.fn(), cancelJob: vi.fn(),
}))
vi.mock('@/utils/dataRefresh', () => ({ publishDataRefresh: vi.fn() }))

const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }
function job(module: string, status = 'RUNNING', jobId = 1): SyncJobResponse {
  return { jobId, module, status, progress: status === 'SUCCESS' ? 100 : 30,
    triggerType: 'MANUAL', stage: null, requestedBy: 'admin', createdAt: '2026-10-05T12:00:00',
    startedAt: null, finishedAt: null, heartbeatAt: null, dataAsOf: null, source: null,
    fetchedCount: 0, insertedCount: 0, updatedCount: 0, skippedCount: 0,
    errorCode: null, errorMessage: null, cancelRequested: false, reused: false }
}
function snapshot(jobs: SyncJobResponse[] = []): SyncOverviewResponse {
  return { currentSeason: '2026-27', activeFullJob: null, latestFullJob: null,
    modules: ['TEAMS', 'PLAYERS'].map(key => {
      const latestJob = jobs.find(j => j.module === key) ?? null
      return { key, count: 30, latestJob, activeJob: latestJob && ['RUNNING', 'QUEUED', 'CANCELLING'].includes(latestJob.status) ? latestJob : null,
        activeFullStep: null, latestFullStep: null, lastSuccessfulAt: null }
    }) }
}
function step(module: string, status: string, id: number): SyncJobStepResponse {
  return { id, module, status, stepOrder: id - 1, progress: 100, stage: null,
    startedAt: null, finishedAt: null, fetchedCount: 0, insertedCount: 0, updatedCount: 0,
    skippedCount: 0, errorCode: null, errorMessage: status === 'FAILED' ? 'upstream failed' : null }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(r => { resolve = r })
  return { promise, resolve }
}
let wrapper: ReturnType<typeof mount> | undefined
beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
  vi.useFakeTimers()
  vi.resetAllMocks()
  vi.mocked(api.getSyncOverview).mockResolvedValue({ data: snapshot() } as any)
  vi.mocked(api.getRecentJobs).mockResolvedValue({ data: emptyPage } as any)
  vi.mocked(api.getJobHistory).mockResolvedValue({ data: emptyPage } as any)
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.useRealTimers(); vi.restoreAllMocks() })

describe('DataSyncView task lifecycle', () => {
  it('pauses polling while hidden and refreshes immediately on return', async () => {
    wrapper = mount(DataSyncView)
    await flushPromises()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.getSyncOverview).toHaveBeenCalledTimes(1)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(api.getSyncOverview).toHaveBeenCalledTimes(2)
    expect(api.getJobHistory).toHaveBeenCalledTimes(2)
    wrapper.unmount(); wrapper = undefined
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.getSyncOverview).toHaveBeenCalledTimes(2)
  })

  it('aborts a hidden overview read and ignores its late result after resuming', async () => {
    const pending = deferred<any>()
    vi.mocked(api.getSyncOverview).mockReturnValueOnce(pending.promise)
    wrapper = mount(DataSyncView)
    const options = vi.mocked(api.getSyncOverview).mock.calls[0][0]!
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(options.signal?.aborted).toBe(true)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    pending.resolve({ data: snapshot([job('TEAMS', 'RUNNING', 999)]) })
    await flushPromises()
    expect(api.getSyncOverview).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.global-progress').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('状态刷新失败')
  })

  it('defers initial reads until a hidden page becomes visible', async () => {
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    wrapper = mount(DataSyncView)
    await flushPromises()
    expect(api.getSyncOverview).not.toHaveBeenCalled()
    expect(api.getJobHistory).not.toHaveBeenCalled()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(api.getSyncOverview).toHaveBeenCalledTimes(1)
    expect(api.getJobHistory).toHaveBeenCalledTimes(1)
  })

  it('prevents duplicate full sync submissions while the server is accepting the first one', async () => {
    vi.mocked(api.syncAllModules).mockReturnValue(new Promise(() => {}))
    wrapper = mount(DataSyncView)
    await flushPromises()
    const button = wrapper.findAll('button').find(b => b.text().includes('一键全量同步'))!
    await button.trigger('click')
    await button.trigger('click')
    expect(api.syncAllModules).toHaveBeenCalledTimes(1)
  })

  it('continues discovering scheduled tasks while the page is idle', async () => {
    wrapper = mount(DataSyncView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(15000)
    expect(vi.mocked(api.getSyncOverview).mock.calls.length).toBeGreaterThan(1)
  })

  it('discovers an external active full job from the overview without searching a limited history page', async () => {
    const state = snapshot()
    state.activeFullJob = job('FULL_SYNC', 'QUEUED', 90)
    vi.mocked(api.getSyncOverview).mockResolvedValue({ data: state } as any)
    wrapper = mount(DataSyncView)
    await flushPromises()
    expect(wrapper.get('.global-progress').text()).toContain('全量同步')
    expect(api.getRecentJobs).not.toHaveBeenCalled()
    expect(wrapper.findAll('.module-card').every(card => card.text().includes('等待中'))).toBe(true)
  })

  it('keeps real task state when a submission fails instead of inventing a failed job', async () => {
    vi.mocked(api.syncModule).mockRejectedValue(new Error('offline'))
    wrapper = mount(DataSyncView)
    await flushPromises()
    await wrapper.findAll('.module-card')[0].get('.btn-sm-sync').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.module-card')[0].text()).toContain('待同步')
    expect(wrapper.get('.log-panel').text()).toContain('提交失败')
    expect(wrapper.find('.failure-alert').exists()).toBe(false)
  })

  it('cancels each job once and applies terminal responses when cancellation was too late', async () => {
    vi.mocked(api.getSyncOverview).mockResolvedValueOnce({ data: snapshot([job('TEAMS')]) } as any)
    const pending = deferred<any>()
    vi.mocked(api.cancelJob).mockReturnValue(pending.promise)
    wrapper = mount(DataSyncView)
    await flushPromises()
    const cancel = wrapper.get('.btn-cancel')
    await cancel.trigger('click'); await cancel.trigger('click')
    expect(api.cancelJob).toHaveBeenCalledTimes(1)
    pending.resolve({ data: { accepted: false, job: job('TEAMS', 'SUCCESS') } })
    await flushPromises()
    expect(wrapper.find('.global-progress').exists()).toBe(false)
    expect(wrapper.get('.log-panel').text()).toContain('无需取消')
    expect(wrapper.findAll('.module-card').some(card => card.text().includes('取消中'))).toBe(false)
  })

  it('retains full-sync step failures and retries all failed modules independently', async () => {
    const state = snapshot()
    const steps = [step('TEAMS', 'FAILED', 1), step('PLAYERS', 'FAILED', 2)]
    state.latestFullJob = { ...job('FULL_SYNC', 'FAILED', 90), steps }
    state.modules.forEach((m, index) => { m.latestFullStep = steps[index] })
    vi.mocked(api.getSyncOverview).mockImplementation(async () => ({ data: state } as any))
    vi.mocked(api.syncModule).mockImplementation(async module => {
      const result = job(module, 'SUCCESS', module === 'TEAMS' ? 101 : 102)
      const item = state.modules.find(m => m.key === module)!
      item.latestFullStep = null; item.latestJob = result
      return { data: result } as any
    })
    wrapper = mount(DataSyncView)
    await flushPromises()
    expect(wrapper.get('.failure-alert').text()).toContain('2 个模块')
    await wrapper.get('.btn-sm-retry').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.syncModule).mock.calls.map(args => args[0])).toEqual(['TEAMS', 'PLAYERS'])
    expect(api.syncAllModules).not.toHaveBeenCalled()
  })

  it('does not carry a previous module failure into a successful full-sync step', async () => {
    const oldFailure = { ...job('TEAMS', 'FAILED', 1), errorMessage: 'old proxy failure' }
    const state = snapshot([oldFailure])
    state.activeFullJob = job('FULL_SYNC', 'RUNNING', 90)
    state.modules[0].activeFullStep = step('TEAMS', 'SUCCESS', 1)
    vi.mocked(api.getSyncOverview).mockResolvedValue({ data: state } as any)
    wrapper = mount(DataSyncView)
    await flushPromises()
    const modules = (wrapper.vm as unknown as { modules: Array<{ key: string; errorMessage: string }> }).modules
    expect(modules.find(module => module.key === 'TEAMS')?.errorMessage).toBe('')
    expect(wrapper.find('.failure-alert').exists()).toBe(false)
  })

  it('publishes refresh signals for successful steps when a full sync finishes', async () => {
    const running = snapshot()
    running.activeFullJob = job('FULL_SYNC', 'RUNNING', 90)
    const finished = snapshot()
    finished.latestFullJob = { ...job('FULL_SYNC', 'PARTIAL_FAILED', 90),
      steps: [step('TEAMS', 'SUCCESS', 1), step('PLAYERS', 'FAILED', 2)] }
    vi.mocked(api.getSyncOverview).mockResolvedValueOnce({ data: running } as any)
      .mockResolvedValue({ data: finished } as any)
    wrapper = mount(DataSyncView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(3000)
    expect(publishDataRefresh).toHaveBeenCalledWith({ module: 'TEAMS', jobId: 90, status: 'SUCCESS' })
    expect(publishDataRefresh).toHaveBeenCalledTimes(1)
  })

  it('keeps the last active state across network errors and resumes polling', async () => {
    vi.mocked(api.getSyncOverview).mockResolvedValueOnce({ data: snapshot([job('TEAMS')]) } as any)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ data: snapshot([job('TEAMS', 'SUCCESS')]) } as any)
    wrapper = mount(DataSyncView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(3000)
    expect(wrapper.get('.failure-alert').text()).toContain('状态刷新失败')
    expect(wrapper.findAll('.module-card')[0].text()).toContain('同步中')
    await vi.advanceTimersByTimeAsync(3000)
    expect(wrapper.find('.failure-alert').exists()).toBe(false)
    expect(wrapper.findAll('.module-card')[0].text()).toContain('已完成')
  })

  it('ignores old history responses after a newer filter query has completed', async () => {
    wrapper = mount(DataSyncView)
    await flushPromises()
    const old = deferred<any>()
    vi.mocked(api.getJobHistory).mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ data: { ...emptyPage, content: [job('PLAYERS', 'SUCCESS', 2)] } } as any)
    await wrapper.get('.history-query').trigger('click')
    await wrapper.get('[aria-label="模块筛选"]').setValue('PLAYERS')
    await wrapper.get('.history-query').trigger('click')
    await flushPromises()
    old.resolve({ data: { ...emptyPage, content: [job('TEAMS', 'FAILED', 1)] } })
    await flushPromises()
    expect(wrapper.get('.history-table').text()).toContain('#2')
    expect(wrapper.get('.history-table').text()).not.toContain('#1')
  })

  it('aborts in-flight reads and never restarts polling after leaving the page', async () => {
    vi.mocked(api.getSyncOverview).mockResolvedValueOnce({ data: snapshot([job('TEAMS')]) } as any)
    const pending = deferred<any>()
    vi.mocked(api.getSyncOverview).mockReturnValueOnce(pending.promise)
    wrapper = mount(DataSyncView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(3000)
    const options = vi.mocked(api.getSyncOverview).mock.calls[1][0]!
    wrapper.unmount(); wrapper = undefined
    expect(options.signal?.aborted).toBe(true)
    pending.resolve({ data: snapshot([job('TEAMS')]) })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.getSyncOverview).toHaveBeenCalledTimes(2)
  })
})
