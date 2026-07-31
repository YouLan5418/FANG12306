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

export interface AdminTrend {
  labels: string[]
  values: number[]
}

export interface AdminOrderStats {
  total?: number
  todayOrderCount?: number
  byStatus?: Record<string, number>
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

export interface TrainStationDetail {
  id?: number
  trainId?: number
  stationId?: number
  sequence?: string
  departure?: string
  arrival?: string
  startRegion?: string
  endRegion?: string
  arrivalTime?: string
  departureTime?: string
  stopoverTime?: number
}

export interface TrainRelationDetail {
  departure?: string
  arrival?: string
  startRegion?: string
  endRegion?: string
  departureTime?: string
  arrivalTime?: string
}

export interface TrainPriceDetail {
  departure?: string
  arrival?: string
  seatType: number
  price: number
}

export interface AdminTrainDetail {
  train: AdminTrain
  stations: TrainStationDetail[]
  relations: TrainRelationDetail[]
  prices: TrainPriceDetail[]
}

export interface TrainPublishStation {
  stationId?: number
  stationName: string
  region?: string
  arrivalTime: string
  departureTime: string
  stopoverTime: number
}

export interface TrainPublishSeatPrice {
  seatType: number
  price: number
}

export interface TrainPublishCarriage {
  carriageNumber: string
  carriageType: number
  seatType: number
  seatCount: number
}

export interface TrainPublishReq {
  trainNumber: string
  trainType: number
  trainTag?: string
  trainBrand?: string
  saleTime: string
  saleStatus: number
  stations: TrainPublishStation[]
  seatPrices: TrainPublishSeatPrice[]
  carriages: TrainPublishCarriage[]
}

export interface OperationTask {
  taskName: string
  scannedCount: number
  handledCount: number
  details?: Record<string, unknown>
}

export type AdminPage<T> = PageResult<T>
