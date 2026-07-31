import request, { unwrap } from "./request"
import type { StationOption, TicketItem, TicketSearchReq } from "@/types/ticket"

export function listStations() {
  return unwrap<StationOption[]>(request.get("/ticket-service/station/all"))
}

export function searchTickets(params: TicketSearchReq) {
  return unwrap<TicketItem[]>(request.get("/ticket-service/ticket/query", { params }))
}
