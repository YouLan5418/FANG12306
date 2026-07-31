<script setup lang="ts">
import { computed } from "vue"
import { useRoute, useRouter } from "vue-router"
import {
  ChatDotRound,
  DataBoard,
  House,
  SwitchButton,
  Tickets,
  User
} from "@element-plus/icons-vue"
import { useAuthStore } from "@/stores/auth"

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

function logout() {
  authStore.clearSession()
  router.push({ name: "login" })
}
</script>

<template>
  <div class="page-shell app-layout">
    <el-container>
      <el-header class="app-header">
        <div class="brand" @click="router.push({ name: 'ticketSearch' })">
          <img src="/logo.png" alt="12306" />
          <span>铁路购票系统</span>
        </div>

        <div class="header-actions">
          <template v-if="authStore.isLoggedIn">
            <el-tag effect="plain" round>
              {{ authStore.displayName }}
            </el-tag>
            <el-button :icon="SwitchButton" text @click="logout">退出</el-button>
          </template>
          <template v-else>
            <el-button text @click="router.push({ name: 'login' })">登录</el-button>
            <el-button type="primary" @click="router.push({ name: 'register' })">注册</el-button>
          </template>
        </div>
      </el-header>

      <el-container>
        <el-aside class="app-aside" width="220px">
          <el-menu :default-active="activeMenu" router>
            <el-menu-item index="/tickets">
              <el-icon><Tickets /></el-icon>
              <span>车票查询</span>
            </el-menu-item>
            <el-menu-item index="/orders">
              <el-icon><House /></el-icon>
              <span>我的订单</span>
            </el-menu-item>
            <el-menu-item index="/customer-service">
              <el-icon><ChatDotRound /></el-icon>
              <span>智能客服</span>
            </el-menu-item>
            <el-menu-item v-if="authStore.isAdmin" index="/admin">
              <el-icon><DataBoard /></el-icon>
              <span>管理后台</span>
            </el-menu-item>
          </el-menu>
        </el-aside>

        <el-main class="app-main">
          <div class="page-content">
            <div class="page-title">
              <div>
                <p>Index12306</p>
                <h1>{{ route.meta.title || "铁路购票系统" }}</h1>
              </div>
              <el-icon><User /></el-icon>
            </div>
            <RouterView />
          </div>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.app-layout :deep(.el-container) {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  color: #fff;
  background: #123c69;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}

.brand img {
  width: 32px;
  height: 32px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-aside {
  min-height: calc(100vh - 64px);
  border-right: 1px solid #e5e7eb;
  background: #fff;
}

.app-aside :deep(.el-menu) {
  border-right: 0;
}

.app-main {
  padding: 24px 0 40px;
}

.page-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title p {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 13px;
}

.page-title h1 {
  margin: 0;
  color: #111827;
  font-size: 26px;
}

.page-title .el-icon {
  width: 44px;
  height: 44px;
  color: #123c69;
  border-radius: 8px;
  background: #e8f1fb;
}

@media (max-width: 760px) {
  .app-header {
    padding: 0 14px;
  }

  .brand span {
    display: none;
  }

  .app-aside {
    display: none;
  }

  .app-main {
    padding-top: 16px;
  }
}
</style>
