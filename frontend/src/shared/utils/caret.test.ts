import { describe, expect, it } from 'vitest'
import { insertAtCaret } from './caret'

describe('insertAtCaret', () => {
  it('should_insertAtCaret_whenNoSelection', () => {
    expect(insertAtCaret('abcd', 2, 2, '😀', 100)).toEqual({ value: 'ab😀cd', caret: 4 })
  })

  it('should_replaceSelection_whenTextIsSelected', () => {
    expect(insertAtCaret('abcd', 1, 3, '🎉', 100)).toEqual({ value: 'a🎉d', caret: 3 })
  })

  it('should_appendAtEnd_whenCaretUnknown', () => {
    expect(insertAtCaret('ab', null, null, '👍', 100)).toEqual({ value: 'ab👍', caret: 4 })
  })

  it('should_keepValue_whenResultExceedsMax', () => {
    expect(insertAtCaret('abcd', 4, 4, '😀', 5)).toEqual({ value: 'abcd', caret: 4 })
  })
})
