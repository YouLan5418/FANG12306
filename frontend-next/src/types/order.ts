export type OrderStatus = 0 | 10 | 11 | 12 | 20 | 30

export interface OrderPassengerDetail {
  seatNumber: string
  carriageNumber: string
  seatType: number
  realName: string
  idType: number
  idCard: string
  phone: string
  amount: number
  ticketType: number
  status: number
}

export interface OrderDetail {
  orderSn: string
  userId?: number
  trainNumber: string
  departure: string
  arrival: string
  status: OrderStatus
  departureTime?: string
  arrivalTime?: string
  ridingDate?: string
  orderTime?: string
  payTime?: string
  passengerDetails?: OrderPassengerDetail[]
}

export interface SelfOrderRow {
  orderSn: string
  trainNumber: string
  departure: string
  arrival: string
  status: OrderStatus
  ridingDate?: string
  seatNumber: string
  carriageNumber: string
  seatType: number
  amount: number
}

export interface OrderPageReq {
  current: number
  size: number
  statusType?: number
}
