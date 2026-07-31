<script setup lang="ts">
import { nextTick, ref } from "vue"
import { useRouter } from "vue-router"
import { ChatDotRound, Clock, Close, Delete, Plus, Promotion } from "@element-plus/icons-vue"
import { ElMessage, ElMessageBox } from "element-plus"
import {
  deleteCustomerSession,
  listCustomerMessages,
  listCustomerSessions,
  streamCustomerChat
} from "@/api/customer"
import { useAuthStore } from "@/stores/auth"
import type { ChatSession, CustomerSource, CustomerSseEvent } from "@/types/customer"

interface ChatMessage {
  role: "assistant" | "user" | "system" | "thinking" | "tool"
  content: string
  toolName?: string
  args?: unknown
  result?: unknown
  sources?: CustomerSource[]
  pending?: boolean
}

const router = useRouter()
const authStore = useAuthStore()
const open = ref(false)
const showHistory = ref(false)
const sending = ref(false)
const historyLoading = ref(false)
const input = ref("")
const sessionId = ref("")
const chatBodyRef = ref<HTMLElement>()
const sessions = ref<ChatSession[]>([])
const messages = ref<ChatMessage[]>([welcomeMessage()])

function welcomeMessage(): ChatMessage {
  return {
    role: "assistant",
    content: "你好，我是 12306 智能客服。订单、退票、支付、车站和购票规则都可以从这里问我。"
  }
}

function toggle() {
  if (!authStore.isLoggedIn) {
    ElMessage.warning("请先登录后使用智能客服")
    router.push({ name: "login", query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  open.value = !open.value
}

function newChat() {
  sessionId.value = ""
  showHistory.value = false
  messages.value = [welcomeMessage()]
}

async function openHistory() {
  showHistory.value = true
  historyLoading.value = true
  try {
    sessions.value = await listCustomerSessions()
  } finally {
    historyLoading.value = false
  }
}

async function loadHistory(session: ChatSession) {
  historyLoading.value = true
  try {
    const history = await listCustomerMessages(session.sessionId)
    sessionId.value = session.sessionId
    messages.value = history.length
      ? history.map((item) => {
          if (item.role === "tool") {
            return {
              role: "tool",
              content: "工具调用结果",
              toolName: item.toolName,
              args: parseMaybeJson(item.toolArgs),
              result: parseMaybeJson(item.toolResult)
            }
          }
          return {
            role: item.role === "user" ? "user" : "assistant",
            content: item.content || ""
          }
        })
      : [welcomeMessage()]
    showHistory.value = false
    await scrollToBottom()
  } finally {
    historyLoading.value = false
  }
}

async function removeHistory(session: ChatSession) {
  await ElMessageBox.confirm("确定删除这段客服会话吗？", "删除会话", { type: "warning" })
  await deleteCustomerSession(session.sessionId)
  ElMessage.success("会话已删除")
  if (session.sessionId === sessionId.value) {
    newChat()
  }
  await openHistory()
}

async function send() {
  const content = input.value.trim()
  if (!content || sending.value) {
    return
  }

  messages.value.push({ role: "user", content })
  const assistantIndex = messages.value.push({ role: "assistant", content: "" }) - 1
  input.value = ""
  sending.value = true
  await scrollToBottom()

  try {
    await streamCustomerChat(
      {
        sessionId: sessionId.value || undefined,
        message: content
      },
      {
        onEvent: (event) => handleSseEvent(event, assistantIndex),
        onError: (error) => {
          messages.value[assistantIndex].content ||= "客服暂时不可用，请稍后再试。"
          ElMessage.error(error.message)
        },
        onComplete: () => {
          sending.value = false
        }
      }
    )
  } catch (error) {
    messages.value[assistantIndex].content = "客服暂时不可用，请稍后再试。"
    ElMessage.error(error instanceof Error ? error.message : "客服请求失败")
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function handleSseEvent(event: CustomerSseEvent, assistantIndex: number) {
  if (event.type === "token") {
    messages.value[assistantIndex].content += event.content || ""
  } else if (event.type === "thinking") {
    messages.value.push({ role: "thinking", content: event.content || "正在分析问题..." })
  } else if (event.type === "tool_call") {
    messages.value.push({
      role: "tool",
      content: "正在调用业务工具",
      toolName: event.name || "unknown",
      args: event.args,
      pending: true
    })
  } else if (event.type === "tool_result") {
    const toolMessage = [...messages.value].reverse().find(
      (message) => message.role === "tool" && message.toolName === event.name && message.pending
    )
    if (toolMessage) {
      toolMessage.content = "工具调用完成"
      toolMessage.result = event.result
      toolMessage.sources = event.sources || []
      toolMessage.pending = false
    } else {
      messages.value.push({
        role: "tool",
        content: "工具调用完成",
        toolName: event.name,
        args: event.args,
        result: event.result,
        sources: event.sources || []
      })
    }
  } else if (event.type === "done") {
    sessionId.value = event.sessionId || sessionId.value
  } else if (event.type === "error") {
    messages.value[assistantIndex].content ||= event.message || "客服处理失败"
    ElMessage.error(event.message || "客服处理失败")
  }
  scrollToBottom()
}

function parseMaybeJson(value?: string) {
  if (!value) return undefined
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function formatValue(value: unknown) {
  if (value === undefined || value === null || value === "") {
    return "暂无"
  }
  if (typeof value === "string") {
    return value
  }
  return JSON.stringify(value, null, 2)
}

function sessionTitle(session: ChatSession) {
  return session.title || "未命名会话"
}

async function scrollToBottom() {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}
</script>

<template>
  <div class="customer-widget">
    <transition name="panel">
      <section v-if="open" class="chat-panel">
        <header class="chat-header">
          <div class="chat-title">
            <strong>{{ showHistory ? "历史会话" : "智能客服" }}</strong>
            <span>{{ showHistory ? "选择一段会话继续查看" : "购票、支付、退票助手" }}</span>
          </div>
          <div class="header-actions">
            <el-tooltip content="历史会话" placement="bottom">
              <el-button :icon="Clock" circle text @click="openHistory" />
            </el-tooltip>
            <el-tooltip content="新会话" placement="bottom">
              <el-button :icon="Plus" circle text @click="newChat" />
            </el-tooltip>
            <el-button :icon="Close" circle text @click="open = false" />
          </div>
        </header>

        <main v-if="showHistory" v-loading="historyLoading" class="history-body">
          <button
            v-for="session in sessions"
            :key="session.sessionId"
            class="history-item"
            type="button"
            @click="loadHistory(session)"
          >
            <span>{{ sessionTitle(session) }}</span>
            <small>{{ session.updateTime || "暂无时间" }}</small>
            <el-button :icon="Delete" circle text type="danger" @click.stop="removeHistory(session)" />
          </button>
          <el-empty v-if="!sessions.length && !historyLoading" description="暂无历史会话" />
        </main>

        <main v-else ref="chatBodyRef" class="chat-body">
          <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
            <template v-if="message.role === 'tool'">
              <strong>{{ message.toolName || "业务工具" }}</strong>
              <small>{{ message.content }}</small>
              <pre>参数：{{ formatValue(message.args) }}</pre>
              <pre v-if="!message.pending">结果：{{ formatValue(message.result) }}</pre>
              <div v-if="message.sources?.length" class="sources">
                <span>信息来源</span>
                <article v-for="source in message.sources" :key="source.title">
                  <b>{{ source.title }}</b>
                  <p>{{ source.content }}</p>
                </article>
              </div>
            </template>
            <template v-else>
              <span v-if="message.content">{{ message.content }}</span>
              <span v-else class="typing">正在思考...</span>
            </template>
          </div>
        </main>

        <footer class="chat-input">
          <el-input
            v-model="input"
            :disabled="sending || showHistory"
            placeholder="输入你的问题"
            @keyup.enter="send"
          />
          <el-button :icon="Promotion" :loading="sending" :disabled="showHistory" type="primary" @click="send" />
        </footer>
      </section>
    </transition>

    <el-tooltip content="智能客服" placement="left">
      <button class="float-button" type="button" @click="toggle">
        <el-icon><ChatDotRound /></el-icon>
      </button>
    </el-tooltip>
  </div>
</template>

<style scoped>
.customer-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1200;
}

.float-button {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  color: #fff;
  border: 0;
  border-radius: 50%;
  background: #123c69;
  box-shadow: 0 12px 28px rgb(18 60 105 / 28%);
  cursor: pointer;
}

.float-button .el-icon {
  width: 26px;
  height: 26px;
}

.chat-panel {
  position: absolute;
  right: 0;
  bottom: 72px;
  display: grid;
  width: min(420px, calc(100vw - 32px));
  height: min(620px, calc(100vh - 120px));
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
  border: 1px solid #d8e2ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 48px rgb(15 23 42 / 18%);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  color: #fff;
  background: #123c69;
}

.chat-title {
  display: grid;
  gap: 4px;
}

.chat-header span {
  color: #dbeafe;
  font-size: 12px;
}

.header-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.chat-header :deep(.el-button) {
  color: #fff;
}

.chat-body,
.history-body {
  overflow-y: auto;
  padding: 16px;
  background: #f8fafc;
}

.chat-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message {
  max-width: 86%;
  padding: 10px 12px;
  border-radius: 8px;
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-wrap;
}

.message.assistant {
  align-self: flex-start;
  color: #1f2937;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.message.user {
  align-self: flex-end;
  color: #fff;
  background: #2563eb;
}

.message.system,
.message.thinking {
  align-self: center;
  max-width: 92%;
  color: #64748b;
  background: #eef2f7;
  font-size: 12px;
}

.message.tool {
  align-self: stretch;
  max-width: 100%;
  color: #334155;
  background: #fff;
  border: 1px solid #dbeafe;
}

.message.tool strong,
.message.tool small {
  display: block;
}

.message.tool small {
  margin-top: 2px;
  color: #64748b;
}

.message pre {
  overflow-x: auto;
  margin: 8px 0 0;
  padding: 8px;
  border-radius: 6px;
  background: #f1f5f9;
  font-family: Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

.sources {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.sources > span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.sources article {
  padding: 8px;
  border-radius: 6px;
  background: #ecfdf5;
}

.sources b {
  display: block;
  margin-bottom: 4px;
}

.sources p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #475569;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.history-body {
  display: grid;
  align-content: start;
  gap: 10px;
}

.history-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 8px;
  width: 100%;
  padding: 12px;
  text-align: left;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.history-item span {
  overflow: hidden;
  color: #1f2937;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item small {
  color: #64748b;
}

.history-item :deep(.el-button) {
  grid-row: span 2;
}

.typing {
  color: #64748b;
}

.chat-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
}

.panel-enter-active,
.panel-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 760px) {
  .customer-widget {
    right: 16px;
    bottom: calc(86px + env(safe-area-inset-bottom));
  }

  .float-button {
    width: 52px;
    height: 52px;
  }

  .chat-panel {
    height: min(520px, calc(100vh - 172px));
  }
}
</style>
