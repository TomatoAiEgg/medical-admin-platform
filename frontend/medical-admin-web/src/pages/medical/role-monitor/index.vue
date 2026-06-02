<script setup lang="ts">
import type { Department, Doctor, DoctorMonitorSummary, PatientMonitorSummary, PatientProfile } from '@/api/medical';
import { expireOverdueRegistrations, getDoctorTrace, getMonitorDashboard, getPatientTrace, listDoctorMonitor, listPatientMonitor, scanExceptions } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import StatusTag from '../_components/StatusTag.vue';
import { boolText } from '../_utils/status';
import { pickData, toPercent } from '../_utils/format';
import { departmentName, doctorOptionLabel, loadDepartments, loadDoctors, loadPatients, patientOptionLabel, relationLabel } from '../_utils/lookup';

const loading = ref(false);
const scanning = ref(false);
const expiring = ref(false);
const traceLoading = ref(false);
const traceVisible = ref(false);
const activeTab = ref('doctor');
const metrics = ref<Record<string, unknown>>({});
const scanResult = ref<Record<string, unknown>>({});
const doctorRows = ref<DoctorMonitorSummary[]>([]);
const patientRows = ref<PatientMonitorSummary[]>([]);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const patients = ref<PatientProfile[]>([]);
const traceTitle = ref('');
const traceData = ref<Record<string, any>>({});

const filters = reactive({
  dateRange: [] as string[],
  departmentCode: '',
  doctorId: '',
  patientId: '',
});

const overview = computed(() => [
  { title: '今日挂号', value: metricValue(metrics.value.todayRegistrationCount, 0), desc: '今日创建订单', tone: 'primary' as const, icon: 'Tickets' },
  { title: '待确认', value: metricValue(metrics.value.pendingConfirmCount, 0), desc: '待确认订单', tone: 'warning' as const, icon: 'Clock' },
  { title: '待就诊', value: metricValue(firstExistingMetric(['activeRegistrationCount', 'bookedCount']), 0), desc: '当前有效预约', tone: 'success' as const, icon: 'Calendar' },
  { title: '未处理异常', value: metricValue(firstExistingMetric(['openExceptionCount', 'unhandledExceptionCount']), 0), desc: '需人工处理', tone: 'danger' as const, icon: 'WarningFilled' },
]);

const filteredDoctors = computed(() => {
  if (!filters.departmentCode)
    return doctors.value;
  return doctors.value.filter(item => item.department_code === filters.departmentCode);
});

const doctorSummary = computed(() => {
  const capacity = doctorRows.value.reduce((sum, item) => sum + Number(item.slot_capacity || 0), 0);
  const remaining = doctorRows.value.reduce((sum, item) => sum + Number(item.remaining_slots || 0), 0);
  return { total: doctorRows.value.length, utilization: toPercent(capacity - remaining, capacity) };
});

const patientSummary = computed(() => ({
  total: patientRows.value.length,
  risk: patientRows.value.reduce((sum, item) => sum + Number(item.duplicate_risk_count || 0), 0),
}));

const traceOrders = computed(() => traceData.value.orders || []);
const traceExceptions = computed(() => traceData.value.exceptions || []);
const traceAudits = computed(() => traceData.value.registrationAudits || []);
const traceInventoryAudits = computed(() => traceData.value.inventoryAudits || []);
const traceBindings = computed(() => traceData.value.bindings || []);
const traceSummaryRows = computed(() => Object.entries(traceData.value.summary || {}).map(([name, value]) => ({ name, value })));
const scanRows = computed(() => Object.entries(scanResult.value).map(([name, value]) => ({ name, value })));

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

function queryParams() {
  return {
    startDate: filters.dateRange?.[0] || '',
    endDate: filters.dateRange?.[1] || '',
    departmentCode: filters.departmentCode,
    doctorId: filters.doctorId,
    patientId: filters.patientId,
  };
}

async function loadDashboard() {
  metrics.value = pickData(await getMonitorDashboard(), {});
}

async function loadDoctorRows() {
  doctorRows.value = pickData(await listDoctorMonitor(queryParams()), []);
}

async function loadPatientRows() {
  patientRows.value = pickData(await listPatientMonitor(queryParams()), []);
}

async function loadLookups() {
  const [departmentRows, doctorRows, patientRows] = await Promise.all([loadDepartments(), loadDoctors(), loadPatients()]);
  departments.value = departmentRows;
  doctors.value = doctorRows;
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
    await loadAll();
  }
  finally {
    scanning.value = false;
  }
}

async function runExpireOverdue() {
  await ElMessageBox.confirm('确认同步已过就诊时间的挂号订单为已过期？', '同步过期订单', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  expiring.value = true;
  try {
    const result = pickData(await expireOverdueRegistrations({ reason: '医患监控手动同步过期订单' }), { expiredCount: 0 });
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
  <div class="medical-page role-monitor-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">医患监控</h1>
        <p class="medical-page__desc">从医生和患者两个维度监控挂号行为、异常风险、号源利用率和订单链路。</p>
      </div>
      <div class="medical-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button :loading="expiring" @click="runExpireOverdue">同步过期订单</el-button>
        <el-button type="primary" :loading="scanning" @click="runScan">扫描异常</el-button>
      </div>
    </div>

    <div class="medical-grid medical-grid--4 overview-grid">
      <MetricCard v-for="item in overview" :key="item.title" v-bind="item" />
    </div>

    <section class="medical-filter">
      <el-form :model="filters" inline>
        <el-form-item label="日期">
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
    </section>

    <section v-if="scanRows.length" class="medical-section scan-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">最近扫描结果</h2>
      </div>
      <el-table :data="scanRows" class="medical-table" table-layout="fixed">
        <el-table-column prop="name" label="扫描项" min-width="260" />
        <el-table-column prop="value" label="新增异常数" width="140" />
      </el-table>
    </section>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">监控列表</h2>
        <div class="monitor-summary">
          <span v-if="activeTab === 'doctor'">医生 {{ doctorSummary.total }} 人，利用率 {{ doctorSummary.utilization }}%</span>
          <span v-else>患者 {{ patientSummary.total }} 人，风险 {{ patientSummary.risk }} 次</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="monitor-tabs">
        <el-tab-pane label="医生监控" name="doctor">
          <el-table v-loading="loading" :data="doctorRows" class="medical-table" height="calc(100vh - 460px)" table-layout="fixed" empty-text="暂无医生监控数据">
            <el-table-column prop="doctor_name" label="医生" min-width="120" show-overflow-tooltip />
            <el-table-column label="科室" min-width="120">
              <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
            </el-table-column>
            <el-table-column prop="slot_count" label="今日号源" width="90" />
            <el-table-column prop="registration_count" label="挂号" width="80" />
            <el-table-column prop="booked_count" label="已预约" width="90" />
            <el-table-column prop="cancelled_count" label="取消" width="80" />
            <el-table-column prop="completed_count" label="完成" width="80" />
            <el-table-column prop="no_show_count" label="爽约" width="80" />
            <el-table-column prop="remaining_slots" label="剩余" width="80" />
            <el-table-column label="利用率" width="130">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.utilization_rate || 0)" :stroke-width="8" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDoctorTrace(row)">链路下钻</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="患者监控" name="patient">
          <el-table v-loading="loading" :data="patientRows" class="medical-table" height="calc(100vh - 460px)" table-layout="fixed" empty-text="暂无患者监控数据">
            <el-table-column prop="patient_name" label="患者" min-width="120" show-overflow-tooltip />
            <el-table-column prop="phone_masked" label="手机号" min-width="120" />
            <el-table-column prop="verified_status" label="实名状态" width="100" />
            <el-table-column prop="registration_count" label="历史挂号" width="100" />
            <el-table-column prop="booked_count" label="待就诊" width="90" />
            <el-table-column prop="cancelled_count" label="取消" width="80" />
            <el-table-column prop="no_show_count" label="爽约" width="80" />
            <el-table-column prop="next_clinic_date" label="最近就诊日" min-width="120" />
            <el-table-column label="重复风险" width="100">
              <template #default="{ row }">
                <el-tag :type="row.duplicate_risk_count > 0 ? 'warning' : 'success'" effect="light" round>
                  {{ row.duplicate_risk_count }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPatientTrace(row)">链路下钻</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="traceVisible" :title="traceTitle" size="76%">
      <div v-loading="traceLoading" class="medical-drawer-body">
        <el-table :data="traceSummaryRows" class="medical-table" table-layout="fixed" empty-text="暂无链路汇总">
          <el-table-column prop="name" label="指标" min-width="180" />
          <el-table-column prop="value" label="值" min-width="120" />
        </el-table>

        <el-tabs>
          <el-tab-pane label="关联订单">
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

          <el-tab-pane label="异常记录">
            <el-table :data="traceExceptions" class="medical-table" table-layout="fixed" empty-text="暂无异常记录">
              <el-table-column prop="rule_code" label="规则" min-width="180" show-overflow-tooltip />
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
                  <el-tag :type="row.success ? 'success' : 'danger'">{{ boolText(row.success) }}</el-tag>
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
              <el-table-column prop="bound_at" label="绑定时间" min-width="160" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.overview-grid,
.scan-section {
  margin-bottom: 12px;
}

.monitor-summary {
  font-size: 13px;
  color: var(--medical-text-muted);
}

.monitor-tabs {
  padding: 0 12px 12px;
}
</style>
