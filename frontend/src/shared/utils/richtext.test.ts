import { describe, expect, it } from 'vitest'
import { sanitizeForDisplay } from './richtext'

describe('sanitizeForDisplay', () => {
  it('should_removeScriptAndHandlers_when_htmlIsHostile', () => {
    const out = sanitizeForDisplay('<p onclick="x()">hi</p><script>alert(1)</script><img src=x onerror=alert(1)>')
    expect(out).toBe('<p>hi</p>')
  })

  it('should_openLinksInNewTab_when_linkPresent', () => {
    const out = sanitizeForDisplay('<a href="https://example.com">ok</a>')
    expect(out).toContain('target="_blank"')
    expect(out).toContain('noopener')
  })

  it('should_dropJavascriptLinks_when_protocolDangerous', () => {
    expect(sanitizeForDisplay('<a href="javascript:alert(1)">x</a>')).not.toContain('javascript')
  })

  it('should_keepToolbarFormatting_when_tagsAllowed', () => {
    const html = '<h1>A</h1><h2>B</h2><h3>C</h3><p><strong>đậm</strong> <em>nghiêng</em></p><ul><li>x</li></ul>'
    expect(sanitizeForDisplay(html)).toBe(html)
  })
})
