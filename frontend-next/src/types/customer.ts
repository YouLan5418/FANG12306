export interface CustomerChatReq {
  sessionId?: string
  message: string
}

export interface CustomerSource {
  title?: string
  content?: string
  score?: number
}

export interface CustomerSseEvent {
  type: "token" | "thinking" | "tool_call" | "tool_result" | "done" | "error"
  content?: string
  sessionId?: string
  name?: string
  args?: unknown
  result?: unknown
  sources?: CustomerSource[]
  message?: string
}

export interface ChatSession {
  sessionId: string
  title?: string
  updateTime?: string
}

export interface ChatHistoryMessage {
  role: "user" | "assistant" | "tool" | string
  content?: string
  toolName?: string
  toolArgs?: string
  toolResult?: string
  createTime?: string
}
