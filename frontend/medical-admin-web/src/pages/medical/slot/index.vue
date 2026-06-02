<script setup lang="ts">
import type { ClinicSlot, Department, Doctor } from '@/api/medical';
import { adjustSlotInventory, batchGenerateSlots, changeSlotOperationalStatus, listSlots } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { pickRows, toPercent } from '../_utils/format';
import { departmentName, doctorName, doctorOptionLabel, loadDepartments, loadDoctors } from '../_utils/lookup';

const loading = ref(false);
const actionLoading = ref(false);
const batchLoading = ref(false);
const rows = ref<ClinicSlot[]>([]);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const inventoryVisible = ref(false);
const batchVisible = ref(false);
const auditVisible = ref(false);
const currentSlot = ref<ClinicSlot>();

const filters = reactive({
  departmentCode: '',
  doctorId: '',
  clinicDate: '',
  status: '',
  onlyAvailable: false,
});

const inventoryForm = reactive({
  capacity: 0,
  remainingSlots: 0,
  reason: '',
});

const batchForm = reactive({
  departmentCode: '',
  doctorId: '',
  dateRange: [] as string[],
  weekdays: [1, 2, 3, 4, 5] as number[],
  startTime: '09:00',
  endTime: '12:00',
  intervalMinutes: 30,
  capacity: 10,
  registrationFee: 0,
  roomNo: '',
  remarks: '',
});

const weekdayOptions = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
];

const statusOptions = [
  { label: '开放', value: 'OPEN' },
  { label: '停诊', value: 'SUSPENDED' },
  { label: '关闭', value: 'CLOSED' },
  { label: '取消', value: 'CANCELLED' },
];

const filteredRows = computed(() => {
  if (!filters.onlyAvailable)
    return rows.value;
  return rows.value.filter(item => Number(item.remaining_slots || 0) > 0 && item.status === 'OPEN');
});

const filteredDoctors = computed(() => {
  if (!filters.departmentCode)
    return doctors.value;
  return doctors.value.filter(item => item.department_code === filters.departmentCode);
});

const batchDoctors = computed(() => {
  if (!batchForm.departmentCode)
    return doctors.value;
  return doctors.value.filter(item => item.department_code === batchForm.departmentCode);
});

const overview = computed(() => {
  const total = filteredRows.value.length;
  const capacity = filteredRows.value.reduce((sum, item) => sum + Number(item.capacity || 0), 0);
  const remaining = filteredRows.value.reduce((sum, item) => sum + Number(item.remaining_slots || 0), 0);
  const open = filteredRows.value.filter(item => item.status === 'OPEN').length;
  const suspended = filteredRows.value.filter(item => item.status === 'SUSPENDED').length;
  return [
    { title: '号源总数', value: total, desc: '当前查询时段', tone: 'primary' as const, icon: 'Calendar' },
    { title: '开放号源', value: open, desc: '可预约状态', tone: 'success' as const, icon: 'CircleCheckFilled' },
    { title: '停诊号源', value: suspended, desc: '需要运营关注', tone: 'warning' as const, icon: 'WarningFilled' },
    { title: '剩余率', value: `${toPercent(remaining, capacity)}%`, desc: `剩余 ${remaining} / 容量 ${capacity}`, tone: 'info' as const, icon: 'DataAnalysis' },
  ];
});

function slotStatusLabel(status?: string) {
  return statusOptions.find(item => item.value === status)?.label || status || '-';
}

function slotStatusType(status?: string) {
  if (status === 'OPEN')
    return 'success';
  if (status === 'SUSPENDED')
    return 'warning';
  if (status === 'CANCELLED')
    return 'danger';
  return 'info';
}

async function loadRows() {
  loading.value = true;
  try {
    rows.value = pickRows<ClinicSlot>(await listSlots({
      pageNum: 1,
      pageSize: 120,
      clinicDate: filters.clinicDate,
      departmentCode: filters.departmentCode,
      doctorId: filters.doctorId,
      status: filters.status,
    }));
  }
  finally {
    loading.value = false;
  }
}

async function loadLookups() {
  const [departmentRows, doctorRows] = await Promise.all([loadDepartments(), loadDoctors()]);
  departments.value = departmentRows;
  doctors.value = doctorRows;
}

function resetFilters() {
  filters.departmentCode = '';
  filters.doctorId = '';
  filters.clinicDate = '';
  filters.status = '';
  filters.onlyAvailable = false;
  loadRows();
}

function openInventory(row: ClinicSlot) {
  currentSlot.value = row;
  inventoryForm.capacity = row.capacity;
  inventoryForm.remainingSlots = row.remaining_slots;
  inventoryForm.reason = '';
  inventoryVisible.value = true;
}

async function submitInventory() {
  if (!currentSlot.value)
    return;
  if (inventoryForm.remainingSlots > inventoryForm.capacity) {
    ElMessage.error('剩余号源不能大于总容量');
    return;
  }
  actionLoading.value = true;
  try {
    await adjustSlotInventory(currentSlot.value.slot_id, {
      capacity: inventoryForm.capacity,
      remainingSlots: inventoryForm.remainingSlots,
      reason: inventoryForm.reason,
    });
    ElMessage.success('号源库存已更新');
    inventoryVisible.value = false;
    await loadRows();
  }
  finally {
    actionLoading.value = false;
  }
}

async function submitBatchGenerate() {
  if (!batchForm.departmentCode || !batchForm.doctorId || batchForm.dateRange.length !== 2) {
    ElMessage.error('科室、医生和日期范围不能为空');
    return;
  }
  batchLoading.value = true;
  try {
    const res = await batchGenerateSlots({
      departmentCode: batchForm.departmentCode,
      doctorId: batchForm.doctorId,
      startDate: batchForm.dateRange[0],
      endDate: batchForm.dateRange[1],
      weekdays: batchForm.weekdays,
      startTime: batchForm.startTime,
      endTime: batchForm.endTime,
      intervalMinutes: batchForm.intervalMinutes,
      capacity: batchForm.capacity,
      registrationFee: batchForm.registrationFee || undefined,
      roomNo: batchForm.roomNo || undefined,
      sourceType: 'LOCAL',
      remarks: batchForm.remarks || undefined,
    });
    const data = (res as { data?: Record<string, number> })?.data || (res as Record<string, number>);
    ElMessage.success(`生成 ${data.createdCount || 0} 条，跳过 ${data.skippedCount || 0} 条`);
    batchVisible.value = false;
    await loadRows();
  }
  finally {
    batchLoading.value = false;
  }
}

async function switchStatus(row: ClinicSlot, status: 'OPEN' | 'SUSPENDED') {
  const title = status === 'OPEN' ? '恢复号源' : '停诊号源';
  const result = await ElMessageBox.prompt('请输入操作原因', title, {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /.+/,
    inputErrorMessage: '原因不能为空',
  });

  actionLoading.value = true;
  try {
    await changeSlotOperationalStatus(row.slot_id, {
      status,
      reason: (result as { value: string }).value,
    });
    ElMessage.success(status === 'OPEN' ? '号源已恢复' : '号源已停诊');
    await loadRows();
  }
  finally {
    actionLoading.value = false;
  }
}

function openAudit(row: ClinicSlot) {
  currentSlot.value = row;
  auditVisible.value = true;
}

watch(() => filters.departmentCode, () => {
  if (filters.doctorId && !filteredDoctors.value.some(item => item.doctor_id === filters.doctorId))
    filters.doctorId = '';
});

watch(() => batchForm.departmentCode, () => {
  if (batchForm.doctorId && !batchDoctors.value.some(item => item.doctor_id === batchForm.doctorId))
    batchForm.doctorId = '';
});

onMounted(async () => {
  await Promise.all([loadRows(), loadLookups()]);
});
</script>

<template>
  <div class="medical-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">可预约号源</h1>
        <p class="medical-page__desc">维护医生出诊时段、容量、剩余号源、停诊恢复和库存变化。</p>
      </div>
      <div class="medical-actions">
        <el-button :loading="batchLoading" @click="batchVisible = true">批量放号</el-button>
        <el-button type="primary" :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <div class="medical-grid medical-grid--4 overview-grid">
      <MetricCard v-for="item in overview" :key="item.title" v-bind="item" />
    </div>

    <section class="medical-filter">
      <el-form :model="filters" inline>
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
        <el-form-item label="日期">
          <el-date-picker v-model="filters.clinicDate" value-format="YYYY-MM-DD" type="date" placeholder="就诊日期" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="filters.onlyAvailable">仅看有余号</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRows">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">号源库存</h2>
        <span class="medical-muted">共 {{ filteredRows.length }} 条记录</span>
      </div>
      <el-table v-loading="loading" :data="filteredRows" class="medical-table" height="calc(100vh - 390px)" table-layout="fixed" empty-text="暂无号源">
        <el-table-column label="科室" width="130">
          <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
        </el-table-column>
        <el-table-column label="医生" width="116">
          <template #default="{ row }">{{ doctorName(row.doctor_id, doctors) }}</template>
        </el-table-column>
        <el-table-column prop="clinic_date" label="日期" width="112" />
        <el-table-column label="时段" width="126">
          <template #default="{ row }">{{ row.start_time }}-{{ row.end_time || '-' }}</template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="70" />
        <el-table-column prop="remaining_slots" label="剩余" width="70" />
        <el-table-column label="使用率" min-width="130">
          <template #default="{ row }">
            <el-progress :percentage="toPercent(row.capacity - row.remaining_slots, row.capacity)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="slotStatusType(row.status)" effect="light" round>
              {{ slotStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="room_no" label="诊室" width="90" />
        <el-table-column prop="registration_fee" label="挂号费" width="90" />
        <el-table-column label="操作" width="136">
          <template #default="{ row }">
            <div class="medical-table-actions">
              <el-button link type="primary" @click="openInventory(row)">调整</el-button>
              <el-dropdown trigger="click" :disabled="actionLoading">
                <el-button link type="primary" :loading="actionLoading">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="row.status === 'OPEN'" @click="switchStatus(row, 'SUSPENDED')">停诊</el-dropdown-item>
                    <el-dropdown-item v-else @click="switchStatus(row, 'OPEN')">恢复开放</el-dropdown-item>
                    <el-dropdown-item @click="openAudit(row)">库存审计</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="batchVisible" title="批量放号" width="680px">
      <el-form label-width="104px">
        <el-form-item label="科室">
          <el-select v-model="batchForm.departmentCode" filterable placeholder="选择科室">
            <el-option v-for="item in departments" :key="item.department_code" :label="item.department_name" :value="item.department_code" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="batchForm.doctorId" filterable placeholder="选择医生">
            <el-option v-for="item in batchDoctors" :key="item.doctor_id" :label="doctorOptionLabel(item, departments)" :value="item.doctor_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="batchForm.dateRange" value-format="YYYY-MM-DD" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="出诊星期">
          <el-checkbox-group v-model="batchForm.weekdays">
            <el-checkbox v-for="item in weekdayOptions" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="时段">
          <div class="inline-fields">
            <el-time-picker v-model="batchForm.startTime" value-format="HH:mm" format="HH:mm" placeholder="开始" />
            <el-time-picker v-model="batchForm.endTime" value-format="HH:mm" format="HH:mm" placeholder="结束" />
          </div>
        </el-form-item>
        <el-form-item label="间隔分钟">
          <el-input-number v-model="batchForm.intervalMinutes" :min="5" :max="240" :step="5" />
        </el-form-item>
        <el-form-item label="每段容量">
          <el-input-number v-model="batchForm.capacity" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="挂号费">
          <el-input-number v-model="batchForm.registrationFee" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="诊室">
          <el-input v-model="batchForm.roomNo" placeholder="例如 A201" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remarks" type="textarea" :rows="3" placeholder="批量放号备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="submitBatchGenerate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inventoryVisible" title="调整号源库存" width="460px">
      <el-form label-width="96px">
        <el-form-item label="总容量">
          <el-input-number v-model="inventoryForm.capacity" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="剩余号源">
          <el-input-number v-model="inventoryForm.remainingSlots" :min="0" :max="inventoryForm.capacity" :step="1" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="inventoryForm.reason" type="textarea" :rows="3" placeholder="请输入调整原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inventoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitInventory">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="auditVisible" title="库存审计" size="520px">
      <div class="medical-drawer-body">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="科室">{{ departmentName(currentSlot?.department_code, departments) }}</el-descriptions-item>
          <el-descriptions-item label="医生">{{ doctorName(currentSlot?.doctor_id, doctors) }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ currentSlot?.clinic_date }}</el-descriptions-item>
          <el-descriptions-item label="当前库存">{{ currentSlot?.remaining_slots }} / {{ currentSlot?.capacity }}</el-descriptions-item>
        </el-descriptions>
        <el-empty description="库存审计接口已在订单详情中展示，后续可扩展为按号源查询" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.overview-grid {
  margin-bottom: 12px;
}

.inline-fields {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
