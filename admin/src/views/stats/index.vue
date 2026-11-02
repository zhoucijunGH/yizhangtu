<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api'
import { CategoryPieChart, RegionBarChart } from '@/components/charts'
import type { StatsOverview } from '@/types'
import { fmtRate, today } from '@/utils/format'

const loading = ref(false)
const overview = ref<StatsOverview>({ regionStats: [], categoryDist: [] })

async function load() {
  loading.value = true
  try {
    overview.value = (await api.stats.overview()) || { regionStats: [], categoryDist: [] }
  } finally {
    loading.value = false
  }
}

const regionRows = computed(() => overview.value.regionStats || [])
const categoryRows = computed(() => overview.value.categoryDist || [])

// 汇总卡片
const totalPoints = computed(() => regionRows.value.reduce((s, r) => s + Number(r.pointCount || 0), 0))
const totalAlarms = computed(() => regionRows.value.reduce((s, r) => s + Number(r.alarmCount || 0), 0))
const totalPending = computed(() => regionRows.value.reduce((s, r) => s + Number(r.unprocessed || 0), 0))
const avgCloseRate = computed(() => {
  const rows = regionRows.value
  if (!rows.length) return 0
  return rows.reduce((s, r) => s + Number(r.closeRate || 0), 0) / rows.length
})

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>区域统计</h3>
      <div class="page-header-desc">示范区域指标总览与点位分类分布（数据截至 {{ today() }}）</div>
    </div>

    <div v-loading="loading">
      <!-- 汇总指标卡 -->
      <div class="kpi-row">
        <div class="page-card kpi-card">
          <div class="kpi-label">点位总数</div>
          <div class="kpi-value">{{ totalPoints }}</div>
          <div class="kpi-sub">覆盖 {{ regionRows.length }} 个示范区域</div>
        </div>
        <div class="page-card kpi-card">
          <div class="kpi-label">累计告警</div>
          <div class="kpi-value">{{ totalAlarms }}</div>
          <div class="kpi-sub">含历史与实时模拟数据流</div>
        </div>
        <div class="page-card kpi-card">
          <div class="kpi-label">待处理告警</div>
          <div class="kpi-value">{{ totalPending }}</div>
          <div class="kpi-sub">需前往告警中心办理</div>
        </div>
        <div class="page-card kpi-card">
          <div class="kpi-label">平均办结率</div>
          <div class="kpi-value">{{ fmtRate(avgCloseRate) }}</div>
          <div class="kpi-sub">各区域办结率均值</div>
        </div>
      </div>

      <!-- 图表行 -->
      <div class="chart-row">
        <div class="page-card chart-card">
          <div class="chart-title">点位分类分布</div>
          <CategoryPieChart :rows="categoryRows" />
        </div>
        <div class="page-card chart-card">
          <div class="chart-title">各区域告警数</div>
          <RegionBarChart :rows="regionRows" value-key="alarmCount" unit="条" />
        </div>
      </div>

      <!-- 区域指标表 -->
      <div class="page-card">
        <div class="table-head">
          <div class="chart-title">区域指标明细</div>
          <el-button :icon="Refresh" size="small" @click="load">刷新</el-button>
        </div>
        <el-table :data="regionRows" stripe style="width: 100%">
          <el-table-column prop="region" label="示范区域" min-width="200" />
          <el-table-column prop="parentName" label="行政区" width="120">
            <template #default="{ row }">{{ row.parentName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="pointCount" label="点位数" width="100" align="center" />
          <el-table-column prop="alarmCount" label="告警数" width="100" align="center" />
          <el-table-column prop="unprocessed" label="待处理" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.unprocessed > 0" type="danger" size="small">{{ row.unprocessed }}</el-tag>
              <span v-else style="color: var(--yzt-text-sub)">0</span>
            </template>
          </el-table-column>
          <el-table-column label="办结率" min-width="180">
            <template #default="{ row }">
              <div class="rate-cell">
                <el-progress
                  :percentage="Number(row.closeRate || 0)"
                  :stroke-width="10"
                  :color="Number(row.closeRate) >= 80 ? '#2f9e62' : Number(row.closeRate) >= 50 ? '#e6a23c' : '#f56c6c'"
                />
                <span class="rate-text">{{ fmtRate(row.closeRate) }}</span>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无区域数据" />
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  padding: 16px 18px;
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  padding: 16px 18px;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--yzt-text);
  margin-bottom: 12px;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.rate-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rate-cell .el-progress {
  flex: 1;
}

.rate-text {
  font-size: 13px;
  color: var(--yzt-text);
  font-variant-numeric: tabular-nums;
  width: 52px;
  text-align: right;
}

@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
