import request from '@/utils/request'

export interface QAResponse {
  answer: string
  sessionId?: number
  citations: Array<{
    filePath: string
    line: number | null
    snippet?: string
  }>
}

export interface ChatSession {
  id: number
  projectId: number
  title: string
  createdAt: string
}

export interface ChatMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  citations?: string
  createdAt: string
}

/**
 * Ask a question
 */
export function askQuestion(data: {
  projectId: number
  question: string
  sessionId?: number
}) {
  return request.post<any, { success: boolean; data: QAResponse }>('/qa/ask', data)
}

/**
 * Get chat sessions for a project
 */
export function getChatSessions(projectId: number) {
  return request.get<any, { success: boolean; data: ChatSession[] }>(
    `/qa/sessions/${projectId}`
  )
}

/**
 * Get messages for a session
 */
export function getSessionMessages(sessionId: number) {
  return request.get<any, { success: boolean; data: ChatMessage[] }>(
    `/qa/sessions/${sessionId}/messages`
  )
}

/**
 * Create a new session
 */
export function createSession(projectId: number, title?: string) {
  return request.post<any, { success: boolean; data: ChatSession }>(
    `/qa/sessions`,
    { projectId, title }
  )
}

/**
 * Delete a session
 */
export function deleteSession(sessionId: number) {
  return request.delete<any, { success: boolean }>(
    `/qa/sessions/${sessionId}`
  )
}