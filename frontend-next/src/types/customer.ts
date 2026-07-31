export interface CustomerChatReq {
  sessionId?: string
  message: string
}

export interface CustomerSseEvent {
  type: "token" | "tool_call" | "done" | "error"
  content?: string
  sessionId?: string
  name?: string
  args?: unknown
  message?: string
}
