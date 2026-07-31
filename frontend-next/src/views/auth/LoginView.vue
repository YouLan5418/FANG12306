<script setup lang="ts">
import { reactive } from "vue"
import { useRoute, useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import { Lock, User } from "@element-plus/icons-vue"
import { login } from "@/api/user"
import { useAuthStore } from "@/stores/auth"

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  username: "",
  password: ""
})

async function submit() {
  const session = await login(form)
  authStore.setSession(session)
  ElMessage.success("登录成功")
  router.push((route.query.redirect as string) || "/tickets")
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-panel">
      <div class="auth-intro">
        <img src="/logo.png" alt="12306" />
        <h1>铁路购票系统</h1>
        <p>面向高并发购票、支付退款闭环和运营兜底的业务演示前端。</p>
      </div>

      <el-card class="auth-card" shadow="never">
        <h2>账号登录</h2>
        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              autocomplete="current-password"
              show-password
              type="password"
            />
          </el-form-item>
          <el-button class="auth-submit" type="primary" native-type="submit">登录</el-button>
        </el-form>
        <el-button link type="primary" @click="router.push({ name: 'register' })">
          还没有账号，去注册
        </el-button>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px 16px;
  background: #eef3f8;
}

.auth-panel {
  display: grid;
  width: min(980px, 100%);
  grid-template-columns: 1fr 420px;
  overflow: hidden;
  border: 1px solid #dde6ef;
  border-radius: 8px;
  background: #fff;
}

.auth-intro {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px;
  color: #fff;
  background: #123c69;
}

.auth-intro img {
  width: 56px;
  height: 56px;
  margin-bottom: 24px;
}

.auth-intro h1 {
  margin: 0 0 14px;
  font-size: 34px;
}

.auth-intro p {
  max-width: 420px;
  margin: 0;
  color: #dbeafe;
  line-height: 1.8;
}

.auth-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  border: 0;
  border-radius: 0;
}

.auth-card h2 {
  margin: 0 0 28px;
  font-size: 24px;
}

.auth-submit {
  width: 100%;
  margin: 8px 0 12px;
}

@media (max-width: 860px) {
  .auth-panel {
    grid-template-columns: 1fr;
  }

  .auth-intro {
    padding: 32px;
  }
}
</style>
