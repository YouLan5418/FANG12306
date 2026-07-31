import request, { unwrap } from "./request"
import type {
  AdminDashboard,
  AdminOrderStats,
  AdminOrder,
  AdminPage,
  AdminTrend,
  AdminTrain,
  AdminTrainDetail,
  AdminUser,
  OperationTask,
  TrainPublishReq
} from "@/types/admin"

export function getAdminDashboard() {
  return unwrap<AdminDashboard>(request.get("/admin/dashboard"))
}

export function getOrderTrend(days = 7) {
  return unwrap<AdminTrend>(request.get("/admin/stats/order-trend", { params: { days } }))
}

export function getRevenueTrend(days = 7) {
  return unwrap<AdminTrend>(request.get("/admin/stats/revenue-trend", { params: { days } }))
}

export function getAdminOrderStats() {
  return unwrap<AdminOrderStats>(request.get("/admin/order/stats"))
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

export function getTrainDetail(id: number) {
  return unwrap<AdminTrainDetail>(request.get(`/admin/train/${id}`))
}

export function publishTrain(data: TrainPublishReq) {
  return unwrap<number>(request.post("/admin/train/publish", data))
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
