import request from '@/utils/request'

export interface LoginResponse {
  token: string
  user: {
    id: number
    username: string
    email: string
    nickname: string
  }
}

export function login(data: { username: string; password: string }) {
  return request.post<any, { success: boolean; data: LoginResponse; message: string }>('/auth/login', data)
}

export function register(data: { username: string; password: string; email?: string }) {
  return request.post<any, { success: boolean; data: LoginResponse; message: string }>('/auth/register', data)
}