<script setup lang="ts">
import { onMounted, reactive, ref } from "vue"
import { ElMessage } from "element-plus"
import { queryUserProfile, updateUserProfile } from "@/api/user"
import type { UserProfile, UserUpdateReq } from "@/types/user"

const loading = ref(false)
const saving = ref(false)
const profile = ref<UserProfile | null>(null)

const form = reactive<UserUpdateReq>({
  realName: "",
  phone: "",
  mail: "",
  region: "",
  postCode: "",
  address: ""
})

const idTypeText: Record<number, string> = {
  0: "居民身份证",
  1: "护照",
  2: "港澳居民来往内地通行证",
  3: "台湾居民来往大陆通行证"
}

async function loadProfile() {
  loading.value = true
  try {
    const result = await queryUserProfile()
    profile.value = result
    form.realName = result.realName || ""
    form.phone = result.phone || ""
    form.mail = result.mail || ""
    form.region = result.region || ""
    form.postCode = result.postCode || ""
    form.address = result.address || ""
  } finally {
    loading.value = false
  }
}

async function submit() {
  saving.value = true
  try {
    await updateUserProfile({
      realName: form.realName,
      phone: form.phone,
      mail: form.mail,
      region: form.region,
      postCode: form.postCode,
      address: form.address
    })
    ElMessage.success("个人信息已更新")
    await loadProfile()
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="profile-page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>账号资料</span>
          <el-button :loading="loading" @click="loadProfile">刷新</el-button>
        </div>
      </template>

      <el-descriptions v-if="profile" :column="2" border>
        <el-descriptions-item label="用户 ID">{{ profile.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ profile.realName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="证件类型">
          {{ profile.idType !== undefined ? idTypeText[profile.idType] || profile.idType : "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="证件号码">{{ profile.idCard || "-" }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profile.phone || "-" }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ profile.mail || "-" }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ profile.address || "-" }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>编辑信息</template>
      <el-form class="profile-form" :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.mail" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.region" placeholder="请输入地区" />
        </el-form-item>
        <el-form-item label="邮编">
          <el-input v-model="form.postCode" placeholder="请输入邮编" />
        </el-form-item>
        <el-form-item label="地址" class="full-row">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <div class="form-actions">
          <el-button :loading="saving" type="primary" native-type="submit">保存修改</el-button>
        </div>
      </el-form>
    </el-card>
  </section>
</template>

<style scoped>
.profile-page {
  display: grid;
  gap: 18px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 16px;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.full-row,
.form-actions {
  grid-column: 1 / -1;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .profile-page {
    gap: 12px;
  }

  .card-header {
    align-items: stretch;
    gap: 10px;
  }

  .card-header .el-button {
    flex: 0 0 auto;
  }

  .profile-form {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .form-actions {
    justify-content: stretch;
  }

  .form-actions .el-button {
    width: 100%;
  }
}
</style>
