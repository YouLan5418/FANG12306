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

export interface KbCategory {
  id: number
  name: string
  description?: string
  documentCount?: number
}

export interface KbDocument {
  id: number
  categoryId: number
  fileName: string
  fileType?: string
  parserType?: string
  chunkMode?: string
  status?: number
  chunkCount?: number
}

export interface IngestionTask {
  id: number
  documentId: number
  status?: number
  totalChunks?: number
  processedChunks?: number
  errorMsg?: string
  createTime?: string
  updateTime?: string
}

export interface KnowledgeChunk {
  id: number
  categoryId: number
  documentId: number
  chunkIndex: number
  content: string
  metadata?: string
  embeddingDimension?: number
  createTime?: string
}

export interface KnowledgeSearchResult {
  chunkId: string
  categoryId: number
  documentId: number
  chunkIndex: number
  content: string
  score?: number
  source?: string
  metadata?: string
}

export interface DocumentUploadResp {
  documentId: number
  taskId: number
}
