import request from '@/utils/request'

export interface GraphNode {
  id: string
  labels: string[]
  properties: {
    name?: string
    filePath?: string
    startLine?: number
    signature?: string
    [key: string]: any
  }
}

export interface GraphRelationship {
  id: string
  type: string
  startNodeId: string
  endNodeId: string
  properties?: Record<string, any>
}

export interface GraphData {
  nodes: GraphNode[]
  relationships: GraphRelationship[]
}

export interface CallChainResult {
  callers: Array<{
    name: string
    filePath: string
    startLine: number
  }>
  callees: Array<{
    name: string
    filePath: string
    startLine: number
  }>
}

/**
 * Get graph data for a project
 */
export function getProjectGraph(projectId: number, options?: {
  nodeType?: string
  limit?: number
}) {
  const params = new URLSearchParams()
  if (options?.nodeType) params.append('nodeType', options.nodeType)
  if (options?.limit) params.append('limit', options.limit.toString())

  return request.get<any, { success: boolean; data: GraphData }>(
    `/graph/${projectId}?${params.toString()}`
  )
}

/**
 * Get callers of a method
 */
export function getCallers(projectId: number, methodName: string) {
  return request.get<any, { success: boolean; data: CallChainResult }>(
    `/graph/${projectId}/callers`,
    { params: { method: methodName } }
  )
}

/**
 * Get callees of a method
 */
export function getCallees(projectId: number, methodName: string) {
  return request.get<any, { success: boolean; data: CallChainResult }>(
    `/graph/${projectId}/callees`,
    { params: { method: methodName } }
  )
}

/**
 * Search nodes by name
 */
export function searchNodes(projectId: number, query: string, nodeType?: string) {
  const params: Record<string, string> = { q: query }
  if (nodeType) params.type = nodeType

  return request.get<any, { success: boolean; data: GraphNode[] }>(
    `/graph/${projectId}/search`,
    { params }
  )
}

/**
 * Get node detail with relationships
 */
export function getNodeDetail(projectId: number, nodeId: string) {
  return request.get<any, { success: boolean; data: {
    node: GraphNode
    relationships: {
      incoming: GraphRelationship[]
      outgoing: GraphRelationship[]
    }
  } }>(`/graph/${projectId}/node/${nodeId}`)
}

/**
 * Get impact analysis for a method
 */
export function getImpactAnalysis(projectId: number, methodName: string, depth: number = 3) {
  return request.get<any, { success: boolean; data: GraphData }>(
    `/graph/${projectId}/impact`,
    { params: { method: methodName, depth } }
  )
}
