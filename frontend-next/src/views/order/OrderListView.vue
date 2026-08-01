<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { ElMessage, ElMessageBox } from "element-plus"
import { cancelTicket } from "@/api/ticket"
import { querySelfOrders } from "@/api/order"
import type { SelfOrderRow } from "@/types/order"

const router = useRouter()
const loading = ref(false)
const rows = ref<SelfOrderRow[]>([])
const page = reactive({
  current: 1,
  size: 10,
  total: 0,
  statusType: undefined as number | undefined
})

const statusText: Record<number, string> = {
  0: "待支付",
  10: "已支付",
  11: "部分退款",
  12: "全部退款",
  20: "已完成",
  30: "已关闭"
}

const statusTypeOptions = [
  { label: "全部", value: undefined },
  { label: "待支付", value: 0 },
  { label: "已支付/含退款", value: 1 },
  { label: "已完成", value: 2 }
]

const groupedOrders = computed(() => {
  const map = new Map<string, SelfOrderRow[]>()
  rows.value.forEach((row) => {
    const group = map.get(row.orderSn) || []
    group.push(row)
    map.set(row.orderSn, group)
  })

  return Array.from(map.entries()).map(([orderSn, details]) => {
    const first = details[0]
    return {
      ...first,
      orderSn,
      details,
      amount: details.reduce((sum, item) => sum + Number(item.amount || 0), 0)
    }
  })
})

async function loadOrders() {
  loading.value = true
  try {
    const result = await querySelfOrders({
      current: page.current,
      size: page.size,
      statusType: page.statusType
    })
    rows.value = result.records || []
    page.total = result.total || 0
  } finally {
    loading.value = false
  }
}

async function cancel(orderSn: string) {
  await ElMessageBox.confirm("确定取消该未支付订单吗？", "取消订单", { type: "warning" })
  await cancelTicket(orderSn)
  ElMessage.success("订单已取消")
  await loadOrders()
}

function goPay(row: { orderSn: string; amount: number }) {
  router.push({
    name: "payment",
    params: { orderSn: row.orderSn },
    query: { amount: String(row.amount) }
  })
}

function goRefund(orderSn: string) {
  router.push({ name: "refund", params: { orderSn } })
}

onMounted(loadOrders)
</script>

<template>
  <section class="order-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="page.statusType" placeholder="订单状态" clearable @change="loadOrders">
          <el-option
            v-for="item in statusTypeOptions"
            :key="String(item.value)"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button :loading="loading" @click="loadOrders">刷新</el-button>
      </div>
    </el-card>

    <el-card class="desktop-orders-card" shadow="never">
      <el-table v-loading="loading" :data="groupedOrders" empty-text="暂无订单">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.details" size="small">
              <el-table-column prop="carriageNumber" label="车厢" />
              <el-table-column prop="seatNumber" label="座位" />
              <el-table-column prop="seatType" label="座席类型" />
              <el-table-column prop="amount" label="金额">
                <template #default="{ row: detail }">¥{{ (detail.amount / 100).toFixed(2) }}</template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="orderSn" label="订单号" min-width="170" />
        <el-table-column prop="trainNumber" label="车次" width="100" />
        <el-table-column label="行程" min-width="180">
          <template #default="{ row }">{{ row.departure }} → {{ row.arrival }}</template>
        </el-table-column>
        <el-table-column prop="ridingDate" label="乘车日期" width="120" />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ (row.amount / 100).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="primary" link @click="goPay(row)">支付</el-button>
            <el-button v-if="row.status === 0" type="danger" link @click="cancel(row.orderSn)">
              取消
            </el-button>
            <el-button v-if="[10, 11].includes(row.status)" type="warning" link @click="goRefund(row.orderSn)">
              退款
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div v-loading="loading" class="mobile-order-list">
      <el-empty v-if="!groupedOrders.length" description="暂无订单" />
      <article v-for="row in groupedOrders" :key="row.orderSn" class="mobile-order-card">
        <div class="mobile-order-head">
          <div>
            <strong>{{ row.trainNumber }}</strong>
            <span>{{ row.departure }} → {{ row.arrival }}</span>
          </div>
          <el-tag>{{ statusText[row.status] || row.status }}</el-tag>
        </div>
        <dl class="mobile-order-meta">
          <div>
            <dt>订单号</dt>
            <dd>{{ row.orderSn }}</dd>
          </div>
          <div>
            <dt>乘车日期</dt>
            <dd>{{ row.ridingDate || "-" }}</dd>
          </div>
          <div>
            <dt>金额</dt>
            <dd>¥{{ (row.amount / 100).toFixed(2) }}</dd>
          </div>
        </dl>
        <div class="mobile-detail-list">
          <div v-for="detail in row.details" :key="`${detail.carriageNumber}_${detail.seatNumber}`">
            <span>{{ detail.carriageNumber }}车 {{ detail.seatNumber }}</span>
            <span>¥{{ (detail.amount / 100).toFixed(2) }}</span>
          </div>
        </div>
        <div class="mobile-order-actions">
          <el-button v-if="row.status === 0" type="primary" @click="goPay(row)">支付</el-button>
          <el-button v-if="row.status === 0" type="danger" plain @click="cancel(row.orderSn)">取消</el-button>
          <el-button v-if="[10, 11].includes(row.status)" type="warning" @click="goRefund(row.orderSn)">
            退款
          </el-button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.order-page {
  display: grid;
  gap: 18px;
}

.toolbar {
  display: flex;
  gap: 12px;
}

.mobile-order-list {
  display: none;
}

@media (max-width: 760px) {
  .toolbar {
    display: grid;
    grid-template-columns: 1fr;
  }

  .desktop-orders-card {
    display: none;
  }

  .mobile-order-list {
    display: grid;
    gap: 12px;
  }

  .mobile-order-card {
    display: grid;
    gap: 12px;
    padding: 14px;
    border: 1px solid #dbe3ec;
    border-radius: 8px;
    background: #fff;
  }

  .mobile-order-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .mobile-order-head div {
    display: grid;
    min-width: 0;
    gap: 4px;
  }

  .mobile-order-head strong {
    color: #123c69;
    font-size: 18px;
  }

  .mobile-order-head span {
    color: #475569;
  }

  .mobile-order-meta {
    display: grid;
    gap: 8px;
    margin: 0;
  }

  .mobile-order-meta div,
  .mobile-detail-list div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  .mobile-order-meta dt,
  .mobile-order-meta dd {
    margin: 0;
  }

  .mobile-order-meta dt {
    flex: 0 0 auto;
    color: #64748b;
  }

  .mobile-order-meta dd {
    min-width: 0;
    overflow-wrap: anywhere;
    text-align: right;
  }

  .mobile-detail-list {
    display: grid;
    gap: 6px;
    padding-top: 10px;
    color: #475569;
    border-top: 1px dashed #dbe3ec;
    font-size: 13px;
  }

  .mobile-order-actions {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(96px, 1fr));
    gap: 8px;
  }
}
</style>
