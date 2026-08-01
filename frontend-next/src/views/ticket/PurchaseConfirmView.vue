<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import { purchaseTicket } from "@/api/ticket"
import { queryPassengers } from "@/api/user"
import type { Passenger } from "@/types/user"
import type { PurchaseTicketDetail, TicketItem } from "@/types/ticket"

const router = useRouter()
const ticket = ref<TicketItem | null>(null)
const passengers = ref<Passenger[]>([])
const loading = ref(false)
const purchaseResult = ref<PurchaseTicketDetail[]>([])
const orderSn = ref("")

const form = reactive({
  passengerId: "",
  seatType: 2,
  ticketType: 0,
  chooseSeat: ""
})

const seatTypeText: Record<number, string> = {
  0: "商务座",
  1: "一等座",
  2: "二等座",
  3: "硬座",
  4: "硬卧",
  5: "软卧"
}

const seatOptions = computed(() =>
  (ticket.value?.seatClassList || []).map((seat) => ({
    label: `${seatTypeText[seat.seatType] || `座席 ${seat.seatType}`} / 余票 ${seat.quantity} / ¥${seat.price}`,
    value: seat.seatType
  }))
)

const totalAmount = computed(() =>
  purchaseResult.value.reduce((sum, item) => sum + Number(item.amount || 0), 0)
)

onMounted(async () => {
  const rawTicket = sessionStorage.getItem("index12306.selectedTicket")
  if (rawTicket) {
    ticket.value = JSON.parse(rawTicket) as TicketItem
    form.seatType = ticket.value.seatClassList?.[0]?.seatType ?? 2
  }

  passengers.value = await queryPassengers()
})

async function submit() {
  if (!ticket.value) {
    ElMessage.warning("请先选择车次")
    router.push({ name: "ticketSearch" })
    return
  }

  if (!form.passengerId) {
    ElMessage.warning("请选择乘车人")
    return
  }

  loading.value = true
  try {
    const result = await purchaseTicket({
      trainId: ticket.value.trainId,
      departure: ticket.value.departure,
      arrival: ticket.value.arrival,
      passengers: [
        {
          passengerId: form.passengerId,
          seatType: form.seatType,
          ticketType: form.ticketType
        }
      ],
      chooseSeats: form.chooseSeat ? [form.chooseSeat] : undefined
    })
    orderSn.value = result.orderSn
    purchaseResult.value = result.ticketOrderDetailResults || []
    ElMessage.success("下单成功")
  } finally {
    loading.value = false
  }
}

function goPay() {
  router.push({
    name: "payment",
    params: { orderSn: orderSn.value },
    query: { amount: String(totalAmount.value) }
  })
}
</script>

<template>
  <section class="purchase-page">
    <el-card shadow="never">
      <template #header>车次信息</template>
      <el-descriptions v-if="ticket" :column="3" border>
        <el-descriptions-item label="车次">{{ ticket.trainNumber }}</el-descriptions-item>
        <el-descriptions-item label="出发站">{{ ticket.departure }}</el-descriptions-item>
        <el-descriptions-item label="到达站">{{ ticket.arrival }}</el-descriptions-item>
        <el-descriptions-item label="出发时间">{{ ticket.departureTime }}</el-descriptions-item>
        <el-descriptions-item label="到达时间">{{ ticket.arrivalTime }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="请先从车票查询页选择车次" />
    </el-card>

    <el-card shadow="never">
      <template #header>乘车人与座席</template>
      <el-form class="purchase-form" :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="乘车人">
          <el-select v-model="form.passengerId" placeholder="选择乘车人">
            <el-option
              v-for="passenger in passengers"
              :key="passenger.id"
              :label="`${passenger.realName} / ${passenger.idCard}`"
              :value="passenger.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="座席类型">
          <el-select v-model="form.seatType">
            <el-option
              v-for="seat in seatOptions"
              :key="seat.value"
              :label="seat.label"
              :value="seat.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="票种">
          <el-select v-model="form.ticketType">
            <el-option label="成人票" :value="0" />
            <el-option label="儿童票" :value="1" />
            <el-option label="学生票" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="指定座位">
          <el-input v-model="form.chooseSeat" placeholder="可选，例如 012A" />
        </el-form-item>
        <el-button :loading="loading" type="primary" native-type="submit">提交订单</el-button>
      </el-form>
    </el-card>

    <el-card v-if="orderSn" shadow="never">
      <template #header>下单结果</template>
      <el-result icon="success" title="订单创建成功" :sub-title="`订单号：${orderSn}`">
        <template #extra>
          <el-button type="primary" @click="goPay">去支付 ¥{{ (totalAmount / 100).toFixed(2) }}</el-button>
          <el-button @click="router.push({ name: 'orders' })">查看订单</el-button>
        </template>
      </el-result>
      <el-table class="desktop-result-table" :data="purchaseResult">
        <el-table-column prop="realName" label="乘车人" />
        <el-table-column prop="carriageNumber" label="车厢" />
        <el-table-column prop="seatNumber" label="座位" />
        <el-table-column prop="amount" label="金额">
          <template #default="{ row }">¥{{ (row.amount / 100).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
      <div class="mobile-result-list">
        <div v-for="item in purchaseResult" :key="`${item.realName}_${item.carriageNumber}_${item.seatNumber}`">
          <span>{{ item.realName }}</span>
          <strong>{{ item.carriageNumber }}车 {{ item.seatNumber }}</strong>
          <em>¥{{ (item.amount / 100).toFixed(2) }}</em>
        </div>
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.purchase-page {
  display: grid;
  gap: 18px;
}

.purchase-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr)) auto;
  gap: 16px;
  align-items: end;
}

.purchase-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.mobile-result-list {
  display: none;
}

@media (max-width: 980px) {
  .purchase-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .purchase-page {
    gap: 12px;
  }

  .purchase-form .el-button {
    width: 100%;
  }

  .purchase-page :deep(.el-result__extra) {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .purchase-page :deep(.el-result__extra .el-button) {
    width: 100%;
  }

  .desktop-result-table {
    display: none;
  }

  .mobile-result-list {
    display: grid;
    gap: 8px;
  }

  .mobile-result-list div {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 4px 12px;
    padding: 10px;
    border: 1px solid #dbe3ec;
    border-radius: 8px;
  }

  .mobile-result-list span {
    color: #64748b;
  }

  .mobile-result-list strong {
    color: #123c69;
    font-weight: 700;
  }

  .mobile-result-list em {
    grid-row: 1 / span 2;
    grid-column: 2;
    align-self: center;
    color: #111827;
    font-style: normal;
    font-weight: 700;
  }
}
</style>
