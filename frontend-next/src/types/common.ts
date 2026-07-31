export interface ApiResult<T> {
  code: string
  message?: string
  data: T
  success?: boolean
}

export interface PageResult<T> {
  total: number
  current: number
  size: number
  records: T[]
}

export type UserRole = "USER" | "ADMIN"
