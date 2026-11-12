import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { http, registerAuthHandlers, toApiError } from './http'
import { ApiError } from '../types/api'

interface FakeResult {
  status: number
  data?: unknown
}

type Responder = (config: InternalAxiosRequestConfig) => FakeResult | 'network-error'

/** Thay adapter của axios bằng hàm giả: không cần mạng, kiểm tra được request thật sự gửi đi. */
function fakeServer(responder: Responder): InternalAxiosRequestConfig[] {
  const calls: InternalAxiosRequestConfig[] = []
  http.defaults.adapter = async (config) => {
    calls.push(config)
    const result = responder(config)
    if (result === 'network-error') {
      throw new AxiosError('Network Error', 'ERR_NETWORK', config)
    }
    const response = { data: result.data, status: result.status, statusText: '', headers: {}, config }
    if (result.status >= 200 && result.status < 300) {
      return response
    }
    throw new AxiosError('failed', 'ERR_BAD_REQUEST', config, null, response)
  }
  return calls
}

const errorBody = (code: string) => ({ success: false, code, message: code, timestamp: 'now' })

describe('http', () => {
  const originalAdapter = http.defaults.adapter

  beforeEach(() => {
    registerAuthHandlers(null)
  })

  afterEach(() => {
    http.defaults.adapter = originalAdapter
    registerAuthHandlers(null)
  })

  it('should_useApiV1BaseUrl_when_instanceCreated', () => {
    expect(http.defaults.baseURL).toBe('/api/v1')
  })

  it('should_attachBearerToken_when_handlerHasToken', async () => {
    const calls = fakeServer(() => ({ status: 200, data: {} }))
    registerAuthHandlers({ getAccessToken: () => 'abc', refreshTokens: vi.fn(), onSessionExpired: vi.fn() })

    await http.get('/me')

    expect(calls[0].headers.Authorization).toBe('Bearer abc')
  })

  it('should_notAttachAuthorization_when_noToken', async () => {
    const calls = fakeServer(() => ({ status: 200, data: {} }))
    registerAuthHandlers({ getAccessToken: () => null, refreshTokens: vi.fn(), onSessionExpired: vi.fn() })

    await http.get('/me')

    expect(calls[0].headers.Authorization).toBeUndefined()
  })

  it('should_refreshAndRetryOnce_when_protectedEndpointReturns401', async () => {
    let token = 'old'
    const refreshTokens = vi.fn(async () => {
      token = 'new'
    })
    registerAuthHandlers({ getAccessToken: () => token, refreshTokens, onSessionExpired: vi.fn() })
    const calls = fakeServer((config) =>
      config.headers.Authorization === 'Bearer new'
        ? { status: 200, data: { ok: true } }
        : { status: 401, data: errorBody('TOKEN_EXPIRED') },
    )

    const response = await http.get('/me')

    expect(response.data).toEqual({ ok: true })
    expect(refreshTokens).toHaveBeenCalledTimes(1)
    expect(calls).toHaveLength(2)
  })

  it('should_notRefresh_when_loginReturns401', async () => {
    const refreshTokens = vi.fn()
    registerAuthHandlers({ getAccessToken: () => null, refreshTokens, onSessionExpired: vi.fn() })
    fakeServer(() => ({ status: 401, data: errorBody('INVALID_CREDENTIALS') }))

    await expect(http.post('/auth/login', {})).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS', status: 401 })
    expect(refreshTokens).not.toHaveBeenCalled()
  })

  it('should_expireSession_when_refreshRejectedWith401', async () => {
    const onSessionExpired = vi.fn()
    registerAuthHandlers({
      getAccessToken: () => 'old',
      refreshTokens: vi.fn().mockRejectedValue(new ApiError('UNAUTHORIZED', 401, 'x')),
      onSessionExpired,
    })
    fakeServer(() => ({ status: 401, data: errorBody('TOKEN_EXPIRED') }))

    await expect(http.get('/me')).rejects.toBeInstanceOf(ApiError)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('should_expireSession_when_refreshRejectedBecauseAccountLocked', async () => {
    const onSessionExpired = vi.fn()
    registerAuthHandlers({
      getAccessToken: () => 'old',
      refreshTokens: vi.fn().mockRejectedValue(new ApiError('ACCOUNT_LOCKED', 403, 'x')),
      onSessionExpired,
    })
    fakeServer(() => ({ status: 401, data: errorBody('UNAUTHORIZED') }))

    await expect(http.get('/me')).rejects.toMatchObject({ code: 'ACCOUNT_LOCKED' })
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('should_keepSession_when_refreshFailsOnlyBecauseOfNetwork', async () => {
    const onSessionExpired = vi.fn()
    registerAuthHandlers({
      getAccessToken: () => 'old',
      refreshTokens: vi.fn().mockRejectedValue(new ApiError('NETWORK_ERROR', 0, 'offline')),
      onSessionExpired,
    })
    fakeServer(() => ({ status: 401, data: errorBody('TOKEN_EXPIRED') }))

    await expect(http.get('/me')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('should_notLogOut_when_retriedRequestIsForbidden', async () => {
    const onSessionExpired = vi.fn()
    let token = 'old'
    registerAuthHandlers({
      getAccessToken: () => token,
      refreshTokens: async () => {
        token = 'new'
      },
      onSessionExpired,
    })
    fakeServer((config) =>
      config.headers.Authorization === 'Bearer new'
        ? { status: 403, data: errorBody('FORBIDDEN') }
        : { status: 401, data: errorBody('TOKEN_EXPIRED') },
    )

    await expect(http.get('/users')).rejects.toMatchObject({ code: 'FORBIDDEN', status: 403 })
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('should_notLoop_when_retryStillReturns401', async () => {
    const refreshTokens = vi.fn().mockResolvedValue(undefined)
    registerAuthHandlers({ getAccessToken: () => 't', refreshTokens, onSessionExpired: vi.fn() })
    const calls = fakeServer(() => ({ status: 401, data: errorBody('UNAUTHORIZED') }))

    await expect(http.get('/me')).rejects.toMatchObject({ status: 401 })
    expect(refreshTokens).toHaveBeenCalledTimes(1)
    expect(calls).toHaveLength(2)
  })

  it('should_mapToNetworkError_when_noResponse', async () => {
    fakeServer(() => 'network-error')

    await expect(http.get('/me')).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 })
  })

  it('should_carryFieldDetails_when_validationFails', async () => {
    fakeServer(() => ({
      status: 422,
      data: { ...errorBody('VALIDATION_ERROR'), details: [{ field: 'email', message: 'invalid' }] },
    }))

    const error = (await http.post('/users', {}).catch((e: unknown) => e)) as ApiError
    expect(error.code).toBe('VALIDATION_ERROR')
    expect(error.details).toEqual([{ field: 'email', message: 'invalid' }])
  })

  it('should_returnUnknown_when_errorIsNotAnAxiosError', () => {
    expect(toApiError(new Error('boom'))).toMatchObject({ code: 'UNKNOWN', message: 'boom' })
  })
})
