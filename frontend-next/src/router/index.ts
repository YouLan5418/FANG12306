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
        path: "orders",
        name: "orders",
        component: () => import("@/views/order/OrderListView.vue"),
        meta: { title: "我的订单", requiresAuth: true }
      },
      {
        path: "customer-service",
        name: "customerService",
        component: () => import("@/views/customer/CustomerServiceView.vue"),
        meta: { title: "智能客服", requiresAuth: true }
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
