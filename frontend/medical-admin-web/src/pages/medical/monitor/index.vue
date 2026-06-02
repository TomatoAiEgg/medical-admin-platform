<script setup lang="ts">
import type { Department, Doctor, DoctorMonitorSummary, PatientMonitorSummary, PatientProfile } from '@/api/medical';
import {
  expireOverdueRegistrations,
  getDoctorTrace,
  getMonitorDashboard,
  getPatientTrace,
  listDoctorMonitor,
  listPatientMonitor,
  scanExceptions,
} from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import StatusTag from '../_components/StatusTag.vue';
import { boolText } from '../_utils/status';
import { pickData, pickRows } from '../_utils/format';
import { departmentName, doctorOptionLabel, loadDepartments, loadDoctors, loadPatients, patientOptionLabel, relationLabel } from '../_utils/lookup';

type TraceRecord = Record<string, any>;
type DateRange = [string, string] | [];

const loading = ref(false);
const scanning = ref(false);
const expiring = ref(false);
const traceLoading = ref(false);
const traceVisible = ref(false);
const traceTitle = ref('');
const activeTab = ref('doctor');

const metrics = ref<Record<string, unknown>>({});
const scanResult = ref<Record<string, unknown>>({});
const doctorRows = ref<DoctorMonitorSummary[]>([]);
const patientRows = ref<PatientMonitorSummary[]>([]);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const patients = ref<PatientProfile[]>([]);
const traceData = ref<TraceRecord>({});

const filters = reactive({
  dateRange: [] as DateRange,
  departmentCode: '',
  doctorId: '',
  patientId: '',
});

const metricLabelMap: Record<string, string> = {
  todayRegistrationCount: '今日挂号',
  bookedCount: '已预约',
  rescheduledCount: '已改约',
  cancelledCount: '已取消',
  expiredCount: '已过期',
  overdueActiveCount: '超时未同步',
  todayOpenSlotCount: '今日开放号源',
  todayRemainingSlots: '今日剩余号源',
  unhandledExceptionCount: '未处理异常',
  knowledgeDocumentCount: '知识文档',
  enabledKnowledgeChunkCount: '可用知识片段',
  pendingConfirmCount: '待确认',
  activeRegistrationCount: '待就诊',
  completedRegistrationCount: '已完成',
  cancelledRegistrationCount: '已取消',
  expiredRegistrationCount: '已过期',
  noShowRegistrationCount: '爽约',
  openExceptionCount: '未处理异常',
  doctorCount: '医生数',
  patientCount: '患者数',
  availableSlotCount: '可用号源',
};

const dashboardCards = computed(() => [
  { title: '今日挂号', value: metricValue('todayRegistrationCount'), desc: '当天创建订单', tone: 'primary' as const, icon: 'Tickets' },
  { title: '待确认', value: metricValue('pendingConfirmCount'), desc: '需运营跟进', tone: 'warning' as const, icon: 'Clock' },
  { title: '待就诊', value: firstMetricValue(['activeRegistrationCount', 'bookedCount']), desc: '有效预约订单', tone: 'success' as const, icon: 'Calendar' },
  {
    title: '未处理异常',
    value: firstMetricValue(['openExceptionCount', 'unhandledExceptionCount']),
    desc: '规则命中待处理',
    tone: firstMetricValue(['openExceptionCount', 'unhandledExceptionCount']) > 0 ? 'danger' as const : 'info' as const,
    icon: 'WarningFilled',
  },
  { title: '可用号源', value: firstMetricValue(['availableSlotCount', 'todayRemainingSlots']), desc: '今日可预约容量', tone: 'info' as const, icon: 'Histogram' },
]);

const metricRows = computed(() => Object.entries(metrics.value).map(([key, value]) => ({
  key,
  label: metricLabel(key),
  value,
})));
const scanRows = computed(() => Object.entries(scanResult.value).map(([key, value]) => ({ key, value })));
const traceOrders = computed<TraceRecord[]>(() => traceData.value.orders || []);
const traceExceptions = computed<TraceRecord[]>(() => traceData.value.exceptions || []);
const traceAudits = computed<TraceRecord[]>(() => traceData.value.registrationAudits || []);
const traceInventoryAudits = computed<TraceRecord[]>(() => traceData.value.inventoryAudits || []);
const traceBindings = computed<TraceRecord[]>(() => traceData.value.bindings || []);
const traceSummaryRows = computed(() => Object.entries(traceData.value.summary || {}).map(([key, value]) => ({ key, value })));

const filteredDoctors = computed(() => {
  if (!filters.departmentCode)
    return doctors.value;
  return doctors.value.filter(item => item.department_code === filters.departmentCode);
});

function metricValue(key: string) {
  return Number(metrics.value[key] || 0);
}

function firstMetricValue(keys: string[]) {
  for (const key of keys) {
    const value = metrics.value[key];
    if (value !== undefined && value !== null)
      return Number(value || 0);
  }
  return 0;
}

function metricLabel(key: string) {
  return metricLabelMap[key] || key;
}

function percentValue(value?: number) {
  if (value === undefined || value === null)
    return '-';
  return `${value}%`;
}

function queryParams() {
  return {
    startDate: filters.dateRange[0] || '',
    endDate: filters.dateRange[1] || '',
    departmentCode: filters.departmentCode,
    doctorId: filters.doctorId,
    patientId: filters.patientId,
  };
}

async function loadDashboard() {
  metrics.value = pickData(await getMonitorDashboard(), {});
}

async function loadDoctorRows() {
  doctorRows.value = pickRows<DoctorMonitorSummary>(await listDoctorMonitor(queryParams()));
}

async function loadPatientRows() {
  patientRows.value = pickRows<PatientMonitorSummary>(await listPatientMonitor(queryParams()));
}

async function loadLookups() {
  const [departmentRows, doctorLookupRows, patientRows] = await Promise.all([loadDepartments(), loadDoctors(), loadPatients()]);
  departments.value = departmentRows;
  doctors.value = doctorLookupRows;
  patients.value = patientRows;
}

async function loadAll() {
  loading.value = true;
  try {
    await Promise.all([loadDashboard(), loadDoctorRows(), loadPatientRows(), loadLookups()]);
  }
  finally {
    loading.value = false;
  }
}

async function runScan() {
  scanning.value = true;
  try {
    scanResult.value = pickData(await scanExceptions(), {});
    ElMessage.success('异常扫描已完成');
    await loadAll();
  }
  finally {
    scanning.value = false;
  }
}

async function runExpireOverdue() {
  await ElMessageBox.confirm('确认同步已超过就诊时间的挂号订单为已过期？', '同步过期订单', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });

  expiring.value = true;
  try {
    const result = pickData(await expireOverdueRegistrations({ reason: '监控看板手动同步过期订单' }), { expiredCount: 0 });
    ElMessage.success(`已同步 ${result.expiredCount || 0} 条过期订单`);
    await loadAll();
  }
  finally {
    expiring.value = false;
  }
}

function resetFilters() {
  filters.dateRange = [];
  filters.departmentCode = '';
  filters.doctorId = '';
  filters.patientId = '';
  loadAll();
}

async function openDoctorTrace(row: DoctorMonitorSummary) {
  traceVisible.value = true;
  traceTitle.value = `医生链路 - ${row.doctor_name || '未命名医生'}`;
  traceLoading.value = true;
  try {
    traceData.value = pickData(await getDoctorTrace(row.doctor_id, queryParams()), {});
  }
  finally {
    traceLoading.value = false;
  }
}

async function openPatientTrace(row: PatientMonitorSummary) {
  traceVisible.value = true;
  traceTitle.value = `患者链路 - ${row.patient_name || '未命名患者'}`;
  traceLoading.value = true;
  try {
    traceData.value = pickData(await getPatientTrace(row.patient_id, queryParams()), {});
  }
  finally {
    traceLoading.value = false;
  }
}

watch(() => filters.departmentCode, () => {
  if (filters.doctorId && !filteredDoctors.value.some(item => item.doctor_id === filters.doctorId))
    filters.doctorId = '';
});

onMounted(loadAll);
</script>

<template>
  <div class="medical-page monitor-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">业务监控</h1>
        <p class="medical-page__desc">汇总挂号订单、医生接诊、患者风险、异常扫描和追踪链路。</p>
      </div>
      <div class="medical-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button :loading="expiring" @click="runExpireOverdue">同步过期订单</el-button>
        <el-button type="primary" :loading="scanning" @click="runScan">扫描异常</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="filters" inline>
      <el-form-item label="日期范围">
        <el-date-picker v-model="filters.dateRange" value-format="YYYY-MM-DD" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item label="科室">
        <el-select v-model="filters.departmentCode" clearable filterable placeholder="全部科室">
          <el-option v-for="item in departments" :key="item.department_code" :label="item.department_name" :value="item.department_code" />
        </el-select>
      </el-form-item>
      <el-form-item label="医生">
        <el-select v-model="filters.doctorId" clearable filterable placeholder="全部医生">
          <el-option v-for="item in filteredDoctors" :key="item.doctor_id" :label="doctorOptionLabel(item, departments)" :value="item.doctor_id" />
        </el-select>
      </el-form-item>
      <el-form-item label="患者">
        <el-select v-model="filters.patientId" clearable filterable placeholder="全部患者">
          <el-option v-for="item in patients" :key="item.patient_id" :label="patientOptionLabel(item)" :value="item.patient_id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="loadAll">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="monitor-metrics">
      <MetricCard v-for="item in dashboardCards" :key="item.title" v-bind="item" />
    </div>

    <div class="monitor-layout">
      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">监控指标</h2>
          <span class="medical-muted">共 {{ metricRows.length }} 项</span>
        </div>
        <div class="medical-section__body">
          <el-table v-loading="loading" :data="metricRows" class="medical-table" table-layout="fixed" empty-text="暂无监控指标">
            <el-table-column prop="label" label="业务名称" min-width="150" />
            <el-table-column prop="key" label="指标编码" min-width="220" show-overflow-tooltip>
              <template #default="{ row }"><span class="medical-mono">{{ row.key }}</span></template>
            </el-table-column>
            <el-table-column label="值" width="120">
              <template #default="{ row }"><strong>{{ row.value }}</strong></template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">异常扫描结果</h2>
          <span class="medical-muted">最近一次扫描</span>
        </div>
        <div class="medical-section__body">
          <el-empty v-if="!scanRows.length" description="暂无扫描结果" />
          <el-table v-else :data="scanRows" class="medical-table" table-layout="fixed">
            <el-table-column prop="key" label="规则/结果" min-width="220" show-overflow-tooltip />
            <el-table-column label="新增异常数" width="130">
              <template #default="{ row }">
                <el-tag :type="Number(row.value) > 0 ? 'warning' : 'success'" effect="light">{{ row.value }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">医患监控明细</h2>
      </div>
      <el-tabs v-model="activeTab" class="monitor-tabs">
        <el-tab-pane label="医生监控" name="doctor">
          <el-table v-loading="loading" :data="doctorRows" class="medical-table" table-layout="fixed" empty-text="暂无医生监控数据">
            <el-table-column prop="doctor_name" label="医生" min-width="120" show-overflow-tooltip />
            <el-table-column label="科室" min-width="120">
              <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
            </el-table-column>
            <el-table-column prop="registration_count" label="挂号量" width="90" />
            <el-table-column prop="booked_count" label="已预约" width="90" />
            <el-table-column prop="rescheduled_count" label="改约" width="80" />
            <el-table-column prop="completed_count" label="完成" width="80" />
            <el-table-column prop="cancelled_count" label="取消" width="80" />
            <el-table-column prop="expired_count" label="过期" width="80" />
            <el-table-column prop="no_show_count" label="爽约" width="80" />
            <el-table-column prop="remaining_slots" label="剩余" width="80" />
            <el-table-column label="利用率" width="100">
              <template #default="{ row }">{{ percentValue(row.utilization_rate) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDoctorTrace(row)">查看链路</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="患者监控" name="patient">
          <el-table v-loading="loading" :data="patientRows" class="medical-table" table-layout="fixed" empty-text="暂无患者监控数据">
            <el-table-column prop="patient_name" label="患者" min-width="120" show-overflow-tooltip />
            <el-table-column prop="phone_masked" label="手机号" min-width="120" />
            <el-table-column prop="verified_status" label="认证" width="110" />
            <el-table-column prop="registration_count" label="挂号量" width="90" />
            <el-table-column prop="booked_count" label="待就诊" width="90" />
            <el-table-column prop="rescheduled_count" label="改约" width="80" />
            <el-table-column prop="completed_count" label="完成" width="80" />
            <el-table-column prop="cancelled_count" label="取消" width="80" />
            <el-table-column prop="expired_count" label="过期" width="80" />
            <el-table-column prop="no_show_count" label="爽约" width="80" />
            <el-table-column prop="next_clinic_date" label="最近就诊日" min-width="120" />
            <el-table-column label="重复风险" width="100">
              <template #default="{ row }">
                <el-tag :type="row.duplicate_risk_count > 0 ? 'warning' : 'success'" effect="light">{{ row.duplicate_risk_count }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPatientTrace(row)">查看链路</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="traceVisible" :title="traceTitle" size="76%">
      <div v-loading="traceLoading" class="medical-drawer-body">
        <section class="medical-section">
          <div class="medical-section__header">
            <h2 class="medical-section__title">链路汇总</h2>
          </div>
          <div class="medical-section__body">
            <el-empty v-if="!traceSummaryRows.length" description="暂无链路汇总" />
            <el-table v-else :data="traceSummaryRows" class="medical-table" table-layout="fixed">
              <el-table-column prop="key" label="指标" min-width="180" />
              <el-table-column label="值" min-width="120">
                <template #default="{ row }">{{ row.value }}</template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="medical-section">
          <div class="medical-section__header">
            <h2 class="medical-section__title">关联明细</h2>
          </div>
          <el-tabs class="trace-tabs">
            <el-tab-pane label="订单">
              <el-table :data="traceOrders" class="medical-table" table-layout="fixed" empty-text="暂无关联订单">
                <el-table-column prop="display_name" label="用户" min-width="120" show-overflow-tooltip />
                <el-table-column prop="patient_name" label="患者" min-width="120" show-overflow-tooltip />
                <el-table-column prop="doctor_name" label="医生" min-width="120" show-overflow-tooltip />
                <el-table-column prop="department_name" label="科室" min-width="120" show-overflow-tooltip />
                <el-table-column prop="clinic_date" label="就诊日期" min-width="120" />
                <el-table-column prop="start_time" label="时间" width="100" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <StatusTag :value="row.status" />
                  </template>
                </el-table-column>
                <el-table-column prop="created_at" label="创建时间" min-width="160" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="异常">
              <el-table :data="traceExceptions" class="medical-table" table-layout="fixed" empty-text="暂无异常记录">
                <el-table-column prop="rule_code" label="规则" min-width="180" show-overflow-tooltip />
                <el-table-column label="级别" width="120">
                  <template #default="{ row }">
                    <StatusTag :value="row.severity" kind="severity" />
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <StatusTag :value="row.status" kind="exception" />
                  </template>
                </el-table-column>
                <el-table-column prop="detected_at" label="发现时间" min-width="160" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="订单审计">
              <el-table :data="traceAudits" class="medical-table" table-layout="fixed" empty-text="暂无订单审计">
                <el-table-column prop="operation_type" label="操作" width="120" />
                <el-table-column label="成功" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.success ? 'success' : 'danger'" effect="light">{{ boolText(row.success) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="trace_id" label="traceId" min-width="220" show-overflow-tooltip />
                <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
                <el-table-column prop="created_at" label="时间" min-width="160" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="库存审计">
              <el-table :data="traceInventoryAudits" class="medical-table" table-layout="fixed" empty-text="暂无库存审计">
                <el-table-column prop="operation_type" label="操作" width="120" />
                <el-table-column prop="remaining_before" label="前" width="80" />
                <el-table-column prop="remaining_after" label="后" width="80" />
                <el-table-column prop="created_at" label="时间" min-width="160" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="就诊人绑定">
              <el-table :data="traceBindings" class="medical-table" table-layout="fixed" empty-text="暂无绑定记录">
                <el-table-column prop="display_name" label="用户" min-width="120" show-overflow-tooltip />
                <el-table-column label="关系" width="100">
                  <template #default="{ row }">{{ relationLabel(row.relation_code) }}</template>
                </el-table-column>
                <el-table-column label="默认" width="90">
                  <template #default="{ row }">{{ boolText(row.is_default) }}</template>
                </el-table-column>
                <el-table-column label="有效" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.active ? 'success' : 'info'" effect="light">{{ boolText(row.active) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="bound_at" label="绑定时间" min-width="160" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.monitor-page {
  display: grid;
  gap: 12px;
}

.monitor-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.monitor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 12px;
}

.monitor-tabs,
.trace-tabs {
  padding: 0 14px 14px;
}

@media (max-width: 1400px) {
  .monitor-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .monitor-layout {
    grid-template-columns: 1fr;
  }

  .monitor-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .monitor-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
