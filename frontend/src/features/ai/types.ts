/** Một nguồn mà câu trả lời trích dẫn. path là đường dẫn trong ứng dụng để mở đúng báo cáo/task/chat gốc. */
export interface AiSource {
  n: number
  kind: string
  title: string
  path: string | null
  at: string | null
}

export interface AiAnswer {
  text: string
  sources: AiSource[]
}

export interface AiHistoryTurn {
  role: 'user' | 'assistant'
  text: string
}

export type RewriteMode = 'POLISH' | 'FORMAL' | 'SHORTEN' | 'TRANSLATE_VI' | 'TRANSLATE_JA' | 'TRANSLATE_EN'
export const REWRITE_MODES: RewriteMode[] = ['POLISH', 'FORMAL', 'SHORTEN', 'TRANSLATE_VI', 'TRANSLATE_JA', 'TRANSLATE_EN']

export type AiLang = 'vi' | 'ja' | 'en'
