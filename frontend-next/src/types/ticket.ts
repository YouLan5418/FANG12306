export interface StationOption {
  code: string
  name: string
}

export interface TicketSearchReq {
  fromStation: string
  toStation: string
  departureDate: string
}

export interface SeatTypeStock {
  seatType: string
  seatTypeName: string
  price: number
  remaining: number
}

export interface TicketItem {
  trainId: string
  trainNumber: string
  fromStation: string
  toStation: string
  departureTime: string
  arrivalTime: string
  duration: string
  seatTypes: SeatTypeStock[]
}
