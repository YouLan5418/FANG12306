export type PayChannel = "ALI_PAY" | "WECHAT_PAY"

export interface PayCreateReq {
  orderSn: string
  totalAmount: number
  channel: PayChannel
}

export interface PayResp {
  paySn: string
  payUrl?: string
  orderSn: string
  totalAmount: number
  status: string
}

export interface PayInfo {
  paySn: string
  orderSn: string
  totalAmount: number
  payAmount?: number
  status: string
  gmtPayment?: string
  channel?: PayChannel
  tradeType?: string
}

export interface RefundDetailReq {
  carriageNumber: string
  seatNumber: string
  seatType?: number
  realName?: string
  idType?: number
  idCard?: string
}

export interface RefundReq {
  orderSn: string
  refundType: 11 | 12
  refundAmount?: number
  refundDetailReqDTOList?: RefundDetailReq[]
}

export interface RefundResp {
  refundSn: string
  orderSn: string
  refundAmount: number
  status: string
}
