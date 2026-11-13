// Gọi API trợ lý AI. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { AiAnswer, AiHistoryTurn, AiLang, RewriteMode } from './types'

/** Mô hình có thể mất vài chục giây để trả lời nên các lời gọi này chờ lâu hơn mặc định. */
const SLOW = { timeout: 90_000 }

export async function getStatus(): Promise<boolean> {
  const { data } = await http.get<ApiSuccess<{ enabled: boolean }>>('/ai/status')
  return data.data.enabled
}

export async function ask(question: string, history: AiHistoryTurn[], lang: AiLang): Promise<AiAnswer> {
  const { data } = await http.post<ApiSuccess<AiAnswer>>('/ai/ask', { question, history, lang }, SLOW)
  return data.data
}

export async function summarizeCommunity(communityId: number, days: number, lang: AiLang): Promise<AiAnswer> {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - (days - 1))
  const day = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  const { data } = await http.post<ApiSuccess<AiAnswer>>(
    '/ai/summaries/community',
    { communityId, from: day(from), to: day(to), lang },
    SLOW,
  )
  return data.data
}

export async function summarizeChat(conversationId: number, hours: number, lang: AiLang): Promise<AiAnswer> {
  const { data } = await http.post<ApiSuccess<AiAnswer>>('/ai/summaries/chat', { conversationId, hours, lang }, SLOW)
  return data.data
}

export async function draftReport(lang: AiLang): Promise<AiAnswer> {
  const { data } = await http.post<ApiSuccess<AiAnswer>>('/ai/assist/draft-report', { lang }, SLOW)
  return data.data
}

export async function rewrite(text: string, mode: RewriteMode, lang: AiLang): Promise<AiAnswer> {
  const { data } = await http.post<ApiSuccess<AiAnswer>>('/ai/assist/rewrite', { text, mode, lang }, SLOW)
  return data.data
}

export async function plan(days: number, lang: AiLang): Promise<AiAnswer> {
  const { data } = await http.post<ApiSuccess<AiAnswer>>('/ai/plan', { days, lang }, SLOW)
  return data.data
}
