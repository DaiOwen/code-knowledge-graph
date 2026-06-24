import request from '@/utils/request'

export interface Project {
  id: number
  name: string
  gitUrl: string
  branch: string
  language: string
  status: string
  createdAt: string
}

export function getProjects() {
  return request.get<any, { success: boolean; data: Project[] }>('/projects')
}

export function getProject(id: number) {
  return request.get<any, { success: boolean; data: Project }>(`/projects/${id}`)
}

export function createProject(data: {
  name: string
  gitUrl: string
  branch?: string
  language?: string
  parseScope?: string
}) {
  return request.post<any, { success: boolean; data: Project }>('/projects', data)
}

export function deleteProject(id: number) {
  return request.delete<any, { success: boolean }>(`/projects/${id}`)
}

export function triggerParse(id: number) {
  return request.post<any, { success: boolean }>(`/projects/${id}/parse`)
}