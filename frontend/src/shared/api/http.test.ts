import { describe, expect, it } from 'vitest'
import { http } from './http'

describe('http', () => {
  it('should_useApiV1BaseUrl_when_instanceCreated', () => {
    expect(http.defaults.baseURL).toBe('/api/v1')
  })
})
