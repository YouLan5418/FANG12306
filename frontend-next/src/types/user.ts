import type { UserRole } from "./common"

export interface LoginReq {
  usernameOrMailOrPhone: string
  password: string
}

export interface LoginResp {
  userId?: string
  username: string
  realName?: string
  accessToken: string
  role?: UserRole | number
}

export interface RegisterReq {
  username: string
  password: string
  realName: string
  idType?: number
  idCard?: string
  phone?: string
  mail?: string
  region?: string
}

export interface CurrentUser {
  id?: string
  username: string
  realName?: string
  idType?: number
  idCard?: string
  phone?: string
  mail?: string
  address?: string
  region?: string
  postCode?: string
  role?: UserRole
}

export interface UserProfile extends CurrentUser {
  id: string
}

export interface UserUpdateReq {
  realName?: string
  phone?: string
  mail?: string
  region?: string
  postCode?: string
  address?: string
}

export interface Passenger {
  id: string
  realName: string
  idType: number
  idCard: string
  discountType: number
  phone: string
  verifyStatus?: number
}
