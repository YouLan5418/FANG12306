import request, { unwrap } from "./request"
import type {
  AdminDashboard,
  AdminOrder,
  AdminPage,
  AdminTrain,
  AdminUser,
  OperationTask
} from "@/types/admin"

export function getAdminDashboard() {
  return unwrap<AdminDashboard>(request.get("/admin/dashboard"))
}

export function getOrderTrend(days = 7) {
  return unwrap<Record<string, unknown>>(request.get("/admin/stats/order-trend", { params: { days } }))
}

export function listAdminUsers(params: { page: number; size: number; keyword?: string }) {
  return unwrap<AdminPage<AdminUser>>(request.get("/admin/user/list", { params }))
}

export function toggleAdminUserStatus(id: number) {
  return unwrap<void>(request.put(`/admin/user/${id}/status`))
}

export function listAdminOrders(params: {
  page: number
  size: number
  status?: number
  keyword?: string
}) {
  return unwrap<AdminPage<AdminOrder>>(request.get("/admin/order/list", { params }))
}

export function cancelAdminOrder(orderSn: string) {
  return unwrap<void>(request.post(`/admin/order/${orderSn}/cancel`))
}

export function listAdminTrains(params: { page: number; size: number; keyword?: string }) {
  return unwrap<AdminPage<AdminTrain>>(request.get("/admin/train/list", { params }))
}

export function closeTimeoutOrders(timeoutMinutes = 30) {
  return unwrap<OperationTask>(
    request.post("/admin/ops/timeout-orders/close", null, { params: { timeoutMinutes } })
  )
}

export function calibrateRemainingTickets() {
  return unwrap<OperationTask>(request.post("/admin/ops/remaining-ticket/calibrate"))
}

export function compensateAbnormalOrders() {
  return unwrap<OperationTask>(request.post("/admin/ops/order/compensate"))
}
