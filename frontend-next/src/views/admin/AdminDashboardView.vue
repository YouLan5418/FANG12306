<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { Plus, Refresh, Search, View } from "@element-plus/icons-vue"
import * as echarts from "echarts/core"
import { BarChart, LineChart, PieChart } from "echarts/charts"
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components"
import { CanvasRenderer } from "echarts/renderers"
import {
  calibrateRemainingTickets,
  cancelAdminOrder,
  closeTimeoutOrders,
  compensateAbnormalOrders,
  getAdminDashboard,
  getAdminOrderStats,
  getOrderTrend,
  getRevenueTrend,
  getTrainDetail,
  listAdminOrders,
  listAdminTrains,
  listAdminUsers,
  publishTrain,
  toggleAdminUserStatus
} from "@/api/admin"
import { listStations } from "@/api/ticket"
import type {
  AdminDashboard,
  AdminOrder,
  AdminTrain,
  AdminTrainDetail,
  AdminTrend,
  AdminUser,
  OperationTask,
  TrainPublishReq
} from "@/types/admin"
import type { StationOption } from "@/types/ticket"

const activeTab = ref("dashboard")
const dashboardLoading = ref(false)
const tableLoading = ref(false)
const opsLoading = ref(false)
const publishLoading = ref(false)
const detailLoading = ref(false)
const stationLoading = ref(false)

const dashboard = ref<AdminDashboard>({})
const orderTrend = ref<AdminTrend>({ labels: [], values: [] })
const revenueTrend = ref<AdminTrend>({ labels: [], values: [] })
const orderStatus = ref<Record<string, number>>({})
const users = ref<AdminUser[]>([])
const orders = ref<AdminOrder[]>([])
const trains = ref<AdminTrain[]>([])
const stations = ref<StationOption[]>([])
const latestOperation = ref<OperationTask | null>(null)
const trainDetail = ref<AdminTrainDetail | null>(null)

const orderChartRef = ref<HTMLDivElement>()
const revenueChartRef = ref<HTMLDivElement>()
const statusChartRef = ref<HTMLDivElement>()
type EChartsInstance = echarts.ECharts
let orderChart: EChartsInstance | null = null
let revenueChart: EChartsInstance | null = null
let statusChart: EChartsInstance | null = null

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer
])

const userQuery = reactive({ page: 1, size: 10, total: 0, keyword: "" })
const orderQuery = reactive({
  page: 1,
  size: 10,
  total: 0,
  keyword: "",
  status: undefined as number | undefined
})
const trainQuery = reactive({ page: 1, size: 10, total: 0, keyword: "" })

const publishDrawer = ref(false)
const detailDrawer = ref(false)
const publishStep = ref(0)
const publishForm = reactive({
  trainNumber: "",
  trainType: 0,
  trainTag: "复兴号",
  trainBrand: "CR400",
  saleTime: "",
  saleStatus: 0,
  stations: [
    { stationId: undefined as number | undefined, stationName: "", region: "", arrivalTime: "", departureTime: "", stopoverTime: 0 },
    { stationId: undefined as number | undefined, stationName: "", region: "", arrivalTime: "", departureTime: "", stopoverTime: 0 }
  ],
  seatPrices: [{ seatType: 2, priceYuan: 553 }],
  carriages: [{ carriageNumber: "01", carriageType: 2, seatType: 2, seatCount: 100 }]
})

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

const seatTypeText: Record<number, string> = {
  0: "商务座",
  1: "一等座",
  2: "二等座",
  3: "硬座",
  4: "硬卧",
  5: "软卧"
}

const trainTypeText: Record<number, string> = {
  0: "高铁",
  1: "动车",
  2: "普快",
  3: "特快"
}

const carriageTypeText: Record<number, string> = {
  0: "商务车厢",
  1: "一等座车厢",
  2: "二等座车厢",
  3: "硬座车厢",
  4: "卧铺车厢"
}

const groupedStations = computed(() => {
  const groups = new Map<string, StationOption[]>()
  stations.value.forEach((station) => {
    const regionName = station.regionName || station.region || "其他"
    const list = groups.get(regionName) || []
    list.push(station)
    groups.set(regionName, list)
  })
  return Array.from(groups.entries()).map(([regionName, options]) => ({
    regionName,
    options: options.sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
  }))
})

async function renderCharts() {
  if (!orderChartRef.value || !revenueChartRef.value || !statusChartRef.value) return
  orderChart = orderChart || echarts.init(orderChartRef.value)
  revenueChart = revenueChart || echarts.init(revenueChartRef.value)
  statusChart = statusChart || echarts.init(statusChartRef.value)

  orderChart.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 34, right: 16, top: 28, bottom: 28 },
    xAxis: { type: "category", data: orderTrend.value.labels },
    yAxis: { type: "value" },
    series: [{ type: "line", smooth: true, data: orderTrend.value.values, areaStyle: {} }]
  })
  revenueChart.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 34, right: 16, top: 28, bottom: 28 },
    xAxis: { type: "category", data: revenueTrend.value.labels },
    yAxis: { type: "value" },
    series: [{ type: "bar", data: revenueTrend.value.values, itemStyle: { borderRadius: [4, 4, 0, 0] } }]
  })
  statusChart.setOption({
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [
      {
        type: "pie",
        radius: ["42%", "68%"],
        center: ["50%", "44%"],
        data: Object.entries(orderStatus.value).map(([key, value]) => ({
          name: orderStatusText[Number(key)] || `状态 ${key}`,
          value
        }))
      }
    ]
  })
}

function resizeCharts() {
  orderChart?.resize()
  revenueChart?.resize()
  statusChart?.resize()
}

async function loadDashboard() {
  dashboardLoading.value = true
  try {
    const [summary, orders, revenue, status] = await Promise.all([
      getAdminDashboard(),
      getOrderTrend(7),
      getRevenueTrend(7),
      getAdminOrderStats()
    ])
    dashboard.value = summary
    orderTrend.value = orders
    revenueTrend.value = revenue
    orderStatus.value = status.byStatus || {}
    await nextTick()
    await renderCharts()
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

async function loadStations() {
  if (stations.value.length) return
  stationLoading.value = true
  try {
    stations.value = await listStations()
  } finally {
    stationLoading.value = false
  }
}

function addPublishStation() {
  publishForm.stations.push({
    stationId: undefined,
    stationName: "",
    region: "",
    arrivalTime: "",
    departureTime: "",
    stopoverTime: 0
  })
}

function removePublishStation(index: number) {
  if (publishForm.stations.length <= 2) {
    ElMessage.warning("至少需要始发站和终到站")
    return
  }
  publishForm.stations.splice(index, 1)
}

function addSeatPrice() {
  publishForm.seatPrices.push({ seatType: 2, priceYuan: 0 })
}

function addCarriage() {
  const next = String(publishForm.carriages.length + 1).padStart(2, "0")
  publishForm.carriages.push({ carriageNumber: next, carriageType: 2, seatType: 2, seatCount: 100 })
}

function onStationChange(index: number, stationName: string) {
  const selected = stations.value.find((station) => station.name === stationName)
  if (!selected) return
  publishForm.stations[index].stationId = selected.id
  publishForm.stations[index].region = selected.regionName || selected.region || ""
}

async function openPublishDrawer() {
  publishDrawer.value = true
  publishStep.value = 0
  await loadStations()
}

function buildPublishPayload(): TrainPublishReq {
  return {
    trainNumber: publishForm.trainNumber,
    trainType: publishForm.trainType,
    trainTag: publishForm.trainTag,
    trainBrand: publishForm.trainBrand,
    saleTime: publishForm.saleTime,
    saleStatus: publishForm.saleStatus,
    stations: publishForm.stations.map((station) => ({
      stationId: station.stationId,
      stationName: station.stationName,
      region: station.region,
      arrivalTime: station.arrivalTime,
      departureTime: station.departureTime,
      stopoverTime: Number(station.stopoverTime || 0)
    })),
    seatPrices: publishForm.seatPrices.map((item) => ({
      seatType: item.seatType,
      price: Math.round(Number(item.priceYuan || 0) * 100)
    })),
    carriages: publishForm.carriages.map((item) => ({
      carriageNumber: item.carriageNumber,
      carriageType: item.carriageType,
      seatType: item.seatType,
      seatCount: Number(item.seatCount || 0)
    }))
  }
}

async function submitPublishTrain() {
  if (!publishForm.trainNumber || !publishForm.saleTime) {
    ElMessage.warning("请填写车次号和开售时间")
    return
  }
  if (publishForm.stations.some((station) => !station.stationName || !station.departureTime || !station.arrivalTime)) {
    ElMessage.warning("请补全经停站和到发时间")
    return
  }
  publishLoading.value = true
  try {
    await publishTrain(buildPublishPayload())
    ElMessage.success("车次已发布")
    publishDrawer.value = false
    await loadTrains()
  } finally {
    publishLoading.value = false
  }
}

async function openTrainDetail(row: AdminTrain) {
  detailDrawer.value = true
  detailLoading.value = true
  trainDetail.value = null
  try {
    trainDetail.value = await getTrainDetail(row.id)
  } finally {
    detailLoading.value = false
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

onMounted(() => {
  loadDashboard()
  window.addEventListener("resize", resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts)
  orderChart?.dispose()
  revenueChart?.dispose()
  statusChart?.dispose()
})
</script>

<template>
  <section class="admin-page">
    <el-tabs v-model="activeTab" class="admin-tabs" @tab-change="onTabChange">
      <el-tab-pane label="仪表盘" name="dashboard">
        <div v-loading="dashboardLoading" class="dashboard-stack">
          <div class="metric-grid">
            <el-card v-for="card in metricCards" :key="card.label" shadow="never">
              <p>{{ card.label }}</p>
              <strong>{{ card.value }}</strong>
            </el-card>
          </div>
          <div class="chart-grid">
            <el-card shadow="never">
              <template #header>近 7 日订单趋势</template>
              <div ref="orderChartRef" class="chart-box" />
            </el-card>
            <el-card shadow="never">
              <template #header>近 7 日支付趋势</template>
              <div ref="revenueChartRef" class="chart-box" />
            </el-card>
            <el-card shadow="never">
              <template #header>订单状态分布</template>
              <div ref="statusChartRef" class="chart-box" />
            </el-card>
          </div>
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
            <el-button :icon="Search" :loading="tableLoading" type="primary" @click="loadOrders">查询</el-button>
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
                <el-button v-if="row.status === 0" type="danger" link @click="cancelOrder(row)">关闭</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="users">
        <el-card shadow="never">
          <div class="toolbar two-columns">
            <el-input v-model="userQuery.keyword" clearable placeholder="用户名 / 姓名" />
            <el-button :icon="Search" :loading="tableLoading" type="primary" @click="loadUsers">查询</el-button>
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
            <el-button :icon="Search" :loading="tableLoading" type="primary" @click="loadTrains">查询</el-button>
            <el-button :icon="Plus" type="success" @click="openPublishDrawer">发布车次</el-button>
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
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button :icon="View" type="primary" link @click="openTrainDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="运营兜底" name="ops">
        <el-card shadow="never">
          <div class="ops-actions">
            <el-button :icon="Refresh" :loading="opsLoading" type="primary" @click="runOperation('timeout')">
              关闭超时订单
            </el-button>
            <el-button :icon="Refresh" :loading="opsLoading" type="warning" @click="runOperation('calibrate')">
              校准 Redis 余票
            </el-button>
            <el-button :icon="Refresh" :loading="opsLoading" type="success" @click="runOperation('compensate')">
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

    <el-drawer v-model="publishDrawer" title="发布车次" size="720px" class="admin-drawer">
      <el-steps :active="publishStep" finish-status="success" simple>
        <el-step title="基础信息" />
        <el-step title="站点与票价" />
        <el-step title="车厢" />
      </el-steps>

      <div v-if="publishStep === 0" class="drawer-section">
        <el-form label-width="92px">
          <el-form-item label="车次号">
            <el-input v-model="publishForm.trainNumber" placeholder="G1001" />
          </el-form-item>
          <el-form-item label="车次类型">
            <el-select v-model="publishForm.trainType">
              <el-option v-for="(label, value) in trainTypeText" :key="value" :label="label" :value="Number(value)" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="publishForm.trainTag" />
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="publishForm.trainBrand" />
          </el-form-item>
          <el-form-item label="开售时间">
            <el-date-picker v-model="publishForm.saleTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
          <el-form-item label="销售状态">
            <el-radio-group v-model="publishForm.saleStatus">
              <el-radio-button :label="0">可售</el-radio-button>
              <el-radio-button :label="1">停售</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>

      <div v-else-if="publishStep === 1" class="drawer-section">
        <div class="section-title">
          <span>经停站</span>
          <el-button :icon="Plus" link type="primary" @click="addPublishStation">添加站点</el-button>
        </div>
        <div v-for="(station, index) in publishForm.stations" :key="index" class="station-row">
          <el-select
            v-model="station.stationName"
            :loading="stationLoading"
            filterable
            placeholder="选择车站"
            @change="onStationChange(index, $event)"
          >
            <el-option-group v-for="group in groupedStations" :key="group.regionName" :label="group.regionName">
              <el-option
                v-for="item in group.options"
                :key="item.id || item.code || item.name"
                :label="`${item.name} ${item.spell || ''}`"
                :value="item.name"
              />
            </el-option-group>
          </el-select>
          <el-input v-model="station.region" placeholder="地区" />
          <el-date-picker v-model="station.arrivalTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="到达时间" />
          <el-date-picker v-model="station.departureTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="发车时间" />
          <el-input-number v-model="station.stopoverTime" :min="0" controls-position="right" />
          <el-button type="danger" link @click="removePublishStation(index)">移除</el-button>
        </div>

        <div class="section-title">
          <span>座席票价</span>
          <el-button :icon="Plus" link type="primary" @click="addSeatPrice">添加票价</el-button>
        </div>
        <div v-for="(item, index) in publishForm.seatPrices" :key="index" class="price-row">
          <el-select v-model="item.seatType">
            <el-option v-for="(label, value) in seatTypeText" :key="value" :label="label" :value="Number(value)" />
          </el-select>
          <el-input-number v-model="item.priceYuan" :min="0" :precision="2" controls-position="right" />
          <span>元</span>
          <el-button v-if="publishForm.seatPrices.length > 1" type="danger" link @click="publishForm.seatPrices.splice(index, 1)">
            移除
          </el-button>
        </div>
      </div>

      <div v-else class="drawer-section">
        <div class="section-title">
          <span>车厢配置</span>
          <el-button :icon="Plus" link type="primary" @click="addCarriage">添加车厢</el-button>
        </div>
        <div v-for="(item, index) in publishForm.carriages" :key="index" class="carriage-row">
          <el-input v-model="item.carriageNumber" placeholder="车厢号" />
          <el-select v-model="item.carriageType">
            <el-option v-for="(label, value) in carriageTypeText" :key="value" :label="label" :value="Number(value)" />
          </el-select>
          <el-select v-model="item.seatType">
            <el-option v-for="(label, value) in seatTypeText" :key="value" :label="label" :value="Number(value)" />
          </el-select>
          <el-input-number v-model="item.seatCount" :min="1" controls-position="right" />
          <el-button v-if="publishForm.carriages.length > 1" type="danger" link @click="publishForm.carriages.splice(index, 1)">
            移除
          </el-button>
        </div>
      </div>

      <template #footer>
        <el-button :disabled="publishStep === 0" @click="publishStep--">上一步</el-button>
        <el-button v-if="publishStep < 2" type="primary" @click="publishStep++">下一步</el-button>
        <el-button v-else :loading="publishLoading" type="success" @click="submitPublishTrain">发布</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="detailDrawer" title="车次详情" size="680px" class="admin-drawer">
      <div v-loading="detailLoading">
        <el-descriptions v-if="trainDetail" :column="2" border>
          <el-descriptions-item label="车次">{{ trainDetail.train.trainNumber }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ trainDetail.train.trainBrand }}</el-descriptions-item>
          <el-descriptions-item label="始发站">{{ trainDetail.train.startStation }}</el-descriptions-item>
          <el-descriptions-item label="终到站">{{ trainDetail.train.endStation }}</el-descriptions-item>
          <el-descriptions-item label="开售时间">{{ trainDetail.train.saleTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ trainDetail.train.saleStatus === 0 ? "可售" : "停售" }}</el-descriptions-item>
        </el-descriptions>

        <h3>经停站</h3>
        <el-table :data="trainDetail?.stations || []" size="small" empty-text="暂无站点">
          <el-table-column prop="departure" label="出发站" />
          <el-table-column prop="arrival" label="到达站" />
          <el-table-column prop="departureTime" label="发车时间" min-width="160" />
          <el-table-column prop="arrivalTime" label="到达时间" min-width="160" />
        </el-table>

        <h3>票价</h3>
        <el-table :data="trainDetail?.prices || []" size="small" empty-text="暂无票价">
          <el-table-column prop="departure" label="出发站" />
          <el-table-column prop="arrival" label="到达站" />
          <el-table-column label="座席">
            <template #default="{ row }">{{ seatTypeText[row.seatType] || row.seatType }}</template>
          </el-table-column>
          <el-table-column label="价格">
            <template #default="{ row }">¥{{ (row.price / 100).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
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

.dashboard-stack {
  display: grid;
  gap: 16px;
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

.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 16px;
}

.chart-box {
  height: 260px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px auto;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar.two-columns {
  grid-template-columns: minmax(220px, 1fr) auto;
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

.drawer-section {
  display: grid;
  gap: 16px;
  padding-top: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
}

.station-row,
.price-row,
.carriage-row {
  display: grid;
  gap: 10px;
  align-items: center;
}

.station-row {
  grid-template-columns: minmax(140px, 1.1fr) minmax(100px, 0.8fr) minmax(170px, 1fr) minmax(170px, 1fr) 96px auto;
}

.price-row {
  grid-template-columns: 160px 160px 40px auto;
}

.carriage-row {
  grid-template-columns: 120px 160px 140px 120px auto;
}

h3 {
  margin: 22px 0 12px;
  font-size: 16px;
}

@media (max-width: 980px) {
  .metric-grid,
  .chart-grid,
  .toolbar,
  .toolbar.two-columns,
  .station-row,
  .price-row,
  .carriage-row {
    grid-template-columns: 1fr;
  }

  .chart-box {
    height: 220px;
  }
}

@media (max-width: 760px) {
  .admin-tabs {
    padding: 12px;
  }

  :deep(.admin-drawer) {
    width: 100% !important;
  }
}
</style>
