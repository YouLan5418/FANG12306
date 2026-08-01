<script setup lang="ts">
import { computed, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import { createPay, mockPaySuccess } from "@/api/pay"
import type { PayChannel, PayResp } from "@/types/pay"

const route = useRoute()
const router = useRouter()
const channel = ref<PayChannel>("ALI_PAY")
const loading = ref(false)
const paying = ref(false)
const payInfo = ref<PayResp | null>(null)

const orderSn = computed(() => String(route.params.orderSn || ""))
const totalAmount = computed(() => Number(route.query.amount || 0))

async function createPayment() {
  loading.value = true
  try {
    payInfo.value = await createPay({
      orderSn: orderSn.value,
      totalAmount: totalAmount.value,
      channel: channel.value
    })
    ElMessage.success("支付单创建成功")
  } finally {
    loading.value = false
  }
}

async function confirmPay() {
  paying.value = true
  try {
    await mockPaySuccess(orderSn.value, payInfo.value?.totalAmount || totalAmount.value)
    ElMessage.success("支付成功")
    router.push({ name: "orders" })
  } finally {
    paying.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <template #header>支付确认</template>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="订单号">{{ orderSn }}</el-descriptions-item>
      <el-descriptions-item label="支付金额">¥{{ (totalAmount / 100).toFixed(2) }}</el-descriptions-item>
    </el-descriptions>

    <div class="pay-actions">
      <el-radio-group v-model="channel">
        <el-radio-button label="ALI_PAY">支付宝</el-radio-button>
        <el-radio-button label="WECHAT_PAY">微信支付</el-radio-button>
      </el-radio-group>
      <el-button :loading="loading" type="primary" @click="createPayment">创建支付单</el-button>
      <el-button :disabled="!payInfo" :loading="paying" type="success" @click="confirmPay">
        模拟支付成功
      </el-button>
    </div>

    <el-alert
      v-if="payInfo"
      :closable="false"
      show-icon
      type="success"
      :title="`支付单 ${payInfo.paySn} 已创建，状态：${payInfo.status}`"
    />
  </el-card>
</template>

<style scoped>
.pay-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin: 22px 0;
}

@media (max-width: 760px) {
  .pay-actions {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .pay-actions :deep(.el-radio-group) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .pay-actions :deep(.el-radio-button__inner) {
    width: 100%;
  }

  .pay-actions .el-button {
    width: 100%;
  }
}
</style>
