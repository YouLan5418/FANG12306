<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import { queryOrderDetail } from "@/api/order"
import { refund } from "@/api/pay"
import type { OrderDetail, OrderPassengerDetail } from "@/types/order"

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const order = ref<OrderDetail | null>(null)

const form = reactive({
  refundType: 12 as 11 | 12,
  selectedSeats: [] as string[]
})

const orderSn = computed(() => String(route.params.orderSn || ""))
const details = computed(() => order.value?.passengerDetails || [])
const totalAmount = computed(() =>
  details.value.reduce((sum, item) => sum + Number(item.amount || 0), 0)
)
const selectedDetails = computed(() =>
  details.value.filter((item) => form.selectedSeats.includes(detailKey(item)))
)
const selectedAmount = computed(() =>
  selectedDetails.value.reduce((sum, item) => sum + Number(item.amount || 0), 0)
)

function detailKey(item: OrderPassengerDetail) {
  return `${item.carriageNumber}_${item.seatNumber}`
}

async function loadOrder() {
  loading.value = true
  try {
    order.value = await queryOrderDetail(orderSn.value)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (form.refundType === 11 && selectedDetails.value.length === 0) {
    ElMessage.warning("请选择需要部分退款的座位")
    return
  }

  submitting.value = true
  try {
    await refund({
      orderSn: orderSn.value,
      refundType: form.refundType,
      refundAmount: form.refundType === 11 ? selectedAmount.value : undefined,
      refundDetailReqDTOList:
        form.refundType === 11
          ? selectedDetails.value.map((item) => ({
              carriageNumber: item.carriageNumber,
              seatNumber: item.seatNumber,
              seatType: item.seatType
            }))
          : undefined
    })
    ElMessage.success("退款申请已提交")
    router.push({ name: "orders" })
  } finally {
    submitting.value = false
  }
}

onMounted(loadOrder)
</script>

<template>
  <section class="refund-page">
    <el-card v-loading="loading" shadow="never">
      <template #header>退款订单</template>
      <el-descriptions v-if="order" :column="3" border>
        <el-descriptions-item label="订单号">{{ order.orderSn }}</el-descriptions-item>
        <el-descriptions-item label="车次">{{ order.trainNumber }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ (totalAmount / 100).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="出发站">{{ order.departure }}</el-descriptions-item>
        <el-descriptions-item label="到达站">{{ order.arrival }}</el-descriptions-item>
        <el-descriptions-item label="乘车日期">{{ order.ridingDate }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>退款方式</template>
      <el-radio-group v-model="form.refundType">
        <el-radio-button :label="12">全额退款</el-radio-button>
        <el-radio-button :label="11">部分退款</el-radio-button>
      </el-radio-group>

      <el-checkbox-group v-if="form.refundType === 11" v-model="form.selectedSeats" class="seat-list">
        <el-checkbox
          v-for="item in details"
          :key="detailKey(item)"
          :label="detailKey(item)"
          border
        >
          {{ item.realName }} {{ item.carriageNumber }}车 {{ item.seatNumber }}
          ¥{{ (item.amount / 100).toFixed(2) }}
        </el-checkbox>
      </el-checkbox-group>

      <el-alert
        :closable="false"
        class="amount-alert"
        show-icon
        type="info"
        :title="`预计退款金额：¥${((form.refundType === 11 ? selectedAmount : totalAmount) / 100).toFixed(2)}`"
      />

      <el-button :loading="submitting" type="warning" @click="submit">提交退款</el-button>
    </el-card>
  </section>
</template>

<style scoped>
.refund-page {
  display: grid;
  gap: 18px;
}

.seat-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 18px 0;
}

.amount-alert {
  margin: 18px 0;
}

@media (max-width: 760px) {
  .refund-page {
    gap: 12px;
  }

  .refund-page :deep(.el-radio-group) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .refund-page :deep(.el-radio-button__inner) {
    width: 100%;
  }

  .seat-list {
    display: grid;
    grid-template-columns: 1fr;
  }

  .seat-list :deep(.el-checkbox) {
    width: 100%;
    margin-right: 0;
  }

  .refund-page .el-button {
    width: 100%;
  }
}
</style>
