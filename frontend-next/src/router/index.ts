import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router"
import { setupRouterGuards } from "./guards"

export const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/auth/LoginView.vue"),
    meta: { public: true }
  },
  {
    path: "/register",
    name: "register",
    component: () => import("@/views/auth/RegisterView.vue"),
    meta: { public: true }
  },
  {
    path: "/",
    component: () => import("@/components/layout/MainLayout.vue"),
    redirect: "/tickets",
    children: [
      {
        path: "tickets",
        name: "ticketSearch",
        component: () => import("@/views/ticket/TicketSearchView.vue"),
        meta: { title: "车票查询" }
      },
      {
        path: "purchase",
        name: "purchaseConfirm",
        component: () => import("@/views/ticket/PurchaseConfirmView.vue"),
        meta: { title: "确认订单", requiresAuth: true }
      },
      {
        path: "orders",
        name: "orders",
        component: () => import("@/views/order/OrderListView.vue"),
        meta: { title: "我的订单", requiresAuth: true }
      },
      {
        path: "profile",
        name: "profile",
        component: () => import("@/views/user/ProfileView.vue"),
        meta: { title: "我的信息", requiresAuth: true }
      },
      {
        path: "payment/:orderSn",
        name: "payment",
        component: () => import("@/views/pay/PaymentView.vue"),
        meta: { title: "订单支付", requiresAuth: true }
      },
      {
        path: "refund/:orderSn",
        name: "refund",
        component: () => import("@/views/pay/RefundView.vue"),
        meta: { title: "订单退款", requiresAuth: true }
      },
      {
        path: "admin",
        name: "adminDashboard",
        component: () => import("@/views/admin/AdminDashboardView.vue"),
        meta: { title: "管理后台", requiresAuth: true, requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

setupRouterGuards(router)

export default router
