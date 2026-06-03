import request from '@/utils/request'
import type { Role } from '@/api/types'

export interface LoginBody {
  username: string
  password: string
}

export interface LoginRes {
  token: string
  username: string
  role: Role
}

export interface ChangePasswordBody {
  oldPassword: string
  newPassword: string
}

export interface ChangeUsernameBody {
  newUsername: string
}

export function loginApi(body: LoginBody) {
  return request.post<LoginRes>('/auth/login', body)
}

export function registerApi(body: LoginBody) {
  return request.post<LoginRes>('/auth/register', body)
}

export function changePasswordApi(body: ChangePasswordBody) {
  return request.put('/auth/password', body)
}

export function changeUsernameApi(body: ChangeUsernameBody) {
  return request.put<LoginRes>('/auth/username', body)
}
