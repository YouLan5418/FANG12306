import request, { unwrap } from "./request"
import { useAuthStore } from "@/stores/auth"
import type {
  ChatHistoryMessage,
  ChatSession,
  CustomerChatReq,
  CustomerSseEvent,
  DocumentUploadResp,
  IngestionTask,
  KbCategory,
  KbDocument,
  KnowledgeChunk,
  KnowledgeSearchResult
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

export function listKbCategories() {
  return unwrap<KbCategory[]>(request.get("/admin/kb/category"))
}

export function createKbCategory(data: { name: string; description?: string }) {
  return unwrap<number>(request.post("/admin/kb/category", data))
}

export function updateKbCategory(id: number, data: { name: string; description?: string }) {
  return unwrap<void>(request.put(`/admin/kb/category/${id}`, data))
}

export function deleteKbCategory(id: number) {
  return unwrap<void>(request.delete(`/admin/kb/category/${id}`))
}

export function listKbDocuments(categoryId?: number) {
  return unwrap<KbDocument[]>(
    request.get("/admin/kb/document", { params: { categoryId } })
  )
}

export function uploadKbDocument(data: {
  file: File
  categoryId: number
  parserType?: string
  chunkMode?: string
}) {
  const formData = new FormData()
  formData.append("file", data.file)
  formData.append("categoryId", String(data.categoryId))
  if (data.parserType) {
    formData.append("parserType", data.parserType)
  }
  if (data.chunkMode) {
    formData.append("chunkMode", data.chunkMode)
  }
  return unwrap<DocumentUploadResp>(
    request.post("/admin/kb/document/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 120000
    })
  )
}

export function deleteKbDocument(id: number) {
  return unwrap<void>(request.delete(`/admin/kb/document/${id}`))
}

export function listIngestionTasks() {
  return unwrap<IngestionTask[]>(request.get("/admin/kb/task"))
}

export function retryIngestionTask(id: number) {
  return unwrap<void>(request.post(`/admin/kb/task/${id}/retry`))
}

export function listKnowledgeChunks(params?: {
  documentId?: number
  categoryId?: number
  keyword?: string
}) {
  return unwrap<KnowledgeChunk[]>(request.get("/admin/kb/chunk", { params }))
}

export function updateKnowledgeChunk(id: number, content: string) {
  return unwrap<void>(request.put(`/admin/kb/chunk/${id}`, { content }))
}

export function searchKnowledgeTest(params: {
  query: string
  categoryId?: number
  topK?: number
}) {
  return unwrap<KnowledgeSearchResult[]>(request.get("/admin/kb/search", { params }))
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
