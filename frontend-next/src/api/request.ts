import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios"
import { ElMessage } from "element-plus"
import { useAuthStore } from "@/stores/auth"
import type { ApiResult } from "@/types/common"

const request = axios.create({
  baseURL: "/api",
  timeout: 30000
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const authStore = useAuthStore()

  if (authStore.token) {
    config.headers.Authorization = authStore.token
  }

  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>

    if (result && typeof result === "object" && "code" in result && result.code !== "0") {
      ElMessage.error(result.message || "请求失败")
      return Promise.reject(result)
    }

    return response
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const authStore = useAuthStore()

    if (error.response?.status === 401) {
      authStore.clearSession()
      ElMessage.error("登录已失效，请重新登录")
      return Promise.reject(error)
    }

    ElMessage.error(error.response?.data?.message || "网络异常，请稍后重试")
    return Promise.reject(error)
  }
)

export async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const response = await promise
  return response.data.data
}

export default request
