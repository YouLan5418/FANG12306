import type { UserRole } from "./common"

export interface LoginReq {
  username: string
  password: string
}

export interface LoginResp {
  username: string
  realName?: string
  token: string
  role?: UserRole
}

export interface RegisterReq {
  username: string
  password: string
  realName: string
  phone?: string
  mail?: string
}

export interface CurrentUser {
  username: string
  realName?: string
  role: UserRole
}
