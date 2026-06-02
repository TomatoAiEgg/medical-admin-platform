<script setup lang="ts">
import type { Department, DoctorMonitorSummary, PatientMonitorSummary, PatientProfile, RegistrationOrder } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import StatusTag from '../_components/StatusTag.vue';
import { getRegistrationStatusMeta } from '../_utils/status';
import { toPercent } from '../_utils/format';
import { departmentName, loadDepartments, loadPatients, patientName } from '../_utils/lookup';
import { loadMedicalDashboardSnapshot } from './dashboardData';

const loading = ref(false);
const metrics = ref<Record<string, unknown>>({});
const orders = ref<RegistrationOrder[]>([]);
const doctors = ref<DoctorMonitorSummary[]>([]);
const patients = ref<PatientMonitorSummary[]>([]);
const departments = ref<Department[]>([]);
const patientLookups = ref<PatientProfile[]>([]);

const statusKeys = ['PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'NO_SHOW'];

const metricCards = computed(() => [
  { title: '今日挂号数', value: metricValue(metrics.value.todayRegistrationCount, orders.value.length), desc: '当前业务日期订单量', tone: 'primary' as const, icon: 'Tickets' },
  { title: '待确认订单', value: metricValue(metrics.value.pendingConfirmCount, countStatus('PENDING_CONFIRM')), desc: '需要人工或系统确认', tone: 'warning' as const, icon: 'Clock' },
  { title: '异常订单', value: metricValue(firstExistingMetric(['openExceptionCount', 'unhandledExceptionCount']), countAbnormalOrders()), desc: '爽约、过期或规则命中', tone: 'danger' as const, icon: 'WarningFilled' },
  { title: '号源剩余率', value: `${slotRemainingRate.value}%`, desc: '按医生监控号源估算', tone: 'success' as const, icon: 'Calendar' },
  { title: '过期订单', value: metricValue(firstExistingMetric(['expiredRegistrationCount', 'expiredCount']), countStatus('EXPIRED')), desc: '建议执行过期同步', tone: 'info' as const, icon: 'CircleCloseFilled' },
  { title: 'RAG 低置信度', value: metricValue(metrics.value.lowConfidenceRetrievalCount, 0), desc: '知识库检索质量关注', tone: 'warning' as const, icon: 'Collection' },
]);

const slotRemainingRate = computed(() => {
  const capacity = doctors.value.reduce((sum, item) => sum + Number(item.slot_capacity || 0), 0);
  const remaining = doctors.value.reduce((sum, item) => sum + Number(item.remaining_slots || 0), 0);
  return toPercent(remaining, capacity);
});

const statusRows = computed(() => {
  const map = new Map<string, number>();
  orders.value.forEach(item => map.set(item.status, (map.get(item.status) || 0) + 1));
  return statusKeys.map((status) => {
    const meta = getRegistrationStatusMeta(status);
    const count = map.get(status) || 0;
    return { status, label: meta.label, count, percent: toPercent(count, orders.value.length) };
  }).filter(item => item.count > 0 || ['PENDING_CONFIRM', 'BOOKED', 'COMPLETED', 'EXPIRED'].includes(item.status));
});

const departmentRows = computed(() => {
  const map = new Map<string, { departmentCode: string; department: string; count: number; pending: number; completed: number }>();
  orders.value.forEach((item) => {
    const code = item.department_code || '';
    const department = departmentName(code, departments.value);
    const row = map.get(code) || { departmentCode: code, department, count: 0, pending: 0, completed: 0 };
    row.count += 1;
    if (['PENDING_CONFIRM', 'BOOKED', 'CONFIRMED', 'RESCHEDULED'].includes(item.status))
      row.pending += 1;
    if (item.status === 'COMPLETED')
      row.completed += 1;
    map.set(code, row);
  });
  return Array.from(map.values()).sort((a, b) => b.count - a.count).slice(0, 6);
});

const topDoctors = computed(() => [...doctors.value]
  .sort((a, b) => Number(b.registration_count || 0) - Number(a.registration_count || 0))
  .slice(0, 6));

const riskPatients = computed(() => [...patients.value]
  .sort((a, b) => ((b.duplicate_risk_count || 0) + (b.no_show_count || 0)) - ((a.duplicate_risk_count || 0) + (a.no_show_count || 0)))
  .slice(0, 6));

const recentOrders = computed(() => orders.value.slice(0, 8));

const todoRows = computed(() => [
  { type: '订单', title: '待确认订单需要处理', count: countStatus('PENDING_CONFIRM'), path: '/medical/registration' },
  { type: '异常', title: '异常订单需要核查', count: countAbnormalOrders(), path: '/medical/exception' },
  { type: '知识库', title: '低置信度检索样本待复盘', count: Number(metrics.value.lowConfidenceRetrievalCount || 0), path: '/medical/knowledge' },
].filter(item => item.count > 0));

function metricValue(value: unknown, fallback: number): string | number {
  if (typeof value === 'number' || typeof value === 'string')
    return value;
  return fallback;
}

function firstExistingMetric(keys: string[]) {
  for (const key of keys) {
    const value = metrics.value[key];
    if (value !== undefined && value !== null)
      return value;
  }
  return undefined;
}

function countStatus(status: string) {
  return orders.value.filter(item => item.status === status).length;
}

function countAbnormalOrders() {
  return orders.value.filter(item => ['EXPIRED', 'NO_SHOW'].includes(item.status)).length;
}

async function loadDashboard(force = false) {
  if (loading.value)
    return;
  loading.value = true;
  try {
    const [snapshot, departmentRows, patientRows] = await Promise.all([
      loadMedicalDashboardSnapshot(force),
      loadDepartments(),
      loadPatients(),
    ]);
    metrics.value = snapshot.metrics;
    orders.value = snapshot.orders;
    doctors.value = snapshot.doctors;
    patients.value = snapshot.patients;
    departments.value = departmentRows;
    patientLookups.value = patientRows;
  }
  finally {
    loading.value = false;
  }
}

onMounted(() => loadDashboard());
</script>

<template>
  <div v-loading="loading" class="medical-page dashboard-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">医疗工作台</h1>
        <p class="medical-page__desc">聚合今日挂号、待办异常、号源利用率和知识库质量，帮助运营人员快速判断业务状态。</p>
      </div>
      <div class="medical-actions">
        <el-button @click="$router.push('/medical/order-trace')">订单追踪</el-button>
        <el-button type="primary" :loading="loading" @click="loadDashboard(true)">刷新数据</el-button>
      </div>
    </div>

    <div class="medical-grid metric-grid">
      <MetricCard v-for="item in metricCards" :key="item.title" v-bind="item" />
    </div>

    <div class="dashboard-layout">
      <section class="medical-section trend-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">今日订单趋势</h2>
          <span class="medical-muted">按最近订单状态汇总</span>
        </div>
        <div class="trend-bars">
          <div v-for="item in statusRows" :key="item.status" class="trend-row">
            <div class="trend-row__head">
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
            <el-progress :percentage="item.percent" :stroke-width="10" />
          </div>
        </div>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">待处理事项</h2>
          <el-button link type="primary" @click="$router.push('/medical/exception')">查看异常</el-button>
        </div>
        <div class="todo-list">
          <button v-for="item in todoRows" :key="item.title" type="button" class="todo-item" @click="$router.push(item.path)">
            <span class="todo-item__type">{{ item.type }}</span>
            <span class="todo-item__title">{{ item.title }}</span>
            <strong>{{ item.count }}</strong>
          </button>
          <el-empty v-if="!todoRows.length" description="暂无待处理事项" :image-size="72" />
        </div>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">科室挂号排行</h2>
        </div>
        <el-table :data="departmentRows" class="medical-table" height="286" table-layout="fixed" empty-text="暂无数据">
          <el-table-column prop="department" label="科室" min-width="120" show-overflow-tooltip />
          <el-table-column prop="count" label="订单" width="76" />
          <el-table-column prop="pending" label="待处理" width="86" />
          <el-table-column label="占比" min-width="120">
            <template #default="{ row }">
              <el-progress :percentage="toPercent(row.count, orders.length)" :stroke-width="8" />
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">医生接诊排行</h2>
          <el-button link type="primary" @click="$router.push('/medical/role-monitor')">医患监控</el-button>
        </div>
        <el-table :data="topDoctors" class="medical-table" height="286" table-layout="fixed" empty-text="暂无数据">
          <el-table-column prop="doctor_name" label="医生" min-width="110" show-overflow-tooltip />
          <el-table-column label="科室" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
          </el-table-column>
          <el-table-column prop="registration_count" label="挂号" width="76" />
          <el-table-column label="利用率" width="86">
            <template #default="{ row }">{{ row.utilization_rate || 0 }}%</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">最近订单</h2>
          <el-button link type="primary" @click="$router.push('/medical/registration')">进入订单</el-button>
        </div>
        <el-table :data="recentOrders" class="medical-table" height="320" table-layout="fixed" empty-text="暂无订单">
          <el-table-column label="患者" min-width="110">
            <template #default="{ row }">{{ patientName(row.patient_id, patientLookups) }}</template>
          </el-table-column>
          <el-table-column label="科室" min-width="110">
            <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="created_at" label="创建时间" min-width="160" show-overflow-tooltip />
        </el-table>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">患者风险关注</h2>
        </div>
        <el-table :data="riskPatients" class="medical-table" height="320" table-layout="fixed" empty-text="暂无风险数据">
          <el-table-column prop="patient_name" label="患者" min-width="110" show-overflow-tooltip />
          <el-table-column prop="registration_count" label="挂号" width="76" />
          <el-table-column prop="cancelled_count" label="取消" width="76" />
          <el-table-column prop="no_show_count" label="爽约" width="76" />
          <el-table-column prop="duplicate_risk_count" label="重复风险" width="96" />
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.metric-grid {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 12px;
}

.dashboard-layout {
  display: grid;
  grid-template-columns: 1.25fr 0.9fr;
  gap: 12px;
}

.trend-section {
  grid-row: span 2;
}

.trend-bars {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.trend-row__head,
.todo-item {
  display: flex;
  align-items: center;
}

.trend-row__head {
  justify-content: space-between;
  margin-bottom: 6px;
  color: var(--medical-text-secondary);
}

.todo-list {
  display: grid;
  gap: 8px;
  padding: 12px;
}

.todo-item {
  gap: 10px;
  min-height: 44px;
  padding: 0 12px;
  text-align: left;
  cursor: pointer;
  background: var(--medical-surface-soft);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.todo-item:hover {
  border-color: var(--medical-primary);
}

.todo-item__type {
  padding: 2px 6px;
  font-size: 12px;
  color: var(--medical-primary);
  background: var(--medical-primary-soft);
  border-radius: 999px;
}

.todo-item__title {
  flex: 1;
  min-width: 0;
  color: var(--medical-text-secondary);
}

.todo-item strong {
  color: var(--medical-danger);
}

@media (max-width: 1300px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
  }

  .trend-section {
    grid-row: auto;
  }
}
</style>
