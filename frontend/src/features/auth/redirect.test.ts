import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it.each([
    ['/users', '/users'],
    ['/teams/3/tasks?status=ASSIGNED', '/teams/3/tasks?status=ASSIGNED'],
    ['/', '/'],
  ])('should_keepInternalPath_when_%s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected)
  })

  it.each([
    ['//evil.com'],
    ['https://evil.com'],
    ['javascript:alert(1)'],
    ['/\\evil.com'],
    [''],
    [undefined],
    [null],
    [['/a', '/b']],
  ])('should_fallBackToHome_when_%s', (input) => {
    expect(safeRedirect(input)).toBe('/')
  })
})
