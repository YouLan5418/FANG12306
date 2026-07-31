<script setup lang="ts">
import { reactive } from "vue"
import { useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import { register } from "@/api/user"

const router = useRouter()

const form = reactive({
  username: "",
  password: "",
  realName: "",
  phone: "",
  mail: ""
})

async function submit() {
  await register(form)
  ElMessage.success("注册成功，请登录")
  router.push({ name: "login" })
}
</script>

<template>
  <div class="auth-page">
    <el-card class="register-card" shadow="never">
      <h1>创建账号</h1>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" show-password type="password" />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.mail" />
        </el-form-item>
        <el-button class="submit-button" type="primary" native-type="submit">注册</el-button>
        <el-button link type="primary" @click="router.push({ name: 'login' })">返回登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 28px 16px;
  background: #eef3f8;
}

.register-card {
  width: min(460px, 100%);
  border-radius: 8px;
}

.register-card h1 {
  margin: 0 0 24px;
  font-size: 26px;
}

.submit-button {
  width: 100%;
  margin: 8px 0 12px;
}
</style>
