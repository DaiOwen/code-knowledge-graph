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
 * Get chat sessions list
 * Note: Backend endpoint is GET /qa/sessions (with optional projectId query param)
 */
export function getChatSessions(projectId?: number) {
  const params = projectId ? { projectId } : {}
  return request.get<any, { success: boolean; data: ChatSession[] }>(
    '/qa/sessions',
    { params }
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
    { projectId, title: title || '新对话' }
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

/**
 * Ask in a specific session
 */
export function askInSession(sessionId: number, data: {
  projectId: number
  question: string
}) {
  return request.post<any, { success: boolean; data: QAResponse }>(
    `/qa/sessions/${sessionId}/ask`,
    data
  )
}

/**
 * Stream answer via SSE
 */
export function streamAnswer(data: {
  projectId: number
  question: string
  sessionId?: number
}, callbacks: {
  onToken: (token: string) => void
  onComplete: (response: QAResponse) => void
  onError: (error: Error) => void
}) {
  const eventSource = new EventSource(
    `/api/qa/stream?projectId=${data.projectId}&question=${encodeURIComponent(data.question)}${data.sessionId ? `&sessionId=${data.sessionId}` : ''}`,
    { withCredentials: true }
  )

  eventSource.addEventListener('token', (event) => {
    callbacks.onToken(event.data)
  })

  eventSource.addEventListener('complete', (event) => {
    try {
      const response = JSON.parse(event.data) as QAResponse
      callbacks.onComplete(response)
    } catch (e) {
      callbacks.onError(new Error('Failed to parse response'))
    }
    eventSource.close()
  })

  eventSource.addEventListener('error', (event) => {
    callbacks.onError(new Error(event.data || 'Stream error'))
    eventSource.close()
  })

  eventSource.onerror = () => {
    callbacks.onError(new Error('Connection error'))
    eventSource.close()
  }

  return eventSource
}