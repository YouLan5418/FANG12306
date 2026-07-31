<script setup lang="ts">
import { nextTick, ref } from "vue"
import { useRouter } from "vue-router"
import { ChatDotRound, Close, Promotion } from "@element-plus/icons-vue"
import { ElMessage } from "element-plus"
import { streamCustomerChat } from "@/api/customer"
import { useAuthStore } from "@/stores/auth"
import type { CustomerSseEvent } from "@/types/customer"

interface ChatMessage {
  role: "assistant" | "user" | "system"
  content: string
}

const router = useRouter()
const authStore = useAuthStore()
const open = ref(false)
const sending = ref(false)
const input = ref("")
const sessionId = ref("")
const chatBodyRef = ref<HTMLElement>()
const messages = ref<ChatMessage[]>([
  {
    role: "assistant",
    content: "你好，我是 12306 智能客服。订单、退票、支付问题都可以从这里问我。"
  }
])

function toggle() {
  if (!authStore.isLoggedIn) {
    ElMessage.warning("请先登录后使用智能客服")
    router.push({ name: "login", query: { redirect: router.currentRoute.value.fullPath } })
    return
  }

  open.value = !open.value
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
    sending.value = false
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
  } else if (event.type === "tool_call") {
    messages.value.push({
      role: "system",
      content: `已调用业务工具：${event.name || "unknown"}`
    })
  } else if (event.type === "done") {
    sessionId.value = event.sessionId || sessionId.value
  } else if (event.type === "error") {
    messages.value[assistantIndex].content ||= event.message || "客服处理失败"
    ElMessage.error(event.message || "客服处理失败")
  }
  scrollToBottom()
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
          <div>
            <strong>智能客服</strong>
            <span>购票、支付、退款助手</span>
          </div>
          <el-button :icon="Close" circle text @click="open = false" />
        </header>

        <main ref="chatBodyRef" class="chat-body">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="message"
            :class="message.role"
          >
            <span v-if="message.content">{{ message.content }}</span>
            <span v-else class="typing">正在思考...</span>
          </div>
        </main>

        <footer class="chat-input">
          <el-input
            v-model="input"
            :disabled="sending"
            placeholder="输入你的问题"
            @keyup.enter="send"
          />
          <el-button :icon="Promotion" :loading="sending" type="primary" @click="send" />
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
  width: min(360px, calc(100vw - 32px));
  height: min(520px, calc(100vh - 120px));
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
  padding: 16px;
  color: #fff;
  background: #123c69;
}

.chat-header div {
  display: grid;
  gap: 4px;
}

.chat-header span {
  color: #dbeafe;
  font-size: 12px;
}

.chat-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding: 16px;
  background: #f8fafc;
}

.message {
  max-width: 82%;
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

.message.system {
  align-self: center;
  max-width: 92%;
  color: #64748b;
  background: #eef2f7;
  font-size: 12px;
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
    height: min(460px, calc(100vh - 172px));
  }
}
</style>
