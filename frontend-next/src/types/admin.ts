import type { PageResult } from "./common"

export interface AdminDashboard {
  userCount?: number
  orderCount?: number
  trainCount?: number
  ticketCount?: number
  todayOrderCount?: number
  revenueAmount?: number
  refundAmount?: number
  [key: string]: unknown
}

export interface AdminUser {
  id: number
  username: string
  realName?: string
  region?: string
  userType?: number
  verifyStatus?: number
  phone?: string
  mail?: string
  idCard?: string
  createTime?: string
}

export interface AdminOrder {
  id: number
  orderSn: string
  userId: number
  username: string
  trainNumber: string
  departure: string
  arrival: string
  ridingDate?: string
  departureTime?: string
  arrivalTime?: string
  status: number
  orderTime?: string
  payTime?: string
}

export interface AdminTrain {
  id: number
  trainNumber: string
  trainType?: number
  trainTag?: string
  trainBrand?: string
  startStation?: string
  endStation?: string
  startRegion?: string
  endRegion?: string
  saleTime?: string
  saleStatus?: number
  departureTime?: string
  arrivalTime?: string
}

export interface OperationTask {
  taskName: string
  scannedCount: number
  handledCount: number
  details?: Record<string, unknown>
}

export type AdminPage<T> = PageResult<T>
