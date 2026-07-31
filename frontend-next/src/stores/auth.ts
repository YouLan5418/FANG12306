import { defineStore } from "pinia"
import { computed, ref } from "vue"
import type { LoginResp } from "@/types/user"
import type { UserRole } from "@/types/common"

const TOKEN_KEY = "index12306.token"
const USER_KEY = "index12306.user"

export const useAuthStore = defineStore("auth", () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || "")
  const username = ref("")
  const realName = ref("")
  const role = ref<UserRole>("USER")

  const cachedUser = localStorage.getItem(USER_KEY)
  if (cachedUser) {
    try {
      const user = JSON.parse(cachedUser) as Omit<LoginResp, "accessToken">
      username.value = user.username || ""
      realName.value = user.realName || ""
      role.value = user.role === 1 || user.role === "ADMIN" ? "ADMIN" : "USER"
    } catch {
      localStorage.removeItem(USER_KEY)
    }
  }

  const isLoggedIn = computed(() => Boolean(token.value))
  const isAdmin = computed(() => role.value === "ADMIN")
  const displayName = computed(() => realName.value || username.value || "未登录")

  function setSession(payload: LoginResp) {
    token.value = payload.accessToken
    username.value = payload.username
    realName.value = payload.realName || ""
    role.value = payload.role === 1 || payload.role === "ADMIN" ? "ADMIN" : "USER"

    localStorage.setItem(TOKEN_KEY, payload.accessToken)
    localStorage.setItem(
      USER_KEY,
      JSON.stringify({
        username: username.value,
        realName: realName.value,
        role: role.value
      })
    )
  }

  function clearSession() {
    token.value = ""
    username.value = ""
    realName.value = ""
    role.value = "USER"
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    username,
    realName,
    role,
    isLoggedIn,
    isAdmin,
    displayName,
    setSession,
    clearSession
  }
})
