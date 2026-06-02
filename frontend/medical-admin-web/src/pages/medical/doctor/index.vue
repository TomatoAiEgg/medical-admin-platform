<script setup lang="ts">
import type { Department, Doctor } from '@/api/medical';
import { addDoctor, listDoctors, updateDoctor } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { displayValue, pickRows } from '../_utils/format';
import { departmentName, loadDepartments } from '../_utils/lookup';

const loading = ref(false);
const saving = ref(false);
const rows = ref<Doctor[]>([]);
const departments = ref<Department[]>([]);
const total = ref(0);
const editVisible = ref(false);
const isCreate = ref(false);

const query = reactive({
  departmentCode: '',
  doctorName: '',
  pageNum: 1,
  pageSize: 20,
});

const form = reactive<Doctor>({
  doctor_id: '',
  department_code: '',
  doctor_name: '',
  title_name: '',
  speciality: '',
  specialty: '',
  online_enabled: true,
  active: true,
});

const metrics = computed(() => [
  { title: '医生总数', value: total.value || rows.value.length, desc: '当前筛选结果', tone: 'primary' as const, icon: 'UserFilled' },
  { title: '开放挂号', value: rows.value.filter(item => item.online_enabled).length, desc: '可线上预约', tone: 'success' as const, icon: 'CircleCheck' },
  { title: '停用医生', value: rows.value.filter(item => item.active === false).length, desc: '已暂停服务', tone: 'warning' as const, icon: 'RemoveFilled' },
  { title: '覆盖科室', value: new Set(rows.value.map(item => item.department_code).filter(Boolean)).size, desc: '按科室统计', tone: 'info' as const, icon: 'OfficeBuilding' },
]);

function pickTotal(res: unknown, fallback: number) {
  const data = res as { total?: number; data?: { total?: number } };
  return data?.total ?? data?.data?.total ?? fallback;
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await listDoctors({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      departmentCode: query.departmentCode,
      doctorName: query.doctorName,
    });
    rows.value = pickRows<Doctor>(res);
    total.value = pickTotal(res, rows.value.length);
  }
  finally {
    loading.value = false;
  }
}

async function loadLookups() {
  departments.value = await loadDepartments();
}

function resetForm(row?: Doctor) {
  form.doctor_id = row?.doctor_id || '';
  form.department_code = row?.department_code || '';
  form.doctor_name = row?.doctor_name || '';
  form.title_name = row?.title_name || '';
  form.speciality = row?.speciality || row?.specialty || '';
  form.specialty = row?.specialty || row?.speciality || '';
  form.online_enabled = row?.online_enabled ?? true;
  form.active = row?.active ?? true;
}

function openEditor(row?: Doctor) {
  isCreate.value = !row;
  resetForm(row);
  editVisible.value = true;
}

async function saveRow() {
  if (!form.doctor_id || !form.department_code || !form.doctor_name) {
    ElMessage.warning('请填写医生编号、科室和医生姓名');
    return;
  }

  saving.value = true;
  try {
    const payload = { ...form, specialty: form.specialty || form.speciality };
    if (isCreate.value)
      await addDoctor(payload);
    else
      await updateDoctor(form.doctor_id, payload);
    ElMessage.success('医生已保存');
    editVisible.value = false;
    await loadRows();
  }
  finally {
    saving.value = false;
  }
}

function resetFilters() {
  query.departmentCode = '';
  query.doctorName = '';
  query.pageNum = 1;
  loadRows();
}

function handlePageChange(page: number) {
  query.pageNum = page;
  loadRows();
}

function handleSizeChange(size: number) {
  query.pageSize = size;
  query.pageNum = 1;
  loadRows();
}

onMounted(async () => {
  await Promise.all([loadRows(), loadLookups()]);
});
</script>

<template>
  <div class="medical-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">医生查询</h1>
        <p class="medical-page__desc">维护医生档案、职称、擅长方向和线上挂号开放状态。</p>
      </div>
      <div class="medical-actions">
        <el-button type="primary" @click="openEditor()">新增医生</el-button>
        <el-button :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="query" inline>
      <el-form-item label="科室">
        <el-select v-model="query.departmentCode" clearable filterable placeholder="全部科室">
          <el-option v-for="item in departments" :key="item.department_code" :label="item.department_name" :value="item.department_code" />
        </el-select>
      </el-form-item>
      <el-form-item label="医生姓名">
        <el-input v-model="query.doctorName" clearable placeholder="输入医生姓名" @keyup.enter="loadRows" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="loadRows">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="medical-grid medical-grid--4">
      <MetricCard v-for="item in metrics" :key="item.title" v-bind="item" />
    </div>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">医生列表</h2>
        <span class="medical-muted">共 {{ total || rows.length }} 条</span>
      </div>
      <div class="medical-section__body">
        <el-table v-loading="loading" :data="rows" class="medical-table" table-layout="fixed">
          <el-table-column prop="doctor_name" label="医生" width="120" />
          <el-table-column label="科室" width="140">
            <template #default="{ row }">{{ departmentName(row.department_code, departments) }}</template>
          </el-table-column>
          <el-table-column label="职称" width="120">
            <template #default="{ row }">{{ displayValue(row.title_name) }}</template>
          </el-table-column>
          <el-table-column label="擅长" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ displayValue(row.speciality || row.specialty) }}</template>
          </el-table-column>
          <el-table-column label="线上挂号" width="110">
            <template #default="{ row }">
              <el-tag :type="row.online_enabled === false ? 'info' : 'success'" effect="light">
                {{ row.online_enabled === false ? '关闭' : '开启' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="row.active === false ? 'danger' : 'success'" effect="light">
                {{ row.active === false ? '停用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="86">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="doctor-pagination">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50, 100]"
            :total="total || rows.length"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </section>

    <el-dialog v-model="editVisible" :title="isCreate ? '新增医生' : '编辑医生'" width="600px">
      <el-form label-width="100px">
        <el-form-item label="医生编号" required>
          <el-input v-model="form.doctor_id" :disabled="!isCreate" placeholder="系统唯一编号，仅保存使用" />
        </el-form-item>
        <el-form-item label="所属科室" required>
          <el-select v-model="form.department_code" filterable placeholder="选择科室">
            <el-option v-for="item in departments" :key="item.department_code" :label="item.department_name" :value="item.department_code" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生姓名" required>
          <el-input v-model="form.doctor_name" placeholder="输入医生姓名" />
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="form.title_name" placeholder="主任医师 / 副主任医师等" />
        </el-form-item>
        <el-form-item label="擅长">
          <el-input v-model="form.speciality" type="textarea" :rows="3" placeholder="填写擅长方向" />
        </el-form-item>
        <el-form-item label="线上挂号">
          <el-switch v-model="form.online_enabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.active" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.medical-page {
  display: grid;
  gap: 12px;
}

.doctor-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
