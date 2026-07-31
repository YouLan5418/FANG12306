<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import {
  calibrateRemainingTickets,
  cancelAdminOrder,
  closeTimeoutOrders,
  compensateAbnormalOrders,
  getAdminDashboard,
  listAdminOrders,
  listAdminTrains,
  listAdminUsers,
  toggleAdminUserStatus
} from "@/api/admin"
import type { AdminDashboard, AdminOrder, AdminTrain, AdminUser, OperationTask } from "@/types/admin"

const activeTab = ref("dashboard")
const dashboardLoading = ref(false)
const tableLoading = ref(false)
const opsLoading = ref(false)
const dashboard = ref<AdminDashboard>({})
const users = ref<AdminUser[]>([])
const orders = ref<AdminOrder[]>([])
const trains = ref<AdminTrain[]>([])
const latestOperation = ref<OperationTask | null>(null)

const userQuery = reactive({ page: 1, size: 10, total: 0, keyword: "" })
const orderQuery = reactive({
  page: 1,
  size: 10,
  total: 0,
  keyword: "",
  status: undefined as number | undefined
})
const trainQuery = reactive({ page: 1, size: 10, total: 0, keyword: "" })

const metricCards = computed(() => [
  { label: "用户数", value: dashboard.value.userCount ?? dashboard.value.totalUserCount ?? 0 },
  { label: "订单数", value: dashboard.value.orderCount ?? dashboard.value.totalOrderCount ?? 0 },
  { label: "车次数", value: dashboard.value.trainCount ?? dashboard.value.totalTrainCount ?? 0 },
  { label: "今日订单", value: dashboard.value.todayOrderCount ?? 0 }
])

const orderStatusText: Record<number, string> = {
  0: "待支付",
  10: "已支付",
  11: "部分退款",
  12: "全部退款",
  20: "已完成",
  30: "已关闭"
}

async function loadDashboard() {
  dashboardLoading.value = true
  try {
    dashboard.value = await getAdminDashboard()
  } finally {
    dashboardLoading.value = false
  }
}

async function loadUsers() {
  tableLoading.value = true
  try {
    const page = await listAdminUsers(userQuery)
    users.value = page.records || []
    userQuery.total = page.total || 0
  } finally {
    tableLoading.value = false
  }
}

async function loadOrders() {
  tableLoading.value = true
  try {
    const page = await listAdminOrders(orderQuery)
    orders.value = page.records || []
    orderQuery.total = page.total || 0
  } finally {
    tableLoading.value = false
  }
}

async function loadTrains() {
  tableLoading.value = true
  try {
    const page = await listAdminTrains(trainQuery)
    trains.value = page.records || []
    trainQuery.total = page.total || 0
  } finally {
    tableLoading.value = false
  }
}

async function toggleUser(row: AdminUser) {
  await toggleAdminUserStatus(row.id)
  ElMessage.success("用户角色已切换")
  await loadUsers()
}

async function cancelOrder(row: AdminOrder) {
  await ElMessageBox.confirm(`确定关闭订单 ${row.orderSn} 吗？`, "关闭订单", { type: "warning" })
  await cancelAdminOrder(row.orderSn)
  ElMessage.success("订单已关闭")
  await loadOrders()
}

async function runOperation(type: "timeout" | "calibrate" | "compensate") {
  opsLoading.value = true
  try {
    if (type === "timeout") {
      latestOperation.value = await closeTimeoutOrders(30)
    } else if (type === "calibrate") {
      latestOperation.value = await calibrateRemainingTickets()
    } else {
      latestOperation.value = await compensateAbnormalOrders()
    }
    ElMessage.success("运营任务执行完成")
  } finally {
    opsLoading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === "dashboard") {
    loadDashboard()
  } else if (name === "users") {
    loadUsers()
  } else if (name === "orders") {
    loadOrders()
  } else if (name === "trains") {
    loadTrains()
  }
}

onMounted(loadDashboard)
</script>

<template>
  <section class="admin-page">
    <el-tabs v-model="activeTab" class="admin-tabs" @tab-change="onTabChange">
      <el-tab-pane label="仪表盘" name="dashboard">
        <div v-loading="dashboardLoading" class="metric-grid">
          <el-card v-for="card in metricCards" :key="card.label" shadow="never">
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="订单管理" name="orders">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="orderQuery.keyword" clearable placeholder="订单号 / 用户 / 车次" />
            <el-select v-model="orderQuery.status" clearable placeholder="状态">
              <el-option label="待支付" :value="0" />
              <el-option label="已支付" :value="10" />
              <el-option label="部分退款" :value="11" />
              <el-option label="全部退款" :value="12" />
              <el-option label="已完成" :value="20" />
              <el-option label="已关闭" :value="30" />
            </el-select>
            <el-button :loading="tableLoading" type="primary" @click="loadOrders">查询</el-button>
          </div>
          <el-table v-loading="tableLoading" :data="orders" empty-text="暂无订单">
            <el-table-column prop="orderSn" label="订单号" min-width="170" />
            <el-table-column prop="username" label="用户" width="120" />
            <el-table-column prop="trainNumber" label="车次" width="100" />
            <el-table-column label="行程" min-width="170">
              <template #default="{ row }">{{ row.departure }} → {{ row.arrival }}</template>
            </el-table-column>
            <el-table-column prop="ridingDate" label="乘车日期" width="120" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag>{{ orderStatusText[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" type="danger" link @click="cancelOrder(row)">
                  关闭
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="users">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="userQuery.keyword" clearable placeholder="用户名 / 姓名" />
            <el-button :loading="tableLoading" type="primary" @click="loadUsers">查询</el-button>
          </div>
          <el-table v-loading="tableLoading" :data="users" empty-text="暂无用户">
            <el-table-column prop="id" label="ID" width="120" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="realName" label="姓名" />
            <el-table-column prop="region" label="地区" />
            <el-table-column label="角色" width="110">
              <template #default="{ row }">
                <el-tag :type="row.userType === 1 ? 'success' : 'info'">
                  {{ row.userType === 1 ? "管理员" : "用户" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="toggleUser(row)">切换角色</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="车次管理" name="trains">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="trainQuery.keyword" clearable placeholder="车次 / 起点 / 终点" />
            <el-button :loading="tableLoading" type="primary" @click="loadTrains">查询</el-button>
          </div>
          <el-table v-loading="tableLoading" :data="trains" empty-text="暂无车次">
            <el-table-column prop="trainNumber" label="车次" width="120" />
            <el-table-column prop="trainBrand" label="品牌" />
            <el-table-column prop="startStation" label="始发站" />
            <el-table-column prop="endStation" label="终到站" />
            <el-table-column prop="departureTime" label="发车时间" min-width="160" />
            <el-table-column prop="arrivalTime" label="到达时间" min-width="160" />
            <el-table-column label="销售状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.saleStatus === 0 ? 'success' : 'info'">
                  {{ row.saleStatus === 0 ? "可售" : "停售" }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="运营兜底" name="ops">
        <el-card shadow="never">
          <div class="ops-actions">
            <el-button :loading="opsLoading" type="primary" @click="runOperation('timeout')">
              关闭超时订单
            </el-button>
            <el-button :loading="opsLoading" type="warning" @click="runOperation('calibrate')">
              校准 Redis 余票
            </el-button>
            <el-button :loading="opsLoading" type="success" @click="runOperation('compensate')">
              补偿异常订单
            </el-button>
          </div>
          <el-descriptions v-if="latestOperation" class="ops-result" :column="3" border>
            <el-descriptions-item label="任务">{{ latestOperation.taskName }}</el-descriptions-item>
            <el-descriptions-item label="扫描数">{{ latestOperation.scannedCount }}</el-descriptions-item>
            <el-descriptions-item label="处理数">{{ latestOperation.handledCount }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="选择一个运营任务执行" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.admin-page {
  display: grid;
  gap: 18px;
}

.admin-tabs {
  border-radius: 8px;
  background: #fff;
  padding: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 16px;
}

.metric-grid p {
  margin: 0 0 10px;
  color: #64748b;
}

.metric-grid strong {
  color: #123c69;
  font-size: 30px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px auto;
  gap: 12px;
  margin-bottom: 16px;
}

.ops-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}

.ops-result {
  margin-top: 12px;
}

@media (max-width: 760px) {
  .admin-tabs {
    padding: 12px;
  }

  .metric-grid,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
