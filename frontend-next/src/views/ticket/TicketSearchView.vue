<script setup lang="ts">
import { reactive, ref } from "vue"
import { Calendar, Location, Search } from "@element-plus/icons-vue"
import { searchTickets } from "@/api/ticket"
import type { TicketItem } from "@/types/ticket"

const query = reactive({
  fromStation: "",
  toStation: "",
  departureDate: ""
})

const loading = ref(false)
const tickets = ref<TicketItem[]>([])

async function submit() {
  loading.value = true
  try {
    tickets.value = await searchTickets(query)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="ticket-search">
    <el-card shadow="never">
      <el-form class="search-form" :model="query" @submit.prevent="submit">
        <el-form-item label="出发地">
          <el-input v-model="query.fromStation" :prefix-icon="Location" placeholder="例如 北京南" />
        </el-form-item>
        <el-form-item label="到达地">
          <el-input v-model="query.toStation" :prefix-icon="Location" placeholder="例如 上海虹桥" />
        </el-form-item>
        <el-form-item label="出发日期">
          <el-date-picker
            v-model="query.departureDate"
            :prefix-icon="Calendar"
            placeholder="选择日期"
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
          <el-tag effect="plain">{{ tickets.length }} 趟车</el-tag>
        </div>
      </template>

      <el-table :data="tickets" empty-text="请输入条件查询车票">
        <el-table-column prop="trainNumber" label="车次" width="120" />
        <el-table-column prop="fromStation" label="出发站" />
        <el-table-column prop="toStation" label="到达站" />
        <el-table-column prop="departureTime" label="出发时间" />
        <el-table-column prop="arrivalTime" label="到达时间" />
        <el-table-column prop="duration" label="历时" />
        <el-table-column label="座席余票" min-width="220">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-for="seat in row.seatTypes" :key="seat.seatType" class="status-tag">
                {{ seat.seatTypeName }} {{ seat.remaining }}
              </el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <el-button type="primary" link>预订</el-button>
        </el-table-column>
      </el-table>
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
