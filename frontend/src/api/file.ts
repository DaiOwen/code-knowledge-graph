import request from '@/utils/request'

export interface FileInfo {
  path: string
  language: string
  totalLines: number
  content?: string
}

export interface FileContentResult {
  path: string
  language: string
  totalLines: number
  content: string
  startLine: number
  endLine: number
}

/**
 * Get file content
 */
export function getFileContent(projectId: number, filePath: string, options?: {
  startLine?: number
  endLine?: number
  ref?: string
}) {
  const params = new URLSearchParams()
  params.append('path', filePath)
  if (options?.startLine) params.append('startLine', options.startLine.toString())
  if (options?.endLine) params.append('endLine', options.endLine.toString())
  if (options?.ref) params.append('ref', options.ref)

  return request.get<any, { success: boolean; data: FileContentResult }>(
    `/files/${projectId}?${params.toString()}`
  )
}

/**
 * Get file tree
 */
export function getFileTree(projectId: number) {
  return request.get<any, { success: boolean; data: FileInfo[] }>(
    `/files/${projectId}/tree`
  )
}

/**
 * Search files by name
 */
export function searchFiles(projectId: number, query: string) {
  return request.get<any, { success: boolean; data: FileInfo[] }>(
    `/files/${projectId}/search`,
    { params: { q: query } }
  )
}
