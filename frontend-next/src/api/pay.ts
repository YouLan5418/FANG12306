import request, { unwrap } from "./request"
import type { PayCreateReq, PayInfo, PayResp, RefundReq, RefundResp } from "@/types/pay"

export function createPay(data: PayCreateReq) {
  return unwrap<PayResp>(request.post("/pay-service/pay/create", data))
}

export function queryPayByOrderSn(orderSn: string) {
  return unwrap<PayInfo>(request.get("/pay-service/pay/query/order-sn", { params: { orderSn } }))
}

export function mockPaySuccess(orderSn: string, payAmount: number) {
  return request.post<string>("/pay-service/callback/alipay", {
    orderSn,
    tradeNo: `MOCK_${Date.now()}`,
    status: "TRADE_SUCCESS",
    payAmount,
    gmtPayment: new Date().toISOString()
  })
}

export function refund(data: RefundReq) {
  return unwrap<RefundResp>(request.post("/pay-service/common/refund", data))
}
