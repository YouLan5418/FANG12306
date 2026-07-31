import request, { unwrap } from "./request"
import type { PageResult } from "@/types/common"
import type { OrderDetail, OrderPageReq, SelfOrderRow } from "@/types/order"

export function querySelfOrders(params: OrderPageReq) {
  return unwrap<PageResult<SelfOrderRow>>(
    request.get("/order-service/order/ticket/self/page", { params })
  )
}

export function queryOrderDetail(orderSn: string) {
  return unwrap<OrderDetail>(request.get("/order-service/order/ticket/query", { params: { orderSn } }))
}
