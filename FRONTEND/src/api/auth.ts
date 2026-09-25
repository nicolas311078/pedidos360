import { api } from './client'

export type Role = 'CLIENTE' | 'OPERADOR' | 'ADMIN'

export interface UserProfile {
  id: number
  username: string
  email: string
  role: Role
}

export interface AuthResponse {
  token: string
}

export const login = (username: string, password: string) =>
  api.post<AuthResponse>('/api/auth/login', { username, password }).then((r) => r.data)

export const register = (username: string, email: string, password: string) =>
  api.post<AuthResponse>('/api/auth/register', { username, email, password }).then((r) => r.data)

export const fetchProfile = () => api.get<UserProfile>('/api/users/profile').then((r) => r.data)

export const fetchUsers = () => api.get<UserProfile[]>('/api/users').then((r) => r.data)