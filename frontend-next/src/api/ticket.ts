import request, { unwrap } from "./request"
import type {
  PurchaseTicketReq,
  PurchaseTicketResp,
  StationOption,
  TicketPageResp,
  TicketSearchReq
} from "@/types/ticket"

export function listStations() {
  return unwrap<StationOption[]>(request.get("/ticket-service/station/all"))
}

export function searchTickets(params: TicketSearchReq) {
  return unwrap<TicketPageResp>(request.get("/ticket-service/ticket/query", { params }))
}

export function purchaseTicket(data: PurchaseTicketReq) {
  return unwrap<PurchaseTicketResp>(request.post("/ticket-service/ticket/purchase", data))
}

export function cancelTicket(orderSn: string) {
  return unwrap<void>(request.post("/ticket-service/ticket/cancel", { orderSn }))
}
