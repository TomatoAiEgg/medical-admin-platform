<script setup lang="ts">
import type { Department, Doctor, PatientProfile, PlatformUser, RegistrationCreateOptions, RegistrationOrder } from '@/api/medical';
import {
  addRegistration,
  getRegistrationCreateOptions,
  getRegistrationDetail,
  getRegistrationTimeline,
  listRegistrations,
  rescheduleRegistration,
  transitionRegistration,
} from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import StatusTag from '../_components/StatusTag.vue';
import { boolText } from '../_utils/status';
import { displayValue, pickData, pickRows } from '../_utils/format';
import {
  departmentName,
  doctorName,
  loadDepartments,
  loadDoctors,
  loadPatients,
  loadPlatformUsers,
  patientName,
  patientOptionLabel,
  platformUserName,
  relationLabel,
  slotOptionLabel,
  sourceLabel,
  sourceOptions,
  userOptionLabel,
} from '../_utils/lookup';

const loading = ref(false);
const actionLoading = ref(false);
const createLoading = ref(false);
const optionsLoading = ref(false);
const detailLoading = ref(false);
const timelineLoading = ref(false);

const rows = ref<RegistrationOrder[]>([]);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const patients = ref<PatientProfile[]>([]);
const users = ref<PlatformUser[]>([]);
const detailVisible = ref(false);
const createVisible = ref(false);
const rescheduleVisible = ref(false);
const selectedOrder = ref<RegistrationOrder | null>(null);
const detailData = ref<Record<string, any>>({});
const timelineRows = ref<Record<string, unknown>[]>([]);

const filters = reactive({
  registrationId: '',
  patientId: '',
  doctorId: '',
  departmentCode: '',
  dateRange: [] as string[],
  status: '',
});

const createOptions = reactive<RegistrationCreateOptions>({
  slots: [],
  patients: [],
  users: [],
  bindings: [],
});

const createForm = reactive({
  userId: '',
  patientId: '',
  slotId: '',
  sourceChannel: 'MANUAL_ADMIN',
  externalRequestId: '',
});

const rescheduleForm = reactive({
  registrationId: '',
  slotId: '',
  reason: '',
});

const statusOptions = [
  { label: '待确认', value: 'PENDING_CONFIRM' },
  { label: '已预约', value: 'BOOKED' },
  { label: '已改约', value: 'RESCHEDULED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '爽约', value: 'NO_SHOW' },
];

const overviewCards = computed(() => [
  { title: '当前列表', value: rows.value.length, desc: '本次查询结果', tone: 'primary' as const, icon: 'Tickets' },
  { title: '待处理', value: countBy(['PENDING_CONFIRM']), desc: '待确认订单', tone: 'warning' as const, icon: 'Clock' },
  { title: '已预约', value: countBy(['BOOKED', 'RESCHEDULED']), desc: '有效预约中', tone: 'success' as const, icon: 'CircleCheckFilled' },
  { title: '异常结束', value: countBy(['EXPIRED', 'NO_SHOW']), desc: '过期或爽约', tone: 'danger' as const, icon: 'WarningFilled' },
]);

const availablePatients = computed(() => {
  if (!createForm.userId)
    return createOptions.patients;
  const patientIds = new Set((createOptions.bindings || [])
    .filter(item => item.user_id === createForm.userId && item.active)
    .map(item => item.patient_id));
  return createOptions.patients.filter(item => patientIds.has(item.patient_id));
});

const filteredDoctors = computed(() => {
  if (!filters.departmentCode)
    return doctors.value;
  return doctors.value.filter(item => item.department_code === filters.departmentCode);
});

function countBy(statuses: string[]) {
  return rows.value.filter(item => statuses.includes(item.status)).length;
}

function canConfirm(row: RegistrationOrder) {
  return row.status === 'PENDING_CONFIRM';
}

function canFinish(row: RegistrationOrder) {
  return ['BOOKED', 'RESCHEDULED'].includes(row.status);
}

function canReschedule(row: RegistrationOrder) {
  return ['PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED'].includes(row.status);
}

function canCancel(row: RegistrationOrder) {
  return ['PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED'].includes(row.status);
}

async function loadRows() {
  loading.value = true;
  try {
    rows.value = pickRows<RegistrationOrder>(await listRegistrations({
      pageNum: 1,
      pageSize: 80,
      status: filters.status,
      registrationId: filters.registrationId,
      patientId: filters.patientId,
      doctorId: filters.doctorId,
      departmentCode: filters.departmentCode,
      startDate: filters.dateRange?.[0],
      endDate: filters.dateRange?.[1],
    }));
  }
  finally {
    loading.value = false;
  }
}

async function loadLookups() {
  const [departmentRows, doctorRows, patientRows, userRows] = await Promise.all([
    loadDepartments(),
    loadDoctors(),
    loadPatients(),
    loadPlatformUsers(),
  ]);
  departments.value = departmentRows;
  doctors.value = doctorRows;
  patients.value = patientRows;
  users.value = userRows;
}

function resetFilters() {
  filters.registrationId = '';
  filters.patientId = '';
  filters.doctorId = '';
  filters.departmentCode = '';
  filters.dateRange = [];
  filters.status = '';
  loadRows();
}

async function loadCreateOptions() {
  optionsLoading.value = true;
  try {
    const data = pickData(await getRegistrationCreateOptions({ limit: 100 }), {
      slots: [],
      patients: [],
      users: [],
      bindings: [],
    } as RegistrationCreateOptions);
    createOptions.slots = data.slots || [];
    createOptions.patients = data.patients || [];
    createOptions.users = data.users || [];
    createOptions.bindings = data.bindings || [];
  }
  finally {
    optionsLoading.value = false;
  }
}

async function openCreate() {
  createForm.userId = '';
  createForm.patientId = '';
  createForm.slotId = '';
  createForm.sourceChannel = 'MANUAL_ADMIN';
  createForm.externalRequestId = '';
  createVisible.value = true;
  if (!createOptions.slots.length || !createOptions.patients.length || !createOptions.users.length)
    await loadCreateOptions();
}

async function submitCreate() {
  if (!createForm.userId || !createForm.patientId || !createForm.slotId) {
    ElMessage.error('用户、就诊人和号源不能为空');
    return;
  }
  createLoading.value = true;
  try {
    await addRegistration({
      userId: createForm.userId,
      patientId: createForm.patientId,
      slotId: createForm.slotId,
      sourceChannel: createForm.sourceChannel,
      externalRequestId: createForm.externalRequestId || undefined,
    });
    ElMessage.success('挂号订单已创建');
    createVisible.value = false;
    await Promise.all([loadRows(), loadCreateOptions()]);
  }
  finally {
    createLoading.value = false;
  }
}

async function openDetail(row: RegistrationOrder) {
  selectedOrder.value = row;
  detailVisible.value = true;
  detailLoading.value = true;
  timelineLoading.value = true;
  try {
    const [detailRes, timelineRes] = await Promise.all([
      getRegistrationDetail(row.registration_id),
      getRegistrationTimeline(row.registration_id),
    ]);
    detailData.value = pickData<Record<string, any>>(detailRes, {});
    timelineRows.value = pickRows<Record<string, unknown>>(timelineRes);
  }
  finally {
    detailLoading.value = false;
    timelineLoading.value = false;
  }
}

async function openReschedule(row: RegistrationOrder) {
  rescheduleForm.registrationId = row.registration_id;
  rescheduleForm.slotId = '';
  rescheduleForm.reason = '';
  rescheduleVisible.value = true;
  if (!createOptions.slots.length)
    await loadCreateOptions();
}

async function submitReschedule() {
  if (!rescheduleForm.registrationId || !rescheduleForm.slotId) {
    ElMessage.error('请选择新号源');
    return;
  }
  actionLoading.value = true;
  try {
    await rescheduleRegistration(rescheduleForm.registrationId, {
      slotId: rescheduleForm.slotId,
      reason: rescheduleForm.reason || undefined,
    });
    ElMessage.success('订单已改约');
    rescheduleVisible.value = false;
    await Promise.all([loadRows(), loadCreateOptions()]);
  }
  finally {
    actionLoading.value = false;
  }
}

async function runTransition(row: RegistrationOrder, action: 'CONFIRM' | 'CANCEL' | 'COMPLETE' | 'NO_SHOW') {
  const reasonRequired = action === 'CANCEL' || action === 'NO_SHOW';
  let reason = '';
  if (reasonRequired) {
    const result = await ElMessageBox.prompt('请输入操作原因', '订单状态变更', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '原因不能为空',
    });
    reason = (result as { value: string }).value;
  }
  else {
    await ElMessageBox.confirm('确认执行该订单状态操作？', '订单状态变更', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
  }

  actionLoading.value = true;
  try {
    await transitionRegistration(row.registration_id, { action, reason });
    ElMessage.success('订单状态已更新');
    await Promise.all([loadRows(), loadCreateOptions()]);
    if (detailVisible.value && selectedOrder.value?.registration_id === row.registration_id)
      await openDetail(row);
  }
  finally {
    actionLoading.value = false;
  }
}

watch(() => createForm.userId, () => {
  if (createForm.patientId && !availablePatients.value.some(item => item.patient_id === createForm.patientId))
    createForm.patientId = '';
});

watch(() => filters.departmentCode, () => {
  if (filters.doctorId && !filteredDoctors.value.some(item => item.doctor_id === filters.doctorId))
    filters.doctorId = '';
});

onMounted(async () => {
  await Promise.all([loadRows(), loadCreateOptions(), loadLookups()]);
});
</script>

<template>
  <div class="medical-page registration-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">挂号订单</h1>
        <p class="medical-page__desc">查询、确认、取消、完成和改约挂号订单，统一查看订单状态、患者、号源和审计链路。</p>
      </div>
      <div class="medical-actions">
        <el-button :loading="optionsLoading" @click="openCreate">新增挂号</el-button>
        <el-button type="primary" :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <div class="medical-grid medical-grid--4 overview-grid">
      <MetricCard v-for="item in overviewCards" :key="item.title" v-bind="item" />
    </div>

    <section class="medical-filter">
      <el-form :model="filters" inline>
        <el-form-item label="订单号">
          <el-input v-model="filters.registrationId" clearable placeholder="输入订单号" @keyup.enter="loadRows" />
        </el-form-item>
        <el-form-item label="患者">
          <el-select v-model="filters.patientId" clearable filterable placeholder="全部患者">
            <el-option v-for="item in patients" :key="item.patient_id" :label="patientOptionLabel(item)" :value="item.patient_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="filters.departmentCode" clearable filterable placeholder="全部科室">
            <el-option v-for="item in departments" :key="item.department_code" :label="item.department_name" :value="item.department_code" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="filters.doctorId" clearable filterable placeholder="全部医生">
            <el-option v-for="item in filteredDoctors" :key="item.doctor_id" :label="item.doctor_name" :value="item.doctor_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="filters.dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable filterable placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRows">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">订单列表</h2>
        <span class="medical-muted">共 {{ rows.length }} 条记录</span>
      </div>
      <el-table v-loading="loading" :data="rows" class="medical-table" height="calc(100vh - 390px)" table-layout="fixed" empty-text="暂无挂号订单">
        <el-table-column label="患者" width="116">
          <template #default="{ row }">{{ patientName(row.patient_id, patients) }}</template>
        </el-table-column>
        <el-table-column label="平台用户" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ platformUserName(row.user_id, users) }}</template>
        </el-table-column>
        <el-table-column label="科室" width="128">
          <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
        </el-table-column>
        <el-table-column label="医生" width="108">
          <template #default="{ row }">{{ doctorName(row.doctor_id, doctors) }}</template>
        </el-table-column>
        <el-table-column prop="start_time" label="时段" width="86" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="来源" width="104">
          <template #default="{ row }">{{ sourceLabel(row.source_channel) }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="176">
          <template #default="{ row }">
            <div class="medical-table-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" :loading="actionLoading" :disabled="!canReschedule(row)" @click="openReschedule(row)">改约</el-button>
              <el-dropdown trigger="click" :disabled="actionLoading">
                <el-button link type="primary">
                  处理<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :disabled="!canConfirm(row)" @click="runTransition(row, 'CONFIRM')">确认订单</el-dropdown-item>
                    <el-dropdown-item :disabled="!canFinish(row)" @click="runTransition(row, 'COMPLETE')">标记完成</el-dropdown-item>
                    <el-dropdown-item :disabled="!canFinish(row)" @click="runTransition(row, 'NO_SHOW')">标记爽约</el-dropdown-item>
                    <el-dropdown-item divided :disabled="!canCancel(row)" @click="runTransition(row, 'CANCEL')">取消订单</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="订单详情" size="72%">
      <div v-loading="detailLoading" class="medical-drawer-body">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">
            <span class="medical-mono">{{ displayValue(detailData.order?.registration_id || selectedOrder?.registration_id) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detailData.order?.status || selectedOrder?.status" />
          </el-descriptions-item>
          <el-descriptions-item label="患者">{{ displayValue(detailData.patient?.patient_name || patientName(selectedOrder?.patient_id, patients)) }}</el-descriptions-item>
          <el-descriptions-item label="平台用户">{{ displayValue(detailData.user?.display_name || detailData.user?.nickname || platformUserName(selectedOrder?.user_id, users)) }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ displayValue(detailData.patient?.phone_masked) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(detailData.order?.source_channel || (selectedOrder as any)?.source_channel) }}</el-descriptions-item>
          <el-descriptions-item label="科室">{{ displayValue(detailData.department?.department_name || departmentName(selectedOrder?.department_code, departments)) }}</el-descriptions-item>
          <el-descriptions-item label="医生">{{ displayValue(detailData.doctor?.doctor_name || doctorName(selectedOrder?.doctor_id, doctors)) }}</el-descriptions-item>
          <el-descriptions-item label="就诊日期">{{ displayValue(detailData.order?.clinic_date || selectedOrder?.clinic_date) }}</el-descriptions-item>
          <el-descriptions-item label="就诊时段">{{ displayValue(detailData.order?.start_time || selectedOrder?.start_time) }}</el-descriptions-item>
          <el-descriptions-item label="剩余号源">{{ displayValue(detailData.slot?.remaining_slots) }}</el-descriptions-item>
          <el-descriptions-item label="默认就诊人">{{ boolText(detailData.binding?.is_default) }}</el-descriptions-item>
          <el-descriptions-item label="关系">{{ relationLabel(detailData.binding?.relation_code) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ displayValue(detailData.order?.created_at || selectedOrder?.created_at) }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs>
          <el-tab-pane label="状态时间线">
            <div v-loading="timelineLoading">
              <el-timeline>
                <el-timeline-item
                  v-for="item in timelineRows"
                  :key="`${item.event_type}-${item.event_time}-${item.status}`"
                  :timestamp="String(item.event_time || '')"
                  placement="top"
                >
                  <div class="timeline-card">
                    <strong>{{ displayValue(item.title) }}</strong>
                    <div class="timeline-card__meta">
                      <el-tag size="small">{{ displayValue(item.event_type) }}</el-tag>
                      <StatusTag :value="String(item.status || '')" />
                    </div>
                  </div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-if="!timelineRows.length" description="暂无状态时间线" :image-size="80" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="挂号审计">
            <el-table :data="detailData.auditLogs || []" class="medical-table" empty-text="暂无挂号审计">
              <el-table-column prop="operation_type" label="操作" width="130" />
              <el-table-column prop="trace_id" label="traceId" min-width="220" show-overflow-tooltip />
              <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
              <el-table-column prop="created_at" label="时间" min-width="170" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="库存审计">
            <el-table :data="detailData.inventoryAuditLogs || []" class="medical-table" empty-text="暂无库存审计">
              <el-table-column prop="operation_type" label="操作" width="130" />
              <el-table-column prop="remaining_before" label="前" width="80" />
              <el-table-column prop="remaining_after" label="后" width="80" />
              <el-table-column prop="trace_id" label="traceId" min-width="220" show-overflow-tooltip />
              <el-table-column prop="created_at" label="时间" min-width="170" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新增挂号订单" width="640px">
      <el-form v-loading="optionsLoading" label-width="96px">
        <el-form-item label="平台用户">
          <el-select v-model="createForm.userId" filterable placeholder="选择前台登录用户">
            <el-option v-for="item in createOptions.users" :key="item.user_id" :label="userOptionLabel(item)" :value="item.user_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="就诊人">
          <el-select v-model="createForm.patientId" filterable placeholder="选择就诊人">
            <el-option v-for="item in availablePatients" :key="item.patient_id" :label="patientOptionLabel(item)" :value="item.patient_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="号源">
          <el-select v-model="createForm.slotId" filterable placeholder="选择可预约号源">
            <el-option v-for="item in createOptions.slots" :key="item.slot_id" :label="slotOptionLabel(item, departments, doctors)" :value="String(item.slot_id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="createForm.sourceChannel">
            <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="幂等键">
          <el-input v-model="createForm.externalRequestId" placeholder="不填则自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rescheduleVisible" title="订单改约" width="640px">
      <el-form v-loading="optionsLoading" label-width="96px">
        <el-form-item label="订单号">
          <el-input v-model="rescheduleForm.registrationId" disabled />
        </el-form-item>
        <el-form-item label="新号源">
          <el-select v-model="rescheduleForm.slotId" filterable placeholder="选择可预约号源">
            <el-option v-for="item in createOptions.slots" :key="item.slot_id" :label="slotOptionLabel(item, departments, doctors)" :value="String(item.slot_id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="rescheduleForm.reason" type="textarea" :rows="3" placeholder="请输入改约原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rescheduleVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitReschedule">确认改约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.overview-grid {
  margin-bottom: 12px;
}

.timeline-card {
  padding: 10px 12px;
  background: var(--medical-surface);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.timeline-card__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}
</style>
