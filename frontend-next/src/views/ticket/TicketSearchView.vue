<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { Calendar, Location, Search } from "@element-plus/icons-vue"
import { listStations, searchTickets } from "@/api/ticket"
import type { StationOption, TicketItem } from "@/types/ticket"

const router = useRouter()
const query = reactive({
  departureDate: "",
  departure: "",
  arrival: "",
  current: 1,
  size: 10
})

const loading = ref(false)
const stationLoading = ref(false)
const stations = ref<StationOption[]>([])
const tickets = ref<TicketItem[]>([])
const total = ref(0)

const seatTypeText: Record<number, string> = {
  0: "商务座",
  1: "一等座",
  2: "二等座",
  3: "硬座",
  4: "硬卧",
  5: "软卧"
}

const groupedStations = computed(() => {
  const groups = new Map<string, StationOption[]>()
  stations.value.forEach((station) => {
    const regionName = station.regionName || station.region || "其他"
    const list = groups.get(regionName) || []
    list.push(station)
    groups.set(regionName, list)
  })

  return Array.from(groups.entries())
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN"))
    .map(([regionName, options]) => ({
      regionName,
      options: options.sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
    }))
})

async function loadStations() {
  stationLoading.value = true
  try {
    stations.value = await listStations()
  } finally {
    stationLoading.value = false
  }
}

async function submit(resetPage = true) {
  if (resetPage) query.current = 1
  loading.value = true
  try {
    const page = await searchTickets({ ...query, departureDate: query.departureDate || undefined })
    tickets.value = page.trainList || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function reserve(row: TicketItem) {
  sessionStorage.setItem("index12306.selectedTicket", JSON.stringify(row))
  router.push({ name: "purchaseConfirm" })
}

onMounted(loadStations)
</script>

<template>
  <section class="ticket-search">
    <el-card shadow="never">
      <el-form class="search-form" :model="query" @submit.prevent="submit()">
        <el-form-item label="出发地">
          <el-select
            v-model="query.departure"
            :loading="stationLoading"
            clearable
            filterable
            placeholder="选择出发站"
          >
            <template #prefix>
              <el-icon><Location /></el-icon>
            </template>
            <el-option-group
              v-for="group in groupedStations"
              :key="group.regionName"
              :label="group.regionName"
            >
              <el-option
                :key="`${group.regionName}-all-departure`"
                :label="`${group.regionName} 全部车站`"
                :value="group.regionName"
              />
              <el-option
                v-for="station in group.options"
                :key="station.code || station.name"
                :label="`${station.name} ${station.spell || ''} ${station.code || ''}`"
                :value="station.name"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="到达地">
          <el-select
            v-model="query.arrival"
            :loading="stationLoading"
            clearable
            filterable
            placeholder="选择到达站"
          >
            <template #prefix>
              <el-icon><Location /></el-icon>
            </template>
            <el-option-group
              v-for="group in groupedStations"
              :key="group.regionName"
              :label="group.regionName"
            >
              <el-option
                :key="`${group.regionName}-all-arrival`"
                :label="`${group.regionName} 全部车站`"
                :value="group.regionName"
              />
              <el-option
                v-for="station in group.options"
                :key="station.code || station.name"
                :label="`${station.name} ${station.spell || ''} ${station.code || ''}`"
                :value="station.name"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="乘车日期">
          <el-date-picker
            v-model="query.departureDate"
            :prefix-icon="Calendar"
            placeholder="选择乘车日期（可选）"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-button :icon="Search" :loading="loading" type="primary" native-type="submit">
          查询车票
        </el-button>
      </el-form>
    </el-card>

    <el-card class="result-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>查询结果</span>
          <el-tag effect="plain">{{ total || tickets.length }} 趟车</el-tag>
        </div>
      </template>

      <el-table :data="tickets" empty-text="请输入条件查询车票">
        <el-table-column prop="trainNumber" label="车次" width="120" />
        <el-table-column prop="departure" label="出发站" />
        <el-table-column prop="arrival" label="到达站" />
        <el-table-column prop="departureTime" label="出发时间" />
        <el-table-column prop="arrivalTime" label="到达时间" />
        <el-table-column prop="duration" label="历时" />
        <el-table-column label="座席余票" min-width="220">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-for="seat in row.seatClassList" :key="seat.seatType" class="status-tag">
                {{ seatTypeText[seat.seatType] || `座席 ${seat.seatType}` }} {{ seat.quantity }}
              </el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="reserve(row)">预订</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="submit(false)"
      />
    </el-card>
  </section>
</template>

<style scoped>
.ticket-search {
  display: grid;
  gap: 18px;
}

.search-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr)) auto;
  gap: 16px;
  align-items: end;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.result-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 920px) {
  .search-form {
    grid-template-columns: 1fr;
  }
}
</style>
