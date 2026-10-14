export interface StationOption {
  id?: number
  code: string
  name: string
  spell?: string
  region?: string
  regionName?: string
}

export interface TicketSearchReq {
  departureDate?: string
  departure: string
  arrival: string
  current?: number
  size?: number
}

export interface SeatTypeStock {
  seatType: number
  price: number
  quantity: number
}

export interface TicketItem {
  trainId: string
  trainNumber: string
  trainType: number
  departure: string
  arrival: string
  departureTime: string
  arrivalTime: string
  seatClassList: SeatTypeStock[]
}

export interface TicketPageResp {
  trainList: TicketItem[]
  current: number
  size: number
  total: number
}

export interface PurchasePassenger {
  passengerId: string
  seatType: number
  ticketType?: number
}

export interface PurchaseTicketReq {
  trainId: string
  departure: string
  arrival: string
  passengers: PurchasePassenger[]
  chooseSeats?: string[]
}

export interface PurchaseTicketDetail {
  passengerId: string
  carriageNumber: string
  seatNumber: string
  seatType: number
  amount: number
  idType: number
  idCard: string
  phone: string
  realName: string
  ticketType: number
}

export interface PurchaseTicketResp {
  orderSn: string
  ticketOrderDetailResults: PurchaseTicketDetail[]
}
