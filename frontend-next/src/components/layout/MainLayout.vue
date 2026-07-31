<script setup lang="ts">
import { computed } from "vue"
import { useRoute, useRouter } from "vue-router"
import { DataBoard, House, SwitchButton, Tickets, User } from "@element-plus/icons-vue"
import { useAuthStore } from "@/stores/auth"
import CustomerServiceWidget from "@/components/customer/CustomerServiceWidget.vue"

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)
const navItems = computed(() => {
  const items = [
    { path: "/tickets", shortLabel: "车票", fullLabel: "车票查询", icon: Tickets },
    { path: "/orders", shortLabel: "订单", fullLabel: "我的订单", icon: House },
    { path: "/profile", shortLabel: "我的", fullLabel: "我的信息", icon: User }
  ]

  if (authStore.isAdmin) {
    items.push({ path: "/admin", shortLabel: "管理", fullLabel: "管理后台", icon: DataBoard })
  }

  return items
})

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
            <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.fullLabel }}</span>
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

    <nav
      class="bottom-nav"
      :style="{ gridTemplateColumns: `repeat(${navItems.length}, minmax(0, 1fr))` }"
      aria-label="移动端主导航"
    >
      <button
        v-for="item in navItems"
        :key="item.path"
        class="bottom-nav-item"
        :class="{ active: activeMenu === item.path }"
        type="button"
        @click="router.push(item.path)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.shortLabel }}</span>
      </button>
    </nav>

    <CustomerServiceWidget />
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

.bottom-nav {
  display: none;
}

@media (max-width: 760px) {
  .app-header {
    height: 56px;
    padding: 0 14px;
  }

  .brand span {
    display: none;
  }

  .header-actions {
    gap: 6px;
  }

  .header-actions :deep(.el-tag) {
    max-width: 112px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .app-aside {
    display: none;
  }

  .app-main {
    padding: 16px 0 88px;
  }

  .page-content {
    width: min(100vw - 24px, 720px);
  }

  .page-title {
    margin-bottom: 14px;
  }

  .page-title h1 {
    font-size: 22px;
  }

  .page-title .el-icon {
    width: 38px;
    height: 38px;
  }

  .bottom-nav {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 1100;
    display: grid;
    gap: 4px;
    padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
    border-top: 1px solid #dbe3ec;
    background: rgb(255 255 255 / 96%);
    box-shadow: 0 -10px 28px rgb(15 23 42 / 10%);
    backdrop-filter: blur(10px);
  }

  .bottom-nav-item {
    display: grid;
    min-width: 0;
    min-height: 50px;
    place-items: center;
    gap: 3px;
    color: #64748b;
    border: 0;
    border-radius: 8px;
    background: transparent;
    font-size: 12px;
    cursor: pointer;
  }

  .bottom-nav-item .el-icon {
    width: 21px;
    height: 21px;
  }

  .bottom-nav-item.active {
    color: #123c69;
    background: #e8f1fb;
    font-weight: 700;
  }
}
</style>
