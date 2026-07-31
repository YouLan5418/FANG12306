import type { Router } from "vue-router"
import { ElMessage } from "element-plus"
import { useAuthStore } from "@/stores/auth"

export function setupRouterGuards(router: Router) {
  router.beforeEach((to) => {
    const authStore = useAuthStore()

    if (to.meta.requiresAuth && !authStore.isLoggedIn) {
      ElMessage.warning("请先登录")
      return { name: "login", query: { redirect: to.fullPath } }
    }

    if (to.meta.requiresAdmin && !authStore.isAdmin) {
      ElMessage.error("当前账号无管理员权限")
      return { name: "ticketSearch" }
    }

    if (to.name === "login" && authStore.isLoggedIn) {
      return { name: "ticketSearch" }
    }

    return true
  })
}
