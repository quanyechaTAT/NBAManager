import { afterEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import request, { DEFAULT_REQUEST_TIMEOUT_MS } from './request'
import { getToken, setProfile, setToken } from './authStorage'

afterEach(() => localStorage.clear())

describe('request budgets and authentication errors', () => {
  it('uses a bounded ordinary request budget and preserves explicit slow-request overrides', async () => {
    expect(DEFAULT_REQUEST_TIMEOUT_MS).toBe(15_000)
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => ({ data: {}, status: 200, statusText: 'OK', headers: {}, config }))
    await request.get('/teams', { adapter })
    await request.get('/match-detail/game/boxscore', { adapter, timeout: 60_000 })
    expect(adapter.mock.calls[0][0].timeout).toBe(15_000)
    expect(adapter.mock.calls[1][0].timeout).toBe(60_000)
  })

  it('preserves the signed-in account when the server denies an operation with 403', async () => {
    setToken('test-token')
    setProfile({ username: 'user', role: 'USER' })
    await expect(request.get('/admin/overview', {
      adapter: async config => { throw new AxiosError('forbidden', 'ERR_BAD_REQUEST', config, undefined,
        { data: {}, status: 403, statusText: 'Forbidden', headers: {}, config }) },
    })).rejects.toMatchObject({ response: { status: 403 } })
    expect(getToken()).toBe('test-token')
  })

  it('passes cancellation to Axios and does not turn it into an authentication failure', async () => {
    setToken('test-token')
    const controller = new AbortController()
    controller.abort()
    const adapter = vi.fn()
    await expect(request.get('/teams', { signal: controller.signal, adapter })).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(adapter).not.toHaveBeenCalled()
    expect(getToken()).toBe('test-token')
  })

  it('returns an expired admin session to the hash login route even under the legacy login pathname', async () => {
    window.location.href = 'http://localhost/login#/admin/sync'
    setToken('expired-token')
    setProfile({ username: 'admin', role: 'ADMIN' })
    await expect(request.get('/admin/overview', {
      adapter: async config => { throw new AxiosError('unauthorized', 'ERR_BAD_REQUEST', config, undefined,
        { data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config }) },
    })).rejects.toMatchObject({ response: { status: 401 } })
    expect(getToken()).toBeNull()
    expect(window.location.hash).toBe('#/login')
  })
})
