<script setup lang="ts">
import type { Department, Doctor, MedicalExceptionHandleLog, MedicalExceptionRecord, MedicalExceptionRule, PatientProfile } from '@/api/medical';
import { handleException, listExceptionHandleLogs, listExceptionRules, listExceptions, scanExceptions } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import StatusTag from '../_components/StatusTag.vue';
import { getExceptionStatusMeta, getSeverityMeta } from '../_utils/status';
import { pickData, pickRows } from '../_utils/format';
import { departmentName, doctorName, doctorOptionLabel, loadDepartments, loadDoctors, loadPatients, patientName, patientOptionLabel } from '../_utils/lookup';

const loading = ref(false);
const scanning = ref(false);
const handling = ref(false);
const logDrawer = ref(false);
const detailDrawer = ref(false);
const rules = ref<MedicalExceptionRule[]>([]);
const records = ref<MedicalExceptionRecord[]>([]);
const handleLogs = ref<MedicalExceptionHandleLog[]>([]);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const patients = ref<PatientProfile[]>([]);
const scanResult = ref<Record<string, unknown>>({});
const selectedException = ref<MedicalExceptionRecord | null>(null);
const detailException = ref<MedicalExceptionRecord | null>(null);

const handleDialog = computed({
  get: () => !!selectedException.value,
  set: (value: boolean) => {
    if (!value)
      selectedException.value = null;
  },
});

const filters = reactive({
  ruleCode: '',
  exceptionType: '',
  severity: '',
  status: '',
  registrationId: '',
  patientId: '',
  doctorId: '',
  departmentCode: '',
  dateRange: [] as string[],
});

const handleForm = reactive({
  exceptionId: 0,
  status: 'PROCESSING',
  remark: '',
});

const statusOptions = [
  { label: '未处理', value: 'UNHANDLED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已忽略', value: 'IGNORED' },
  { label: '已解决', value: 'RESOLVED' },
];

const severityOptions = [
  { label: '提示', value: 'INFO' },
  { label: '警告', value: 'WARN' },
  { label: '错误', value: 'ERROR' },
  { label: '严重', value: 'CRITICAL' },
];

const filteredDoctors = computed(() => {
  if (!filters.departmentCode)
    return doctors.value;
  return doctors.value.filter(item => item.department_code === filters.departmentCode);
});

const overview = computed(() => [
  { title: '未处理', value: countStatus('UNHANDLED'), desc: '需要优先处理', tone: 'danger' as const, icon: 'WarningFilled' },
  { title: '处理中', value: countStatus('PROCESSING'), desc: '已进入处理流程', tone: 'warning' as const, icon: 'Clock' },
  { title: '已确认', value: countStatus('CONFIRMED'), desc: '异常属实待闭环', tone: 'primary' as const, icon: 'InfoFilled' },
  { title: '已忽略', value: countStatus('IGNORED'), desc: '规则命中但无需处理', tone: 'info' as const, icon: 'CircleCloseFilled' },
  { title: '已解决', value: countStatus('RESOLVED'), desc: '处理闭环完成', tone: 'success' as const, icon: 'CircleCheckFilled' },
]);

const scanRows = computed(() => Object.entries(scanResult.value).map(([name, value]) => ({ name, value })));

function countStatus(status: string) {
  return records.value.filter(item => item.status === status).length;
}

function exceptionRuleName(row?: MedicalExceptionRecord | null) {
  if (!row)
    return '-';
  return rules.value.find(item => item.rule_code === row.rule_code)?.rule_name || row.rule_code;
}

function formatEvidence(value?: string | Record<string, unknown>) {
  if (!value)
    return '-';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    }
    catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function queryParams() {
  return {
    pageNum: 1,
    pageSize: 80,
    ...filters,
    startDate: filters.dateRange?.[0],
    endDate: filters.dateRange?.[1],
  };
}

async function loadRules() {
  rules.value = pickRows<MedicalExceptionRule>(await listExceptionRules({ pageNum: 1, pageSize: 100 }));
}

async function loadRecords() {
  records.value = pickRows<MedicalExceptionRecord>(await listExceptions(queryParams()));
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
    await Promise.all([loadRules(), loadRecords(), loadLookups()]);
  }
  finally {
    loading.value = false;
  }
}

async function runScan() {
  scanning.value = true;
  try {
    scanResult.value = pickData(await scanExceptions(), {});
    await loadRecords();
  }
  finally {
    scanning.value = false;
  }
}

function resetFilters() {
  Object.assign(filters, {
    ruleCode: '',
    exceptionType: '',
    severity: '',
    status: '',
    registrationId: '',
    patientId: '',
    doctorId: '',
    departmentCode: '',
    dateRange: [],
  });
  loadRecords();
}

function openHandle(row: MedicalExceptionRecord, status = 'PROCESSING') {
  selectedException.value = row;
  handleForm.exceptionId = row.exception_id;
  handleForm.status = status;
  handleForm.remark = '';
}

function openDetail(row: MedicalExceptionRecord) {
  detailException.value = row;
  detailDrawer.value = true;
}

async function submitHandle() {
  if (!handleForm.exceptionId)
    return;
  handling.value = true;
  try {
    await handleException({
      exceptionId: handleForm.exceptionId,
      status: handleForm.status,
      remark: handleForm.remark,
    });
    ElMessage.success('异常处理状态已更新');
    selectedException.value = null;
    await loadRecords();
  }
  finally {
    handling.value = false;
  }
}

async function openLogs(row: MedicalExceptionRecord) {
  handleLogs.value = pickRows<MedicalExceptionHandleLog>(await listExceptionHandleLogs({
    pageNum: 1,
    pageSize: 50,
    exceptionId: row.exception_id,
  }));
  logDrawer.value = true;
}

watch(() => filters.departmentCode, () => {
  if (filters.doctorId && !filteredDoctors.value.some(item => item.doctor_id === filters.doctorId))
    filters.doctorId = '';
});

onMounted(loadAll);
</script>

<template>
  <div class="medical-page exception-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">异常处理</h1>
        <p class="medical-page__desc">扫描挂号、号源、医生、患者和审计异常，集中处理规则命中记录并形成闭环。</p>
      </div>
      <div class="medical-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="primary" :loading="scanning" @click="runScan">扫描异常</el-button>
      </div>
    </div>

    <div class="medical-grid exception-metrics">
      <MetricCard v-for="item in overview" :key="item.title" v-bind="item" />
    </div>

    <section class="medical-filter">
      <el-form :model="filters" inline>
        <el-form-item label="规则">
          <el-select v-model="filters.ruleCode" clearable filterable placeholder="全部规则" style="width: 210px">
            <el-option v-for="item in rules" :key="item.rule_code" :label="item.rule_name" :value="item.rule_code" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="filters.exceptionType" class="exception-filter__type" clearable placeholder="异常类型" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.severity" class="exception-filter__level" clearable placeholder="全部级别">
            <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" class="exception-filter__status" clearable placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单">
          <el-input v-model="filters.registrationId" clearable placeholder="输入订单号" />
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
        <el-form-item label="日期">
          <el-date-picker v-model="filters.dateRange" value-format="YYYY-MM-DD" type="daterange" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
        <el-form-item class="exception-filter__actions">
          <el-button type="primary" :loading="loading" @click="loadRecords">查询</el-button>
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
        <h2 class="medical-section__title">异常记录</h2>
        <span class="medical-muted">共 {{ records.length }} 条记录</span>
      </div>
      <el-table v-loading="loading" :data="records" class="medical-table" height="calc(100vh - 430px)" table-layout="fixed" empty-text="暂无异常记录">
        <el-table-column label="规则" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ exceptionRuleName(row) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getSeverityMeta(row.severity).type" effect="light" round>
              {{ getSeverityMeta(row.severity).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" kind="exception" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="210" show-overflow-tooltip />
        <el-table-column label="患者" width="116">
          <template #default="{ row }">{{ patientName(row.patient_id, patients) }}</template>
        </el-table-column>
        <el-table-column label="医生" width="110">
          <template #default="{ row }">{{ doctorName(row.doctor_id, doctors) }}</template>
        </el-table-column>
        <el-table-column label="科室" width="120">
          <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
        </el-table-column>
        <el-table-column prop="detected_at" label="检测时间" min-width="150" show-overflow-tooltip />
        <el-table-column prop="handled_by" label="处理人" width="100" />
        <el-table-column label="操作" width="184">
          <template #default="{ row }">
            <div class="medical-table-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openLogs(row)">日志</el-button>
              <el-dropdown trigger="click">
                <el-button link type="primary">
                  处理<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openHandle(row, 'PROCESSING')">标记处理中</el-dropdown-item>
                    <el-dropdown-item @click="openHandle(row, 'CONFIRMED')">确认异常</el-dropdown-item>
                    <el-dropdown-item @click="openHandle(row, 'RESOLVED')">标记解决</el-dropdown-item>
                    <el-dropdown-item @click="openHandle(row, 'IGNORED')">忽略异常</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailDrawer" title="异常详情" size="680px">
      <template v-if="detailException">
        <el-descriptions class="exception-detail" :column="2" border>
          <el-descriptions-item label="规则">{{ exceptionRuleName(detailException) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailException.exception_type || '-' }}</el-descriptions-item>
          <el-descriptions-item label="级别">{{ getSeverityMeta(detailException.severity).label }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ getExceptionStatusMeta(detailException.status).label }}</el-descriptions-item>
          <el-descriptions-item label="订单">{{ detailException.registration_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="号源">{{ detailException.slot_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="患者">{{ patientName(detailException.patient_id, patients) }}</el-descriptions-item>
          <el-descriptions-item label="医生">{{ doctorName(detailException.doctor_id, doctors) }}</el-descriptions-item>
          <el-descriptions-item label="科室">{{ departmentName(detailException.department_code, departments) }}</el-descriptions-item>
          <el-descriptions-item label="业务日期">{{ detailException.biz_date || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检测时间">{{ detailException.detected_at || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{ detailException.handled_at || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detailException.handled_by || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理备注">{{ detailException.handle_remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="exception-detail__block">
          <h3>异常说明</h3>
          <p>{{ detailException.content || detailException.title || '-' }}</p>
        </div>
        <div class="exception-detail__block">
          <h3>证据数据</h3>
          <pre>{{ formatEvidence(detailException.evidence_json) }}</pre>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="handleDialog" title="处理异常" width="560px">
      <el-alert
        v-if="selectedException"
        class="handle-alert"
        :title="selectedException.title"
        :description="`规则：${exceptionRuleName(selectedException)}，当前状态：${getExceptionStatusMeta(selectedException.status).label}`"
        type="warning"
        show-icon
        :closable="false"
      />
      <el-form label-width="90px">
        <el-form-item label="目标状态">
          <el-select v-model="handleForm.status">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.remark" type="textarea" :rows="4" placeholder="请输入处理说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="selectedException = null">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logDrawer" title="处理日志" size="620px">
      <el-table :data="handleLogs" class="medical-table" table-layout="fixed" empty-text="暂无处理日志">
        <el-table-column prop="old_status" label="原状态" width="120" />
        <el-table-column prop="new_status" label="新状态" width="120" />
        <el-table-column prop="handle_user_name" label="处理人" width="120" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="created_at" label="时间" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.exception-metrics {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-bottom: 12px;
}

.scan-section {
  margin-bottom: 12px;
}

.handle-alert {
  margin-bottom: 14px;
}

.exception-detail {
  margin-bottom: 16px;
}

.exception-detail__block {
  margin-top: 16px;

  h3 {
    margin: 0 0 8px;
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 700;
  }

  p {
    margin: 0;
    color: var(--el-text-color-regular);
    line-height: 1.7;
  }

  pre {
    max-height: 260px;
    margin: 0;
    padding: 12px;
    overflow: auto;
    color: var(--el-text-color-regular);
    white-space: pre-wrap;
    word-break: break-word;
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
  }
}

.exception-filter__type {
  width: 226px;
  flex: 0 0 226px;
}

.exception-filter__level,
.exception-filter__status {
  width: 132px;
  flex: 0 0 132px;
}

.exception-filter__actions {
  min-width: 166px;
  flex: 0 0 166px;
}

@media (max-width: 1300px) {
  .exception-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .exception-filter__type,
  .exception-filter__level,
  .exception-filter__status,
  .exception-filter__actions {
    width: 100%;
    flex: 1 1 100%;
    min-width: 0;
  }
}
</style>
