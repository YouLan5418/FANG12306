import request, { unwrap } from "./request"
import { useAuthStore } from "@/stores/auth"
import type {
  ChatHistoryMessage,
  ChatSession,
  CustomerChatReq,
  CustomerSseEvent
} from "@/types/customer"

interface StreamHandlers {
  onEvent: (event: CustomerSseEvent) => void
  onError?: (error: Error) => void
  onComplete?: () => void
}

export async function streamCustomerChat(data: CustomerChatReq, handlers: StreamHandlers) {
  const authStore = useAuthStore()
  const response = await fetch("/api/customer-service/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(authStore.token ? { Authorization: authStore.token } : {})
    },
    body: JSON.stringify(data)
  })

  if (!response.ok || !response.body) {
    throw new Error(`客服请求失败：HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder("utf-8")
  let buffer = ""

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() || ""
      lines.forEach((line) => parseSseLine(line, handlers))
    }

    if (buffer) {
      parseSseLine(buffer, handlers)
    }
    handlers.onComplete?.()
  } catch (error) {
    handlers.onError?.(error instanceof Error ? error : new Error("客服流读取失败"))
  } finally {
    reader.releaseLock()
  }
}

export function listCustomerSessions() {
  return unwrap<ChatSession[]>(request.get("/customer-service/session/list"))
}

export function listCustomerMessages(sessionId: string) {
  return unwrap<ChatHistoryMessage[]>(request.get(`/customer-service/session/${sessionId}/messages`))
}

export function deleteCustomerSession(sessionId: string) {
  return unwrap<void>(request.delete(`/customer-service/session/${sessionId}`))
}

function parseSseLine(line: string, handlers: StreamHandlers) {
  const trimmed = line.trim()
  if (!trimmed.startsWith("data:")) {
    return
  }

  const payload = trimmed.slice(5).trim()
  if (!payload || payload === "[DONE]") {
    return
  }

  try {
    handlers.onEvent(JSON.parse(payload) as CustomerSseEvent)
  } catch {
    handlers.onEvent({ type: "token", content: payload })
  }
}
